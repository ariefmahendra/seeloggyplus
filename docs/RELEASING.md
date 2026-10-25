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
feat(ui): islands light and dark themes
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

## Release description format (mandatory)

The GitHub Release body is **the `release-notes/<version>.md` file**, copied
verbatim by the release workflow. The matching `[X.Y.Z]` section in `CHANGELOG.md`
is only the fallback (and the historical record), so both must stay in sync and
end-user ready — never a one-line internal note.

Required order inside `[X.Y.Z]`:

1. short opening paragraph (optional, e.g. "First published build of the X line.");
2. `### Added`, then `### Changed`, then `### Fixed`, then `### Tests`
   (only the relevant sections, in this order);
3. upgrade notes when needed.

For the first published release of a feature line, summarize the main features
under `### Added` (do not list only CI fixes). A matching
`release-notes/<version>.md` is mandatory and uses this structure:

```markdown
# SeeLoggyPlus X.Y.Z

## Overview
...

## Features            (and/or ## Fixed / ## Improvements)
- ...

## Tests
- ...

## Upgrade notes
- ...

**Full changelog:** [`A.B.C...X.Y.Z`](https://github.com/ariefmahendra/seeloggyplus/compare/A.B.C...X.Y.Z)
```

If a release was already published with the wrong description, fix
`CHANGELOG.md` and `release-notes/<version>.md`, push, then either:

```bash
gh release edit X.Y.Z --notes-file release-notes/X.Y.Z.md   # needs gh auth login
```

or use the GitHub API (`PATCH /repos/<owner>/<repo>/releases/<id>`) with the
release-notes file as the body.

## Release checklist

1. Ensure `dev` is green locally (the test suite is always headless):
   ```bash
   ./gradlew --no-daemon test
   ```
   On Windows use `.\gradlew.bat --no-daemon test`.
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
   - **creates the GitHub Release**, using `release-notes/<version>.md` as the
     release body, and attaches the artifacts,
   - publishes `update-manifest.json` to `gh-pages`.
8. Verify the release page and its notes. A ready-to-use narrative per version also
   lives in `release-notes/<version>.md` for manual publishing.

## Online documentation and compatibility gate

Linux release jobs use RUNNER_LABELS, defaulting to GitHub-hosted ubuntu-latest.
The same labels select tests, Linux packages and publication. A self-hosted
runner can be selected when it is online and provisioned. All runners require
Python 3.8 or newer, native fontconfig, freetype, Pango, GTK 3 and binutils.
On Rocky Linux, provision with `sudo dnf install -y fontconfig freetype gtk3 pango binutils`;
on Ubuntu use the corresponding apt packages. Binutils supplies objcopy for jlink.
The workflow checks native dependencies and a headless JavaFX dialog before the
full suite. It supplies its own
verified DejaVu fonts and isolated font cache; the runner account does not need
unrestricted sudo after the native libraries are installed.

Help Contents opens https://seeloggyplus-doc.vercel.app/. Offline help is no longer
included. Update both English and Indonesian guides and changelogs in
the sibling seeloggyplus-doc repository, and deploy its main branch to Vercel.

Generate documentation screenshots headlessly with:

```powershell
.\gradlew.bat --no-daemon test --tests "*DocumentationScreenshotGeneratorTest" -PdocumentationScreenshotsDir=build/documentation-screenshots
```

Before tagging, test both real portable packages against the released 0.7.0 jar:

```powershell
.\gradlew.bat --no-daemon verifyReleaseCompatibility -PlegacyJar=build/release-compatibility/seeloggyplus.jar
```

The gate downloads through the old updater, checks legacy ZIP whitelists, preserves
synthetic database records, credentials, trusted hosts and launcher settings, and
exercises activation, checksum rejection, health confirmation and rollback.
Run it on Windows and Linux before publishing. Never publish when this gate fails.

## SonarQube Community Build release gate

The `SonarQube CE` workflow analyzes only pushes to `dev`; manual dispatch must
also target `dev`. The project key is the repository variable `SONAR_PROJECT_KEY`
(default `seeloggyplus`). `SONAR_HOST_URL` and `SONAR_TOKEN` are repository secrets;
the token needs Execute Analysis permission for that project. The GitHub-hosted
Linux runner must be able to reach the SonarQube server.

