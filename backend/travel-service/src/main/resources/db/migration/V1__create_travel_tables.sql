CREATE TABLE travels (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    owner_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE destinations (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL REFERENCES travels(id) ON DELETE CASCADE,
    city VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    arrival_date DATE NOT NULL,
    departure_date DATE NOT NULL,
    order_index INTEGER NOT NULL
);

CREATE TABLE activities (
    id UUID PRIMARY KEY,
    destination_id UUID NOT NULL REFERENCES destinations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    date DATE NOT NULL,
    cost NUMERIC(10, 2)
);

CREATE TABLE accommodations (
    id UUID PRIMARY KEY,
    destination_id UUID NOT NULL UNIQUE REFERENCES destinations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    address VARCHAR(255) NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL
);

CREATE TABLE transportations (
    id UUID PRIMARY KEY,
    travel_id UUID NOT NULL REFERENCES travels(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    from_location VARCHAR(200) NOT NULL,
    to_location VARCHAR(200) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    arrival_time TIMESTAMP NOT NULL,
    provider VARCHAR(200)
);
