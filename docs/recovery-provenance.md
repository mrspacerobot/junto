# Recovery provenance and import classification

The recovery branch `fm/junto-review-a1` was created from clean `master`, then
fast-forwarded through the existing 42 `feat/activity-lifecycle` commits before the
reviewed local source layer was classified and imported. The source repository at
`/Users/dylan/projects/junto` was not edited, built, staged, cleaned, or switched.

## Immutable recovery evidence

The pre-import capture is outside this repository at:

`/Users/dylan/projects/firstmate/data/junto-review-a1/recovery/20260801T154610Z/`

It contains the working-tree tar archive (excluding only root `.git`), all-ref Git
bundle, tracked binary diff, untracked and ignored manifests, source status and
refs, full content/metadata manifests, and two read-only coherence passes.
`COHERENCE.txt` records a pass. The top-level artifact inventory is
`ARTIFACTS.sha256`, whose own SHA-256 is recorded in
`ARTIFACTS.sha256.sha256`.

Key immutable artifact hashes:

- `junto-working-tree.tar`: `7489754a457584922566158be79d1cead87865df3f1918a5143c0ce0103cfdf9`
- `junto-all-refs.bundle`: `45d32d7020ec24fe7f3065111d009e94bf438b6ac6b07bca57d464cccb4c56e6`
- tracked diff passes 1 and 2: `04c3e8fe73ef1f21eef05b0cd9bfb4326a81ea6486b2d7609c0adf87b56529b6`
- source-tree passes 1 and 2: `338a1d8d9e5e03f9cfc1033f115bb0b419fa3468311bc7de0dd1dff840fc9e30`

## Imported

- Junto backend application, domain, auth, invite, activity, participant, chat,
  message, moderation, report, WebSocket, fixtures, and tests.
- Vue application entry points, configuration, components, views, stores,
  composables, API transformations, Playwright lifecycle, and lockfile.
- Released Onda migrations byte-for-byte plus forward-only Junto migrations.
- CI, Docker/PostGIS configuration, historical design/specification documents,
  and source requirements needed to explain domain behavior.
- Executable mode for `backend/gradlew`.

## Deliberately excluded

- Compiled or generated build output: `backend/bin`, `backend/build`,
  `frontend/dist`, dependency trees, Gradle caches, and generated diagnostics.
- IDE/Eclipse metadata and developer/agent state: `.idea`, `.vscode`, `.claude`,
  `.superpowers`, `.worktrees`, `.classpath`, `.project`, and `.settings`.
- Browser/test transients: `.playwright-mcp`, `frontend/test-results`, logs, and
  screenshots created as failure evidence.
- Unreferenced root design screenshots and obsolete scaffold assets that were not
  runtime imports or maintained documentation.
- Developer-specific Testcontainers socket configuration.

Generated frontend SDK files remain intentionally ignored and are reproduced from
the tracked `docs/openapi.json` by the fail-fast `postinstall`/`generate-api`
command. CI first regenerates the OpenAPI document from a schema-valid PostGIS
application, then regenerates and compiles the SDK.
