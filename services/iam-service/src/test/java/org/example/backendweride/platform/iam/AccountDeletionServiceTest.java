package org.example.backendweride.platform.iam;

import org.example.backendweride.platform.iam.application.internal.commandservices.AccountDeletionService;
import org.example.backendweride.platform.iam.application.internal.outboundservices.deletion.DeletionTargets;
import org.example.backendweride.platform.iam.application.internal.outboundservices.hashing.HashingService;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountDeletionWorkflow;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountSession;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountStatus;
import org.example.backendweride.platform.iam.domain.model.aggregates.DeletionStep;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountDeletionWorkflowRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.DeletionStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

class AccountDeletionServiceTest {
    private static final Long ID = 7L;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountSessionRepository sessions = mock(AccountSessionRepository.class);
    private final AccountDeletionWorkflowRepository workflows = mock(AccountDeletionWorkflowRepository.class);
    private final DeletionStepRepository steps = mock(DeletionStepRepository.class);
    private final HashingService hashing = mock(HashingService.class);
    private final FakeTargets targets = new FakeTargets();
    private final AccountDeletionService service = new AccountDeletionService(accounts, sessions, workflows, steps,
            hashing, targets, mock(PlatformTransactionManager.class));
    private final Account account = new Account("ana", "hash");
    private final AccountSession session = new AccountSession(ID, Instant.now().plusSeconds(3600), null);

    /** Stand-in for the internal calls; records what iam asked. */
    static class FakeTargets implements DeletionTargets {
        final Set<String> configured = new HashSet<>();
        final Set<String> failing = new HashSet<>();
        final List<String> deleted = new ArrayList<>();
        Lock lock = Lock.LOCKED;
        int aborts = 0;

