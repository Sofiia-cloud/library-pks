CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    login VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_app_users_login_lower ON app_users(lower(login));
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER','LIBRARIAN','ADMIN')),
    PRIMARY KEY (user_id, role)
);
CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_refresh_sessions_user ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_sessions_expires ON refresh_sessions(expires_at);
