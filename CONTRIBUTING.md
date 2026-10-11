# Contributing to HandsFree

Read [PRODUCT.md](PRODUCT.md), [README.md](README.md), [SECURITY.md](SECURITY.md) and [PRIVACY.md](PRIVACY.md) first. The project is a development MVP; contributions must distinguish implementation from observed device behavior.

## Development

Use the pinned Android/Gradle versions in the repository and Python 3.12. Follow README setup with your own ignored environment file. Never copy someone else's phone ID or credentials into source. Keep UI, app discovery, reasoning, voice and execution separate. Preserve typed outcomes and independent policy validation.

Run the checks relevant to the change:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --no-daemon
$env:PYTEST_DISABLE_PLUGIN_AUTOLOAD = "1"
python -m pytest backend/tests -q
```

For device changes, install on an authorized phone, use legitimate permission flows and record device/OS, app/model version, locale, exact scenario, result and any manual intervention. Never mark an unrun test passed. Do not include raw private screenshots, transcripts, device identifiers or logs in a PR.

## Pull requests and review

1. Create a feature branch from current main. Keep each PR focused on a real feature or fix; avoid count-padding or unrelated formatting.
2. Add meaningful regression coverage for changed behavior, run relevant checks, and inspect the diff for secrets and misleading claims.
3. Push a same-repository branch when authorized and open a ready-for-review PR into main. External contributors may use forks, but review integrations may offer reduced functionality there.
4. Read ShipWright's summary, inline comments and release recommendation. Address valid findings, explain disagreements, push fixes and obtain review of the latest commit.
5. Complete the maintainer's independent review, including the requested ChatGPT-in-Chrome review when used for this project. Never upload credentials or private device artifacts.
6. Merge only the reviewed head with passing required checks and resolved valid findings. Missing, skipped or stale review is not approval. Explicit human-review conditions require a human maintainer.

The event guide skips PRs over 5,000 changed lines and flags some changes over 800 lines or involving dependencies/security as higher risk. Prefer coherent small changes. Drafts normally require marking ready or an explicit verification request. Use `/shipwright rerun` only when needed for a stale/failed review.

No direct implementation pushes to main, forced history rewrites, automatic permission granting, payment execution or secret-bearing artifacts. The guide's event period does not authorize disguising pre-event work or submitting a project without owner review.

## Languages and app support

New locales require resources, commands, approval/decline handling, privacy/sensitive-action coverage, speech availability checks and native-language review. New app workflows must use current accessible evidence, handle unsupported versions, and stop at protected boundaries. Do not add fixed meals, fake cart success or guessed package IDs.

## Licensing

Contributions are under Apache 2.0 unless otherwise agreed in writing. Preserve copyright/NOTICE files. Record upstream project, pinned revision, file, license and changes for any reused code. Model licenses are separate; do not commit model weights.
