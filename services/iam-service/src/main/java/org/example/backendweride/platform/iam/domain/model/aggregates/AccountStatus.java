package org.example.backendweride.platform.iam.domain.model.aggregates;

/**
 * Account lifecycle. DELETION_PENDING counts as inactive for every session (ADR-004, ADR-008).
 */
public enum AccountStatus { ACTIVE, DELETION_PENDING }
