package org.example.backendweride.platform.iam;

import org.example.backendweride.platform.iam.application.internal.commandservices.AccountSecurityService;
import org.example.backendweride.platform.iam.application.internal.outboundservices.hashing.HashingService;
import org.example.backendweride.platform.iam.domain.model.aggregates.Account;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountSession;
import org.example.backendweride.platform.iam.domain.model.aggregates.AccountStatus;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountRepository;
import org.example.backendweride.platform.iam.infrastructure.persistence.jpa.repositories.AccountSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSecurityServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountSessionRepository sessions = mock(AccountSessionRepository.class);
    private final AccountSecurityService security = new AccountSecurityService(accounts, sessions, mock(HashingService.class));

    private Account account(AccountStatus status) {
        var account = new Account("ana", "hash");
        ReflectionTestUtils.setField(account, "status", status);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void activeSessionOfActiveAccountIsActive() {
        account(AccountStatus.ACTIVE);
        var session = new AccountSession(7L, Instant.now().plusSeconds(60), null);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        assertTrue(security.isSessionActive(session.getId()));
    }

    @Test
    void deletionPendingAccountMakesSessionInactive() {
        account(AccountStatus.DELETION_PENDING);
        var session = new AccountSession(7L, Instant.now().plusSeconds(60), null);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        assertFalse(security.isSessionActive(session.getId()));
    }

    @Test
    void revokedExpiredOrUnknownSessionIsInactive() {
        account(AccountStatus.ACTIVE);
        var revoked = new AccountSession(7L, Instant.now().plusSeconds(60), null);
        revoked.revoke();
        var expired = new AccountSession(7L, Instant.now().minusSeconds(1), null);
        when(sessions.findById(revoked.getId())).thenReturn(Optional.of(revoked));
        when(sessions.findById(expired.getId())).thenReturn(Optional.of(expired));
        assertFalse(security.isSessionActive(revoked.getId()));
        assertFalse(security.isSessionActive(expired.getId()));
        assertFalse(security.isSessionActive("unknown"));
    }
}
