CREATE TABLE feedbacks (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL REFERENCES travels(id) ON DELETE CASCADE,
    traveler_id UUID NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_feedbacks_travel_id ON feedbacks(travel_id);
-- Un seul feedback par (travel, traveler) - double le check applicatif dans FeedbackService.
CREATE UNIQUE INDEX idx_feedbacks_travel_traveler ON feedbacks(travel_id, traveler_id);

CREATE TABLE reports (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL REFERENCES travels(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL,
    reported_type VARCHAR(20) NOT NULL,
    reported_id UUID NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_reports_travel_id ON reports(travel_id);
CREATE INDEX idx_reports_reported_id ON reports(reported_id);
