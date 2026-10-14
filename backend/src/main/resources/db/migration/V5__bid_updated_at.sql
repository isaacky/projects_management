-- Bids and bid openings use the shared tenant entity base, which keeps an updated_at stamp.
-- V4 left the column out of both tables; Hibernate schema validation refused to start.

ALTER TABLE procurement.bids
    ADD COLUMN updated_at timestamptz;
UPDATE procurement.bids SET updated_at = coalesce(withdrawn_at, received_at);
ALTER TABLE procurement.bids
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE procurement.bid_openings
    ADD COLUMN updated_at timestamptz;
UPDATE procurement.bid_openings SET updated_at = opened_at;
ALTER TABLE procurement.bid_openings
    ALTER COLUMN updated_at SET NOT NULL;
