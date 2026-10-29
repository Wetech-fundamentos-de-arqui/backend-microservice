package org.example.backendweride.platform.iam.application.internal.outboundservices.deletion;

/**
 * Internal calls of the deletion workflow towards other services (ADR-008). A target without
 * configuration is simply not configured: its steps stay pending.
 */
public interface DeletionTargets {
    enum Lock { LOCKED, ACTIVE_BOOKINGS, UNAVAILABLE }

    boolean isConfigured(String target);

    /** POST /internal/v1/users/{userId}/deletion-lock on booking. */
    Lock lockDeletion(String userId);

    /** DELETE /internal/v1/users/{userId}/deletion-lock on booking; best effort, never throws. */
    void abortDeletion(String userId);

    /** DELETE /internal/v1/users/{userId}/personal-data/{workflowId}; true once the target confirms. */
    boolean deletePersonalData(String target, String userId, String workflowId);
}
