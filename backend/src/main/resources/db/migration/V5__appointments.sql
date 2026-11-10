-- Single shared agenda. All writes lock this row to prevent overlapping slots.
CREATE TABLE appointment_agenda (id INTEGER PRIMARY KEY);
INSERT INTO appointment_agenda(id) VALUES(1);
CREATE TABLE assistant_settings (
 id INTEGER PRIMARY KEY, whatsapp_phone VARCHAR(15) NOT NULL,
 whatsapp_link_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 calendar_id VARCHAR(254) NOT NULL DEFAULT '',
 calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 opens_at VARCHAR(5) NOT NULL DEFAULT '06:00',
 closes_at VARCHAR(5) NOT NULL DEFAULT '12:50',
 duration_minutes INTEGER NOT NULL DEFAULT 30,
 weekdays VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7'
);
INSERT INTO assistant_settings(id,whatsapp_phone) VALUES(1,'593123456789');
CREATE TABLE appointment_slot (
 id UUID PRIMARY KEY,
 starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
 ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
 state VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK(state IN ('OPEN','BOOKED','CANCELLED')),
 request_id UUID UNIQUE REFERENCES commercial_request(id),
 booking_key UUID UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(ends_at>starts_at)
);
CREATE INDEX appointment_available ON appointment_slot(state,starts_at);
CREATE TABLE whatsapp_session (
 sender_key VARCHAR(64) PRIMARY KEY, payload TEXT NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE whatsapp_reply (
 message_id VARCHAR(300) PRIMARY KEY, payload TEXT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 attempts INTEGER NOT NULL DEFAULT 0, sent BOOLEAN NOT NULL DEFAULT FALSE
);
