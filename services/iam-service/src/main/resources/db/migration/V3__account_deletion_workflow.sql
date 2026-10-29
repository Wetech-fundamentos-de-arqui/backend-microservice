CREATE TABLE account_deletion_workflows (
    id         VARCHAR(36) NOT NULL,
    account_id BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_deletion_workflows_account_id (account_id),
    INDEX idx_deletion_workflows_status (status)
);

CREATE TABLE deletion_steps (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    workflow_id     VARCHAR(36) NOT NULL,
    target          VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    attempts        INT         NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_deletion_steps_workflow_target UNIQUE (workflow_id, target)
);
