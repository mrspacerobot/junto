-- Activity lifecycle constraints
-- Spec: docs/superpowers/specs/2026-04-25-activity-lifecycle-design.md, Section 4

-- CHECK constraint: signup_deadline must precede starts_at when set.
-- NOT VALID avoids scanning existing rows — safe if app already enforces this,
-- and prevents migration failure if any legacy rows violate the constraint.
ALTER TABLE activities
  ADD CONSTRAINT chk_signup_before_start
  CHECK (signup_deadline IS NULL OR signup_deadline < starts_at)
  NOT VALID;

-- Index on status to support autoCompleteStartedActivities scans.
CREATE INDEX IF NOT EXISTS idx_activities_status ON activities (status);
