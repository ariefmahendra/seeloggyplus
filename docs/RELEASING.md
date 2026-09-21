# Releasing SeeLoggyPlus

This document describes how to cut a release so that every change — and in
particular every bug fix — is tracked and published professionally.

## Versioning

We follow [Semantic Versioning](https://semver.org/):

- **MAJOR** – incompatible changes.
- **MINOR** – new features, backwards compatible.
- **PATCH** – bug fixes only.

The version lives in `gradle.properties` (`version=`). Tags use the bare
version (e.g. `0.4.3`), matching the existing tags.

## Commit messages (Conventional Commits)

Use the format `type(scope): summary`, e.g.:

```
fix(log-viewer): arrow keys navigate the log via scene-level routing
feat(ui): enterprise graphite theme with dark mode
docs(ui): record defect fixes and regression tests
test(ui): add contrast tests for input fields
```

Types: `feat`, `fix`, `test`, `docs`, `refactor`, `perf`, `build`, `ci`, `chore`.

Reference the tracked defect in the body when possible, e.g. `Fixes #42`.
GitHub then links the commit/PR to the issue automatically.

## Change tracking

Two artifacts keep the history clear:

1. **`CHANGELOG.md`** – human-readable, per release, grouped as
   `Added` / `Changed` / `Fixed` / `Tests` (Keep a Changelog).
2. **Categorized release notes** – GitHub generates them on release using
   `.github/release.yml`, grouping pull requests by label
   (🚀 Features, 🐛 Bug Fixes, 🎨 UI & Design, 🧪 Tests, …).

For the categorized notes to work, apply one of the matching **pull-request
labels** (`feature`, `bug`, `ui`, `test`, `docs`, `build`, `chore`, …).

## Release checklist

1. Ensure `dev` is green locally:
   ```bash
   ./gradlew test -PheadlessTest
   ```
2. Update `CHANGELOG.md`:
   - Move the relevant entries from `[Unreleased]` into a new version section.
   - Add the compare/release links at the bottom.
3. Bump the version in `gradle.properties`.
4. Commit on `dev` (e.g. `chore(release): 0.4.4`).
5. Merge `dev` into `main` (`--no-ff`) and push both branches:
   ```bash
   git checkout main
   git merge --no-ff dev -m "Merge dev into main (vX.Y.Z)"
   git push origin main dev
   ```
6. Tag and push the tag (this triggers the release workflow):
   ```bash
   git tag -a X.Y.Z -m "SeeLoggyPlus X.Y.Z"
   git push origin X.Y.Z
   ```
7. The **Release** workflow (`.github/workflows/release.yml`) then:
   - runs the test suite,
   - builds the portable packages for Windows/Linux (with and without JRE),
   - merges the update manifests,
   - **creates the GitHub Release** and attaches the artifacts,
   - publishes `update-manifest.json` to `gh-pages`.
8. Verify the release page and the generated notes. Edit the notes if you want a
   short professional summary on top (the categorized list is appended).

## One-off / manual release (if CI is unavailable)

With the GitHub CLI authenticated (`gh auth login`):

```bash
gh release create X.Y.Z \
  --title "SeeLoggyPlus X.Y.Z — <short title>" \
  --notes-file docs/release-notes/X.Y.Z.md
```

Prefer the automated workflow; use this only as a fallback.

## Labels to create (one-time, repository settings)

`feature`, `enhancement`, `bug`, `fix`, `ui`, `design`, `theme`, `test`,
`tests`, `documentation`, `docs`, `build`, `ci`, `chore`, `dependencies`,
`ignore-for-release`.
