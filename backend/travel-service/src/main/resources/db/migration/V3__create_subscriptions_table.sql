CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL REFERENCES travels(id) ON DELETE CASCADE,
    traveler_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    subscribed_at TIMESTAMP NOT NULL,
    cancelled_at TIMESTAMP
);

CREATE INDEX idx_subscriptions_travel_id ON subscriptions(travel_id);
CREATE INDEX idx_subscriptions_traveler_id ON subscriptions(traveler_id);
