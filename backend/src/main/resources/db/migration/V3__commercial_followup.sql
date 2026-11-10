ALTER TABLE commercial_request ADD COLUMN crm_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE commercial_request ADD COLUMN next_contact_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE commercial_request ADD COLUMN close_reason VARCHAR(1000);
ALTER TABLE commercial_request ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE commercial_request ADD COLUMN followup_alerted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX request_followup ON commercial_request(next_contact_at,followup_alerted);
CREATE TABLE crm_activity (
 id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES commercial_request(id),
 actor_id UUID NOT NULL REFERENCES admin_user(id), kind VARCHAR(20) NOT NULL,
 body VARCHAR(4000) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX crm_activity_request ON crm_activity(request_id,created_at);
CREATE TABLE crm_notice (
 id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES commercial_request(id),
 recipient_id UUID NOT NULL REFERENCES admin_user(id), kind VARCHAR(20) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, read_at TIMESTAMP WITH TIME ZONE,
 sent_at TIMESTAMP WITH TIME ZONE, cancelled BOOLEAN NOT NULL DEFAULT FALSE,
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX crm_notice_pending ON crm_notice(sent_at,cancelled,next_attempt_at);
