package org.example.backendweride.platform.iam.application.internal.commandservices;

import org.example.backendweride.platform.iam.application.internal.outboundservices.deletion.DeletionTargets;
import org.example.backendweride.platform.iam.application.internal.outboundservices.hashing.HashingService;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountDeletionWorkflow;
import org.example.backendweride.platform.iam.domain.model.aggregates.DeletionStep;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountDeletionWorkflowRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.DeletionStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * IAM side of ADR-008. The workflow is persisted, never deletes data of other contexts and only reaches
 * DELETED when booking's lock and every service's personal-data deletion have been confirmed.
 */
@Service
public class AccountDeletionService {
    static final String BOOKING_LOCK = "booking-lock";
    static final String BOOKING = "booking";
    static final List<String> PERSONAL_DATA = List.of("profile", "garage", "booking", "ride", "notification");
    static final Duration RETRY = Duration.ofSeconds(60);
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AccountRepository accounts;
    private final AccountSessionRepository sessions;
    private final AccountDeletionWorkflowRepository workflows;
    private final DeletionStepRepository steps;
    private final HashingService hashing;
    private final DeletionTargets targets;
    private final TransactionTemplate transaction;

    public AccountDeletionService(AccountRepository accounts, AccountSessionRepository sessions,
                                  AccountDeletionWorkflowRepository workflows, DeletionStepRepository steps,
                                  HashingService hashing, DeletionTargets targets,
                                  PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.workflows = workflows;
        this.steps = steps;
        this.hashing = hashing;
        this.targets = targets;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Starts (or returns, if one is already running) the deletion workflow of the account; returns its id. */
    public String requestDeletion(Long accountId, String sid, String password) {
        var account = accounts.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (password == null || !hashing.matches(password, account.getPassword()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID");
        var running = workflows.findFirstByAccountIdAndStatus(accountId, AccountDeletionWorkflow.Status.IN_PROGRESS);
        if (running.isPresent()) return running.get().getId();

        var current = sid == null ? null : sessions.findById(sid).orElse(null);
        if (current == null || !current.isActive() || !accountId.equals(current.getAccountId()) || !account.isActive())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        String userId = String.valueOf(accountId);
        boolean locked = false;
        if (targets.isConfigured(BOOKING)) {
            switch (targets.lockDeletion(userId)) {
                case ACTIVE_BOOKINGS -> throw new ResponseStatusException(HttpStatus.CONFLICT, "ACTIVE_BOOKINGS");
                case UNAVAILABLE -> {
                    targets.abortDeletion(userId);      // the lock may have been taken before the timeout
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_UNAVAILABLE");
                }
                case LOCKED -> locked = true;
            }
        }
        try {
            boolean bookingLocked = locked;
            return transaction.execute(status -> start(accountId, bookingLocked));
        } catch (RuntimeException e) {
            if (locked) targets.abortDeletion(userId);
            throw e;
        }
    }

    private String start(Long accountId, boolean bookingLocked) {
        var account = accounts.findLockedById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var running = workflows.findFirstByAccountIdAndStatus(accountId, AccountDeletionWorkflow.Status.IN_PROGRESS);
        if (running.isPresent()) return running.get().getId();

        account.markDeletionPending();
        accounts.save(account);
        var open = sessions.findByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(accountId, Instant.now());
        open.forEach(session -> session.revoke());
        sessions.saveAll(open);

        var workflow = workflows.save(new AccountDeletionWorkflow(accountId));
        var created = new ArrayList<DeletionStep>();
        created.add(new DeletionStep(workflow.getId(), BOOKING_LOCK, bookingLocked));
        PERSONAL_DATA.forEach(target -> created.add(new DeletionStep(workflow.getId(), target, false)));
        steps.saveAll(created);
        return workflow.getId();
    }

    /** Scheduled retry: advances every running workflow as far as the configured targets allow. */
    public void processPending() {
        for (var workflow : workflows.findByStatus(AccountDeletionWorkflow.Status.IN_PROGRESS)) {
            try {
                process(workflow);
            } catch (RuntimeException e) {
                LOGGER.warn("deletion workflow {} not advanced: {}", workflow.getId(), e.getMessage());
            }
        }
    }

    private void process(AccountDeletionWorkflow workflow) {
        var all = steps.findByWorkflowId(workflow.getId());
        var now = Instant.now();
        String userId = String.valueOf(workflow.getAccountId());

        var lock = all.stream().filter(step -> step.getTarget().equals(BOOKING_LOCK)).findFirst().orElseThrow();
        if (!lock.isDone()) {
            if (!targets.isConfigured(BOOKING) || !lock.isDue(now)) return;     // nothing may be deleted before the lock
            switch (targets.lockDeletion(userId)) {
                case LOCKED -> { lock.markDone(); steps.save(lock); }
                case ACTIVE_BOOKINGS -> { abort(workflow); return; }
                case UNAVAILABLE -> { lock.retryAfter(RETRY); steps.save(lock); return; }
            }
        }
        for (var step : all) {
            if (step.getTarget().equals(BOOKING_LOCK) || !step.isDue(now) || !targets.isConfigured(step.getTarget())) continue;
            if (targets.deletePersonalData(step.getTarget(), userId, workflow.getId())) step.markDone();
            else step.retryAfter(RETRY);
            steps.save(step);
        }
        if (all.stream().allMatch(DeletionStep::isDone)) finish(workflow);
    }

    /** Late 409 from booking: nothing was deleted, the account goes back to ACTIVE (sessions stay revoked). */
    private void abort(AccountDeletionWorkflow workflow) {
        transaction.executeWithoutResult(status -> {
            accounts.findLockedById(workflow.getAccountId()).ifPresent(account -> {
                account.reactivate();
                accounts.save(account);
            });
            workflow.markAborted();
            workflows.save(workflow);
        });
    }

    /** Every service confirmed: iam removes only its own data. */
    private void finish(AccountDeletionWorkflow workflow) {
        transaction.executeWithoutResult(status -> {
            sessions.deleteAllByAccountId(workflow.getAccountId());
            accounts.deleteById(workflow.getAccountId());
            workflow.markDeleted();
            workflows.save(workflow);
        });
    }
}
