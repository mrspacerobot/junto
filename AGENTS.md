# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- See `README.md` for the authoritative build, formatting, verification, production SPA packaging, and routing contract.
- Formatting is enforced in CI: `./gradlew spotlessApply|spotlessCheck` (backend) and `npm run format|format:check` (frontend). Keep mechanical reformats in their own commits.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
