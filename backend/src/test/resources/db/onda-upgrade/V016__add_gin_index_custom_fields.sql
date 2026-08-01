-- GIN index enables the @> containment operator used for custom field filtering.
-- jsonb_extract_path_text does NOT use this index; @> does.
-- CONCURRENTLY is intentionally omitted: Flyway runs migrations in a transaction,
-- and CREATE INDEX CONCURRENTLY cannot run inside a transaction.
CREATE INDEX IF NOT EXISTS idx_activities_custom_fields_gin
    ON activities USING GIN (custom_fields);
