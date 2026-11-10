CREATE TABLE admin_setup (
 id INTEGER PRIMARY KEY CHECK (id = 1), completed BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO admin_setup(id, completed) VALUES (1, FALSE);

CREATE TABLE admin_user (
 id UUID PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 display_name VARCHAR(180) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','EDITOR','COMMERCIAL','VIEWER')),
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 password_hash VARCHAR(100),
 totp_secret VARCHAR(512),
 last_totp_step BIGINT NOT NULL DEFAULT -1,
 failed_attempts INTEGER NOT NULL DEFAULT 0,
 locked_until TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE admin_invitation (
 token_hash VARCHAR(64) PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES admin_user(id),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 used_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE admin_audit (
 id UUID PRIMARY KEY,
 actor_id UUID REFERENCES admin_user(id),
 action VARCHAR(40) NOT NULL,
 target_id UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX admin_audit_created ON admin_audit(created_at);
CREATE INDEX request_assignee ON commercial_request(assigned_to);
