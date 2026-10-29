CREATE TABLE accounts (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_name VARCHAR(254) NOT NULL,
    password  VARCHAR(255) NOT NULL,
    status    VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_user_name UNIQUE (user_name)
);

CREATE TABLE sessions (
    id           VARCHAR(255) NOT NULL,
    account_id   BIGINT       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    last_seen_at DATETIME(6),
    revoked_at   DATETIME(6),
    expires_at   DATETIME(6)  NOT NULL,
    user_agent   VARCHAR(500),
    PRIMARY KEY (id),
    INDEX idx_sessions_account_id (account_id)
);
