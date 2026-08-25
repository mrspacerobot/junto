# Production release and promotion

Junto builds a release candidate once, publishes it by commit and digest, and
promotes that same digest. Production never rebuilds source code.

## Release boundary

The `Release candidate` workflow runs only after the `CI` workflow succeeds on
`master`. This means the backend, frontend, end-to-end, and production-image
jobs all finish before registry login or publication.

For every successful commit, the workflow:

1. resolves the application version, highest Flyway migration, and a digest of
   the packaged production configuration;
2. builds the production image for `linux/amd64` and verifies the JAR identity
   and the absence of development profiles, development seed migrations, and
   development-profile bypass controllers;
3. generates a CycloneDX SBOM for both operating-system and Java components,
   then scans that SBOM for fixable dependency vulnerabilities;
4. publishes `ghcr.io/mrspacerobot/junto:sha-<full-commit>` and resolves its
   immutable `sha256:` digest; and
5. creates a prerelease named `v<application-version>-<short-commit>` with the
   release record, SBOM, and vulnerability report attached.

The vulnerability scan is currently evidence, not a severity policy gate.
Issue #32 owns the blocking policy and automated dependency updates. Its
existing `scripts/scan-image-secrets.sh` path and the production-image job's
secret scan remain independent and unchanged.

The application version lives in `backend/build.gradle`. Change it deliberately
for a product release. A commit is still unambiguous when several candidates
share an application version because the image tag, image labels, and release
tag all include the commit identity.

## Configure the production approval gate

Create a GitHub environment named `production` before the first promotion.
Configure at least one required reviewer and prevent self-review. Under
**Deployment branches and tags**, choose **Selected branches and tags** and add
only the `master` branch. Do not add a tag rule, and disable administrator
bypass of environment protection rules. Add:

- environment variable `PRODUCTION_URL`, the public HTTPS application URL;
- environment variable `RENDER_SERVICE_ID`, the ID of the image-backed Render
  web service; and
- environment secret `RENDER_API_KEY`, a dedicated Render API key held only by
  this protected environment and an account with the minimum available access.

Required reviewers on private repositories require eligible GitHub Enterprise
hosting. Do not enable promotion on GitHub Free, Pro, or Team for this private
repository, because those plans only offer required reviewers to public
repositories. At runtime, the workflow verifies that the environment has at
least one required reviewer and prevents self-review; it refuses to deploy when
either protection is absent.

The Render service must be image-backed, configured for the
`ghcr.io/mrspacerobot/junto` image, use the production HTTP health path, have
automatic deploys disabled, and have credentials that can pull the private GHCR
image. Render only accepts an override tag or digest whose registry, repository,
and image name match the service's configured image.

Repository administrators should also protect `master` by requiring all CI
jobs. The workflow ordering remains enforced by `workflow_run`, but branch
protection prevents unreviewed commits from becoming release candidates.

## Promote or roll back

Open Actions, choose `Promote to production`, and use **Run workflow**. Supply:

- the full lowercase 40-character commit SHA of a published candidate;
- a verified backup reference, such as a Render point-in-time recovery
  timestamp or a logical dump identifier;
- the exact image digest currently reported by Render's live deployment, or
  `none (first production release)` when the service has no live deployment;
  and
- the exact confirmation `PROMOTE`.

The production environment reviewer then checks the candidate release record,
SBOM, advisories, schema change, configuration identity, backup, and rollback
target before approving the job.

Promotion reads the publication-time digest from the candidate release record,
requires the commit tag to still resolve to that digest, verifies that the
image labels and release agree with the requested commit, rejects an empty or
placeholder backup reference before contacting Render, and requires the
operator's expected rollback target to match the currently live Render image
immediately before asking Render to deploy the exact recorded digest. The
workflow waits for that specific Render deploy to reach
`live`. Render reaches that state only after its configured health gate accepts
the new deployment. If Render queues the request without returning its ID or
the response is otherwise ambiguous, the workflow recovers any new matching
deployment from the service's deployment list. If the build, pre-deploy
command, update, or health gate fails or times out, the workflow verifies a
non-live terminal state or cancels the deployment before failing, and it does
not move the `prod` tag or finalize the GitHub release.

Only after the candidate is live does the workflow move
`ghcr.io/mrspacerobot/junto:prod` to the verified digest and convert the
prerelease into the production release record. The record includes the commit,
image reference and digest, application version, Flyway schema version,
packaged configuration version, verified backup reference, and prior live image
digest.

Rollback uses the same workflow rather than a mutable provider rollback button:

1. choose the commit whose candidate image matches the recorded prior digest;
2. take or verify the backup appropriate to the current database state;
3. run `Promote to production` for that commit; and
4. have the production environment reviewer approve it.

Application rollback does not reverse Flyway. If the failed release migrated
the database, confirm the older application is compatible with the current
schema. Otherwise restore the recorded backup using the database recovery
procedure before directing traffic to the older application.

## Audit and recovery notes

- Commit tags and the `prod` tag are navigation aids. The `sha256:` digest in
  the release record is the immutable image identity.
- Rerunning an interrupted release reuses an orphaned commit tag only when it
  matches the verified rebuild, then completes any missing release assets or
  draft publication without overwriting a different image identity.
- Keep every promoted and rollback-target digest in GHCR. Render pulls the
  digest again for future restarts and rollbacks.
- Changing Render environment variables does not change the packaged
  configuration version. Review and record operational configuration changes
  alongside the promotion until provider configuration is managed in versioned
  infrastructure code.
- If Render reports the candidate live but a later registry or GitHub release
  bookkeeping step fails, the candidate may already serve production. The
  workflow summary identifies the prior digest; promote its commit through the
  same approved path.
