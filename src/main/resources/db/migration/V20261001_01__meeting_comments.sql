CREATE TABLE IF NOT EXISTS meeting_comments (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL REFERENCES meetings(id),
    group_member_id BIGINT NOT NULL REFERENCES group_members(id),
    content VARCHAR(2000) NOT NULL CHECK (length(trim(content)) > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_meeting_comments_meeting_created
    ON meeting_comments(meeting_id, created_at, id);
