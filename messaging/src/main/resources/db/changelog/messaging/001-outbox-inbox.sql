--liquibase formatted sql

-- Shared by every service that publishes or consumes events. Each service's
-- master changelog includes this file, so the tables live in that service's
-- own database.

--changeset quickbite:messaging-001-outbox
-- Transactional outbox: a row is inserted in the same transaction as the
-- business change, then OutboxRelay sends it to Kafka and sets published_at.
-- id gives the relay a stable order; event_id travels as the "event-id" Kafka
-- header so consumers can drop duplicates.
CREATE TABLE IF NOT EXISTS outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    topic        VARCHAR(255) NOT NULL,
    message_key  VARCHAR(255),
    payload      JSONB        NOT NULL,
    headers      JSONB        NOT NULL DEFAULT '{}'::jsonb, -- W3C trace context of the request that wrote it
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   TEXT
);

-- The relay's query: unpublished rows in id order. Stays tiny because
-- published rows drop out of it.
CREATE INDEX IF NOT EXISTS idx_outbox_events_unpublished
    ON outbox_events (id) WHERE published_at IS NULL;
-- Housekeeping deletes published rows older than the retention period.
CREATE INDEX IF NOT EXISTS idx_outbox_events_published_at
    ON outbox_events (published_at) WHERE published_at IS NOT NULL;
--rollback DROP TABLE IF EXISTS outbox_events;

--changeset quickbite:messaging-001-inbox
-- Inbox: one row per (consumer, event) already processed, written in the same
-- transaction as the consumer's own changes. A redelivered event hits the
-- primary key and is skipped.
CREATE TABLE IF NOT EXISTS processed_events (
    consumer     VARCHAR(255) NOT NULL,
    event_id     VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events (processed_at);
--rollback DROP TABLE IF EXISTS processed_events;