The workflow runs selected headless security, reliability, startup and updater regression
classes on Windows and Linux at the same dev revision. Both jobs must pass and
upload non-empty JaCoCo execution data. `jacocoSonarReport` combines those inputs
against the matching classes before analysis; it does not invoke the full suite.
The scanner imports the combined XML report and waits for the server's Quality
Gate. This is selected-test coverage, not full-suite coverage. Java sources/tests
use Gradle's defaults; scanAll also
includes supported non-JVM files. Keep the server's Quality Gate enabled: a scan
that uploads successfully but fails the gate is a failed workflow.

Community Build receives no branch or pull-request analysis parameters. Only
`dev` feeds this project's single branch. Rename the SonarQube project's main
branch to `dev` in its UI for clarity; a remaining `main` label does not mean the
workflow is analyzing GitHub main.

The `SonarQube dev gate` check on PRs into `main` requires a successful scan of
the exact same-repository `dev` head SHA. It rejects missing, stale, skipped,
cancelled or failed scans and waits up to 15 minutes for an in-progress scan.
An older successful scan cannot hide a newer failure. This PR check uses only
GitHub Actions read permission and receives no SonarQube credentials. Configure
it as a required status check for main, alongside the Windows/Linux validation
jobs. If a scan finishes after the gate times out, rerun the failed gate check.

Configure security conditions and the New Code definition in the SonarQube UI.
The project uses the `SeeLoggyPlus JavaFX` CSS quality profile, copied from
`Sonar way core`. Its selector parameters are recorded in
`config/sonar-javafx-css-profile.json`. Keep the CSS rules and their severities
enabled; the exact additional pseudo-classes and control types are JavaFX
selectors, not wildcard exclusions. Associate this profile only with this project
and compare it with the built-in base after Sonar upgrades to retain new rules.
Review the initial baseline and security hotspots rather than weakening the gate
or broadly excluding application code to make it green. Review the combined
coverage artifact for untested new behavior when a coverage condition fails;
do not disable that condition or exclude new application code to pass it.

## Microsoft Store distribution

The separate Windows x64 MSIX includes a console-free launcher and Java runtime.
The registered identity is `AriefMahendra.SeeLoggyPlus`, publisher
`CN=A19804B0-0953-43F2-B116-C24E0EED56BE`, display name `Arief Mahendra`.

Build on Windows x64 with JDK 21 and Windows SDK packaging tools:

```powershell
.\gradlew.bat --no-daemon packageMicrosoftStore
```

If MakeAppx is outside the standard SDK location, supply
`-PmakeAppx="C:/path/to/x64/makeappx.exe"` or set `MAKEAPPX_EXE`.
Store launcher creation also uses the Windows SDK Manifest Tool, `mt.exe`, to
embed the application's x64 identity and PerMonitorV2 DPI declaration. Override
its location with `-PmanifestTool="C:/path/to/x64/mt.exe"` or `MT_EXE` when needed.
The unsigned submission package is
`build/distributions/SeeLoggyPlus-<version>-x64.msix`. Microsoft Store handles
production signing. This is not a signed package for direct sideloading.
The project version must be `major.minor.patch`; the MSIX version becomes
`major.minor.patch.0`, with each component at most 65535.

To inspect the native launcher, runtime, manifest and assets without MakeAppx:

```powershell
.\gradlew.bat --no-daemon stageStorePackage
```

The staged files are in `build/store/package`. Store data, credentials, known
hosts, logs and memory settings live under `%LOCALAPPDATA%/SeeLoggyPlus/Store`.
The launcher reads `launcher.properties` before starting the bundled JVM, so
memory settings apply on the next launch. Portable data is not automatically
imported. Neither variant checks for, downloads or installs GitHub updates.
Portable has no update notifications or What's New popup. The Store variant
provides Help → Open Microsoft Store and shows bundled What's New notes once
after a version change, with no popup on first installation. Store controls
update delivery. The Store variant does not heal portable launch scripts.

The registered Store ID is `9PDJ4N28SNT7`, configured in
`src/main/resources/store.properties`. The public listing URL is
https://apps.microsoft.com/detail/9PDJ4N28SNT7. The menu opens that product directly
using the Store protocol, with a fallback to its web listing if the protocol
cannot open. `-Dseeloggyplus.storeProductId=<id>` overrides the bundled ID for
testing. Missing or invalid IDs fall back to a Store search. A Store ID is public
and is not a signing secret. The listing becomes available after publication.

