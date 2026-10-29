-- One row per unordered user pair: pending invitation or accepted friendship.
CREATE TABLE friend_requests (
    request_id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT friend_requests_no_self CHECK (sender_id <> recipient_id),
    CONSTRAINT friend_requests_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT friend_requests_acceptance CHECK (
        (status = 'PENDING' AND accepted_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL AND accepted_at >= created_at)
    )
);

CREATE UNIQUE INDEX friend_requests_unique_pair
    ON friend_requests (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id));
CREATE INDEX friend_requests_sender_status ON friend_requests (sender_id, status);
CREATE INDEX friend_requests_recipient_status ON friend_requests (recipient_id, status);
