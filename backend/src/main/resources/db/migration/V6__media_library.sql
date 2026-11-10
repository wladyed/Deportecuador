CREATE TABLE media_asset (
 id UUID PRIMARY KEY, storage_key VARCHAR(128) NOT NULL UNIQUE,
 filename VARCHAR(180) NOT NULL, title VARCHAR(180) NOT NULL,
 content_type VARCHAR(40) NOT NULL, byte_size BIGINT NOT NULL,
 sha256 VARCHAR(64) NOT NULL, width INTEGER, height INTEGER,
 archived BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 1,
 created_by UUID NOT NULL REFERENCES admin_user(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX media_asset_created ON media_asset(created_at);
CREATE TABLE cms_revision_media (
 content_key VARCHAR(80) NOT NULL, locale VARCHAR(2) NOT NULL,
 revision INTEGER NOT NULL, asset_id UUID NOT NULL REFERENCES media_asset(id),
 PRIMARY KEY(content_key,locale,revision,asset_id),
 FOREIGN KEY(content_key,locale,revision) REFERENCES cms_revision(content_key,locale,revision)
);
CREATE INDEX cms_revision_media_asset ON cms_revision_media(asset_id);
