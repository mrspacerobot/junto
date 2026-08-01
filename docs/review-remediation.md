# Junto review remediation record

This record maps every numbered finding in the August 2026 review to its shipped
disposition on `fm/junto-review-a1`.

1. **Committed-tree completeness — fixed.** Foundational backend/frontend source,
   tests, configuration, lockfile, and the executable Gradle wrapper are tracked.
   A fresh local clone completed the full backend build and frontend install,
   generation, type check, unit tests, build, and audit with a clean worktree.
2. **Flyway history replacement — fixed.** Released `V001`–`V005`, `V014`, and
   `V016` match `master` byte-for-byte. `B016` handles new installs and
   forward-only `V017`–`V019` handle existing Onda databases. Integration tests
   cover an empty install, representative Onda upgrade/data retention, old-session
   expiration, and immutable fixture bytes. The Docker volume is distinct and the
   operational copy/backup/rollback procedure is documented.
3. **Generated frontend contract/type-check gap — fixed.** Controller-qualified
   operation IDs are unique and stable, internal current-user arguments are hidden,
   list filters/pageables are flattened, custom fields are `unknown` map values,
   and frontend bindings are regenerated from tracked OpenAPI. Handwritten calls
   use named operations and DTO transforms. `vue-tsc -b` checks the application.
4. **JDBC session principal — fixed.** Sessions serialize only UUID-backed
   `SessionPrincipal`; active user state is reloaded per request. `V017` expires
   incompatible sessions and a real JDBC login/reload/logout test verifies storage.
5. **OTP attempts and throttling — fixed.** Wrong guesses atomically increment the
   current token independently of the submitted code, consumption is conditional,
   and database-backed account/source request and verify limits are shared across
   instances. Repeated and concurrent tests stop at five token attempts.
6. **Invite races/quotas — fixed.** Invite consumption and quota decrement use
   conditional updates inside signup transactions. Concurrency tests prove one
   consumer and no quota over-issuance.
7. **Participant lifecycle bypasses — fixed.** Declines cannot be erased to rejoin;
   pending withdrawal emits no false chat event; approval/decline enforce nested
   activity identity, active state, deadline, creator authority, and locked capacity.
8. **Activity validation/concurrency — fixed.** Full PUT fields, positive capacity,
   coordinate pair/ranges, and database checks are enforced. Capacity updates,
   joins, and lifecycle transitions share row locks and entity versioning. Concurrent
   join/reduction and terminal-transition tests preserve invariants.
9. **WebSocket revocation/delivery/rate limits — fixed.** The endpoint restricts
   origin, uses authenticated HTTP sessions, authorizes subscriptions and each
   outbound activity delivery against current session/user/participant state, and
   broadcasts only after commit. Chat limits are atomic/database-backed. Integration
   coverage proves rollback suppression and immediate leave revocation; unit coverage
   proves logout/suspension suppression.
10. **Magic links/branding — fixed.** Email and UI are Junto-branded. The public
    verification route removes the token from browser history, verifies it, refreshes
    auth, and routes onboarding/home. Playwright completes a real Mailpit link flow.
11. **False-confidence CI — fixed.** CI runs executable Gradle build and Docker
    integration/migration tests, generates OpenAPI from healthy PostGIS with schema
    validation and scheduling disabled, runs real TS/unit/build checks, provisions
    disposable PostgreSQL/Mailpit/browser lifecycle, and runs Playwright.
12. **Optimistic chat duplication — fixed.** REST/STOMP ordering reconciles temporary
    and saved IDs, buffers own echoes, deduplicates either arrival order, and exposes
    failures. Unit and two-browser E2E tests require exactly one rendered message.
13. **Message deep links/metadata/N+1 — fixed.** Route selection watches async thread
    data, viewport width is reactive, type name/image fallback/counts/avatars are
    populated from batched participant data, and integration/E2E tests cover metadata
    and direct links.
14. **Map pagination/filter drift — fixed.** Pagination replaces arrays, the marker
    version tracks coordinate-bearing content including appended pages, and frontend
    filters use `typeSlug`. Pagination unit tests cover append/reset races.
15. **Ignored filter/malformed cursor — fixed.** `joinable` true/false semantics are
    implemented, radius without an origin is rejected, cursors use URL-safe encoding
    and a single 400 parser, and endpoint tests cover all three contracts.
16. **Operational package/session/database transition — fixed.** External names,
    database/user/volume transition, backups, session expiration, verification, and
    rollback are inventoried in `docs/operations/onda-to-junto.md`.
17. **Generated/accidental local artifacts — fixed after preservation.** Legitimate
    source was allowlisted; build, IDE, browser, agent, and obsolete scaffold/image
    artifacts were excluded and ignored. Classification and immutable archive hashes
    are in `docs/recovery-provenance.md`.
18. **Dependency advisories — fixed by controlled upgrades.** Vite, PostCSS, and
    OpenAPI generation were upgraded and vulnerable transitive parser/build packages
    were constrained to patched versions. Generation, type/build/unit/E2E checks pass
    and `npm audit --audit-level=low` reports zero vulnerabilities.

## Final validation evidence

- Backend clean test: **222 tests, 0 failures, 0 errors, 0 skipped**.
- Backend packaging: `./gradlew build`: **passed**.
- Migration coverage: empty install, Onda upgrade, immutable migration fixtures:
  **passed** as part of the full suite.
- OpenAPI: valid PostGIS boot/generation, **31 unique operation IDs**, no injected
  current-user parameter: **passed**.
- Frontend: type check, **4 unit tests**, production build, and zero-advisory audit:
  **passed**.
- Playwright: **7 tests passed**, including two-user realtime chat, optimistic
  deduplication, direct message links, and Mailpit magic-link verification.
- Fresh clone: backend build plus frontend install/generation/type/unit/build/audit:
  **passed**, with executable wrapper and clean Git status.
- Original source status (including ignored/untracked entries) remained byte-state
  equivalent at the Git status layer to recovery pass 2; original HEAD remained
  `5c9cdb02d7153a488a88690c094dcc449faddb42`.
