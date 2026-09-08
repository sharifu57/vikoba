CREATE TABLE IF NOT EXISTS share_purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    group_member_id BIGINT NOT NULL REFERENCES group_members(id),
    share_product_id BIGINT NOT NULL REFERENCES share_products(id),
    quantity INTEGER NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    payment_reference VARCHAR(100),
    proof_text TEXT,
    proof_file_name VARCHAR(255),
    proof_content_type VARCHAR(120),
    proof_file BYTEA,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_reason VARCHAR(500),
    submitted_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_share_purchase_requests_group_status
    ON share_purchase_requests (group_member_id, status, submitted_at DESC);
