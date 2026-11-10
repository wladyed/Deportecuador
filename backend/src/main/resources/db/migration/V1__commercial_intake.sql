CREATE TABLE commercial_request (
 id UUID PRIMARY KEY, request_key UUID NOT NULL UNIQUE, request_type VARCHAR(12) NOT NULL,
 organization VARCHAR(180) NOT NULL, country VARCHAR(100) NOT NULL, contact_name VARCHAR(180) NOT NULL,
 email VARCHAR(254) NOT NULL, phone VARCHAR(40) NOT NULL, description VARCHAR(4000) NOT NULL,
 interests VARCHAR(800) NOT NULL, locale VARCHAR(2) NOT NULL,
 policy_version VARCHAR(80) NOT NULL, consent_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, assigned_to UUID,
 status VARCHAR(30) NOT NULL DEFAULT 'NEW', unassigned_alerted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX request_created ON commercial_request(created_at);
CREATE TABLE notification_outbox (
 id UUID PRIMARY KEY, request_id UUID NOT NULL REFERENCES commercial_request(id),
 event_type VARCHAR(40) NOT NULL, recipient VARCHAR(254) NOT NULL,
 subject VARCHAR(200) NOT NULL, body VARCHAR(4000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, sent_at TIMESTAMP WITH TIME ZONE,
 attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(request_id,event_type)
);
CREATE INDEX outbox_pending ON notification_outbox(sent_at,next_attempt_at);