        @Override public boolean isConfigured(String target) { return configured.contains(target); }
        @Override public Lock lockDeletion(String userId) { return lock; }
        @Override public void abortDeletion(String userId) { aborts++; }
        @Override public boolean deletePersonalData(String target, String userId, String workflowId) {
            if (failing.contains(target)) return false;
            deleted.add(target);
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(account, "id", ID);
        when(accounts.findById(ID)).thenReturn(Optional.of(account));
        when(accounts.findLockedById(ID)).thenReturn(Optional.of(account));
        when(hashing.matches("right", "hash")).thenReturn(true);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(sessions.findByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(eq(ID), any())).thenReturn(List.of(session));
        when(workflows.findFirstByAccountIdAndStatus(eq(ID), any())).thenReturn(Optional.empty());
        when(workflows.save(any(AccountDeletionWorkflow.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsWorkflowMarksAccountPendingAndRevokesSessions() {
        var workflowId = service.requestDeletion(ID, session.getId(), "right");

        assertNotNull(workflowId);
        assertEquals(AccountStatus.DELETION_PENDING, account.getStatus());
        assertFalse(session.isActive());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DeletionStep>> saved = ArgumentCaptor.forClass(List.class);
        verify(steps).saveAll(saved.capture());
        assertEquals(List.of("booking-lock", "profile", "garage", "booking", "ride", "notification"),
                saved.getValue().stream().map(DeletionStep::getTarget).toList());
        assertTrue(saved.getValue().stream().noneMatch(DeletionStep::isDone));      // booking is not configured yet
    }

    @Test
    void lockTakenSynchronouslyIsRecordedAsDone() {
        targets.configured.add("booking");
        service.requestDeletion(ID, session.getId(), "right");
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DeletionStep>> saved = ArgumentCaptor.forClass(List.class);
        verify(steps).saveAll(saved.capture());
        assertTrue(saved.getValue().get(0).isDone());
        assertEquals(0, targets.aborts);
    }

    @Test
    void secondRequestReturnsTheSameWorkflow() {
        var first = service.requestDeletion(ID, session.getId(), "right");
        var existing = new AccountDeletionWorkflow(ID);
        ReflectionTestUtils.setField(existing, "id", first);
        when(workflows.findFirstByAccountIdAndStatus(eq(ID), any())).thenReturn(Optional.of(existing));

        assertEquals(first, service.requestDeletion(ID, session.getId(), "right"));
        verify(workflows, times(1)).save(any(AccountDeletionWorkflow.class));
    }

    @Test
    void wrongPasswordIsBadRequestAndChangesNothing() {
        var error = assertThrows(ResponseStatusException.class, () -> service.requestDeletion(ID, session.getId(), "wrong"));
        assertEquals(400, error.getStatusCode().value());
        assertEquals("CURRENT_PASSWORD_INVALID", error.getReason());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(workflows, never()).save(any());
    }

    @Test
    void bookingConflictIsConflictWithoutWorkflowOrStateChange() {
        targets.configured.add("booking");
        targets.lock = DeletionTargets.Lock.ACTIVE_BOOKINGS;

        var error = assertThrows(ResponseStatusException.class, () -> service.requestDeletion(ID, session.getId(), "right"));

        assertEquals(409, error.getStatusCode().value());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(session.isActive());
        verify(workflows, never()).save(any());
        verify(steps, never()).saveAll(anyIterable());
    }

    @Test
    void uncertainLockIsReleasedBeforeAnswering503() {
        targets.configured.add("booking");
        targets.lock = DeletionTargets.Lock.UNAVAILABLE;

        var error = assertThrows(ResponseStatusException.class, () -> service.requestDeletion(ID, session.getId(), "right"));

        assertEquals(503, error.getStatusCode().value());
        assertEquals(1, targets.aborts);
        verify(workflows, never()).save(any());
    }

    @Test
    void failureAfterTheLockReleasesIt() {
        targets.configured.add("booking");
        when(steps.saveAll(anyIterable())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.requestDeletion(ID, session.getId(), "right"));

        assertEquals(1, targets.aborts);
    }

    private AccountDeletionWorkflow runningWorkflow(List<DeletionStep> stepList) {
        var workflow = new AccountDeletionWorkflow(ID);
        when(workflows.findByStatus(AccountDeletionWorkflow.Status.IN_PROGRESS)).thenReturn(List.of(workflow));
        when(steps.findByWorkflowId(workflow.getId())).thenReturn(stepList);
        return workflow;
    }

    private static List<DeletionStep> pendingSteps(String workflowId) {
        return List.of("booking-lock", "profile", "garage", "booking", "ride", "notification").stream()
                .map(target -> new DeletionStep(workflowId, target, false)).toList();
    }

    @Test
    void workflowEndsDeletedOnlyWhenEveryServiceConfirms() {
        var workflow = new AccountDeletionWorkflow(ID);
        var stepList = pendingSteps(workflow.getId());
        when(workflows.findByStatus(AccountDeletionWorkflow.Status.IN_PROGRESS)).thenReturn(List.of(workflow));
        when(steps.findByWorkflowId(workflow.getId())).thenReturn(stepList);
        targets.configured.addAll(Set.of("booking", "profile", "garage", "ride", "notification"));
        targets.failing.add("ride");

        service.processPending();
        assertEquals(AccountDeletionWorkflow.Status.IN_PROGRESS, workflow.getStatus());   // ride did not confirm
        assertEquals(1, stepList.stream().filter(s -> s.getTarget().equals("ride")).findFirst().orElseThrow().getAttempts());
        verify(accounts, never()).deleteById(any());

        targets.failing.clear();
        stepList.forEach(step -> ReflectionTestUtils.setField(step, "nextAttemptAt", Instant.now().minusSeconds(1)));
        service.processPending();
        assertEquals(AccountDeletionWorkflow.Status.DELETED, workflow.getStatus());
        verify(sessions).deleteAllByAccountId(ID);
        verify(accounts).deleteById(ID);
        assertEquals(Set.of("profile", "garage", "booking", "ride", "notification"), new HashSet<>(targets.deleted));
    }

    @Test
    void unconfiguredTargetsLeaveStepsPendingWithoutFailing() {
        var workflow = new AccountDeletionWorkflow(ID);
        var stepList = pendingSteps(workflow.getId());
        when(workflows.findByStatus(AccountDeletionWorkflow.Status.IN_PROGRESS)).thenReturn(List.of(workflow));
        when(steps.findByWorkflowId(workflow.getId())).thenReturn(stepList);

        service.processPending();

        assertTrue(stepList.stream().noneMatch(DeletionStep::isDone));
        assertTrue(stepList.stream().allMatch(step -> step.getAttempts() == 0));
        assertEquals(AccountDeletionWorkflow.Status.IN_PROGRESS, workflow.getStatus());
        verify(accounts, never()).deleteById(any());
    }

    @Test
    void lateConflictAbortsTheWorkflowAndReactivatesTheAccount() {
        var workflow = new AccountDeletionWorkflow(ID);
        when(workflows.findByStatus(AccountDeletionWorkflow.Status.IN_PROGRESS)).thenReturn(List.of(workflow));
        when(steps.findByWorkflowId(workflow.getId())).thenReturn(pendingSteps(workflow.getId()));
        account.markDeletionPending();
        targets.configured.add("booking");
        targets.lock = DeletionTargets.Lock.ACTIVE_BOOKINGS;

        service.processPending();

        assertEquals(AccountDeletionWorkflow.Status.ABORTED, workflow.getStatus());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertTrue(targets.deleted.isEmpty());
        verify(accounts, never()).deleteById(any());
    }
}
