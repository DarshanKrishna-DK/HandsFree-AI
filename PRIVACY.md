# Privacy and data handling

Last updated: 12 October 2026. This describes the current development architecture and separately identifies the planned release. It is not a claim that the phone-only release is already available.

## Current development build

With screen access enabled and a task started, HandsFree reads accessible text and controls in approved apps. It excludes detected protected inputs, but third-party accessibility semantics vary. User goals, relevant sanitized observations, constraints and recent action receipts go over the USB development bridge to the user's laptop for local model reasoning.

Optional conversation audio and response text are processed by the configured speech service through the laptop backend. This requires the app's audio disclosure and Android microphone permission. Provider credentials stay in the laptop environment. The selected provider's own terms and retention practices apply; HandsFree does not claim those requests stay on-device.

Settings persist locally. Task context, approvals and screen observations are held in memory. Audio is temporarily stored in app-private cache during recording/playback and deleted during normal cleanup. Debug diagnostics may expose sanitized observations to the person operating the development build; do not share raw captures or logs publicly.

## Controls

- Use **Stop conversation** to cancel work and end microphone capture.
- Revoke microphone or accessibility access through Android settings.
- Use text when you do not want to use speech processing.
- Clear app storage or uninstall to remove local settings/cache. This does not delete data held by a separately configured speech provider.
- Never enter passwords, OTPs or payment credentials into HandsFree task prompts.

The repository does not require an analytics account or advertising SDK. Credentials, private planning documents, model files, phone captures and build artifacts are excluded from Git.

## Planned phone-only release

The release plan requires on-device reasoning, verified model storage, explicit speech capability/disclosure, no hidden remote fallback and private transient task data. Model downloads contact the selected artifact host and expose ordinary network metadata. On-device inference does not make online third-party apps offline.

Before publishing a stable APK, this document must be checked against the actual release build, including model downloads, language packs, temporary-file cleanup, backup exclusions and any optional network service. Report discrepancies through [SECURITY.md](SECURITY.md).