The manually dispatched GitHub Actions workflow Build Microsoft Store package
produces an unsigned MSIX artifact without publishing it. Download that artifact
and complete the installed-package checks and Partner Center submission below.

For installed-package validation, sign a copy with a development certificate
matching the manifest publisher and trust it only on the test machine. Keep
private keys outside the repository. Test Start Menu launch, local files, SSH,
saved settings, package upgrades with preserved data and uninstall. Run the
Windows App Certification Kit before submission. App-image smoke testing does
not replace installed-MSIX validation.

Install both Windows App Certification Kit and the SDK's Windows Store managed
tools/metadata. Installing only the certification feature can leave metadata
tests unable to initialize. Test the complete signed MSIX directly from an
administrator PowerShell session:

```powershell
& "C:/Program Files (x86)/Windows Kits/10/App Certification Kit/appcert.exe" reset
& "C:/Program Files (x86)/Windows Kits/10/App Certification Kit/appcert.exe" test -appxpackagepath "C:/path/to/signed-test.msix" -reportoutputpath "C:/path/to/report.xml"
```

Read `OVERALL_RESULT`, `PARTIAL_RUN`, and individual test results from the XML;
the process exit code alone is not the certification result. Keep optional
failures visible. The SDK 10.0.26100.9457 installed-package workflow can fail to
inspect DPI metadata under the protected WindowsApps path even when the same
binary is reported PerMonitorV2-aware by its own BinaryInfo reader outside that
directory. Full MSIX-file validation avoids this observed path-dependent issue
without excluding checks. Keep the original and rerun reports for comparison.

The optional Blocked executables test flags process-launch APIs in the bundled
JVM and launcher, plus executable-like strings in binaries and archives. Those
APIs exist in standard OpenJDK; they cannot all be removed through application
configuration. Review the findings and retain them in the submission evidence.
Microsoft's Desktop Bridge test guidance treats optional tests as informational:
https://learn.microsoft.com/en-us/windows/uwp/debug-test-perf/windows-desktop-bridge-app-tests.
Do not describe an overall PASS with an optional FAIL as all checks passing.

In Partner Center, create an MSIX submission and upload the package. Complete
listings, screenshots, privacy/support links, age ratings and availability.
Explain the required `runFullTrust` capability: this JavaFX desktop application
reads user-selected local logs and connects to user-configured SSH servers.
Use a private audience or package flight for initial distribution.

Follow the normal release gates before publishing. Retain the portable package
layout, update-manifest generation and legacy compatibility gates so older
released applications can still download and activate the transition version.
The new application only confirms startup health for upgrades delivered by old
installers; it does not schedule update checks. Legacy download/staging adapters
remain for those compatibility tools and their end-to-end gates. Keep the JRE
portable ZIP available for manual download. Never put the MSIX in the portable
update manifest. Microsoft Store is the primary Windows distribution channel.

## One-off / manual release (if CI is unavailable)

If a tag run passed its tests and both platform package jobs but failed only at
publication, keep the tag unchanged. Dispatch `Recover release publication` on
main with `release_tag` and `source_run_id`. It verifies the source run's tag,
commit and successful gates, downloads its original artifacts, validates their
manifest and uploaded package digests, and publishes only an existing complete
draft. Release metadata must still match the tested tag. It then publishes the
update manifest to gh-pages. A failed test or package job cannot use this path.

With the GitHub CLI authenticated (`gh auth login`):

```bash
gh release create X.Y.Z \
  --title "SeeLoggyPlus X.Y.Z — <short title>" \
  --notes-file release-notes/X.Y.Z.md
```

Release notes per version are kept in `release-notes/` (e.g. `0.4.3.md`).

Prefer the automated workflow; use this only as a fallback.

## Labels to create (one-time, repository settings)

`feature`, `enhancement`, `bug`, `fix`, `ui`, `design`, `theme`, `test`,
`tests`, `documentation`, `docs`, `build`, `ci`, `chore`, `dependencies`,
`ignore-for-release`.
