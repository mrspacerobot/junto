# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- See `README.md` for the authoritative build, formatting, verification, production SPA packaging, and routing contract.
- Formatting is enforced in CI: `./gradlew spotlessApply|spotlessCheck` (backend) and `npm run format|format:check` (frontend). Keep mechanical reformats in their own commits.
- Authentication intent is explicit: `/api/auth/request-code` is enumeration-safe sign-in, while `/api/auth/signup/request-code` validates the invite independently of account existence. Preserve this split; see `AuthService` and `AuthIntegrationTest`.
- Auth-code replacement in `AuthCodeService` intentionally uses `PostgresAuthTokenIssuanceLock` plus the partial unique index as the final invariant; see `AuthConcurrencyIntegrationTest`.
- `X-Forwarded-*` is trusted only by the `prod` profile, and the client address that `AuthRateLimiter` limits on comes from Tomcat's `RemoteIpValve`. Never widen that trust to shared configuration or switch to the `framework` strategy; see the production runtime section of `README.md` and `ch.junto.core.config` tests.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
