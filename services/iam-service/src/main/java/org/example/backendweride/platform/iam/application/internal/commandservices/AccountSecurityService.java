package org.example.backendweride.platform.iam.application.internal.commandservices;

import org.example.backendweride.platform.iam.application.internal.outboundservices.hashing.HashingService;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountSession;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
@Transactional
public class AccountSecurityService {
    private final AccountRepository accounts;
    private final AccountSessionRepository sessions;
    private final HashingService hashing;

    public AccountSecurityService(AccountRepository accounts, AccountSessionRepository sessions, HashingService hashing) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.hashing = hashing;
    }

    private Account account(Long id, String sid) {
        var account = accounts.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var current = sid == null ? null : sessions.findById(sid).orElse(null);
        if (current == null || !current.isActive() || !id.equals(current.getAccountId()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return account;
    }

    private void checkPassword(Account account, String password) {
        if (password == null || !hashing.matches(password, account.getPassword()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID");
    }

    private int revoke(Long id, String except) {
        var others = sessions.findByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(id, Instant.now());
        int count = 0;
        for (var session : others) {
            if (!session.getId().equals(except)) { session.revoke(); count++; }
        }
        sessions.saveAll(others);
        return count;
    }

    public void changePassword(Long id, String sid, String currentPassword, String newPassword) {
        var account = account(id, sid);
        checkPassword(account, currentPassword);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 64 || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || newPassword.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEW_PASSWORD_INVALID");
        account.updatePassword(hashing.encode(newPassword));
        accounts.save(account);
        revoke(id, sid);
    }

    public int revokeOthers(Long id, String sid) {
        account(id, sid);
        return revoke(id, sid);
    }

    public void logout(Long id, String sid) {
        account(id, sid);
        var session = sessions.findById(sid).orElseThrow();
        session.revoke();
        sessions.save(session);
    }

    /** A session is active when it is unrevoked, unexpired and its account is ACTIVE (not DELETION_PENDING). */
    @Transactional(readOnly = true)
    public boolean isSessionActive(String sid) {
        return sessions.findById(sid)
                .filter(AccountSession::isActive)
                .flatMap(session -> accounts.findById(session.getAccountId()))
                .map(Account::isActive)
                .orElse(false);
    }
}
