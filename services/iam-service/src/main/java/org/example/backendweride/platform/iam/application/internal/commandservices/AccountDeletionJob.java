package org.example.backendweride.platform.iam.application.internal.commandservices;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries pending deletion steps every 60 seconds (ADR-008).
 */
@Component
public class AccountDeletionJob {
    private final AccountDeletionService deletion;

    public AccountDeletionJob(AccountDeletionService deletion) {
        this.deletion = deletion;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void run() {
        deletion.processPending();
    }
}
