# HandsFree product and release plan

HandsFree helps people who need accessible Android navigation complete everyday tasks through voice or text. Its first stable release must work on the phone without a computer, USB debugging or a mandatory hosted account. English, Kannada and Hindi are the initial language targets.

**Current status: development MVP.** The existing implementation uses a laptop for reasoning and speech requests. Phone-only reasoning, Hindi, complete continuous voice acceptance and successful live food-to-cart acceptance are release work, not shipped capabilities.

## Product requirements

- Explicitly started voice or text conversation, corrections, clarification and an always reachable Stop control.
- Real installed-app discovery and verified launch; ask about ambiguous choices and guide missing-app installation through official Android flows.
- Grounded food search and comparison, user-selected items/quantities, current cart evidence and the displayed payable total. Stop before checkout, order placement or payment.
- No assumed dish for a general meal request, invented rating/price/availability or implied scheduled delivery.
- On-device model setup with compatibility/storage checks, verified artifacts, progress, cancellation, deletion and recovery.
- English/Kannada/Hindi interfaces and voice capability checks. Text remains available when speech is unavailable; advertised voice languages require physical acceptance.
- Accessible cream/pink conversation UI using the supplied logo, large controls, TalkBack semantics, font scaling and clear listening/working/waiting states.
- Strict local validation of model proposals, app scope, observation freshness and one-time approvals. Protected inputs and consequential final actions remain manual.
- No release dependency on the development localhost bridge. No undisclosed remote inference or network speech fallback.

## Architecture

Compose UI → task controller → sanitized accessibility observation → planner interface → typed proposal → deterministic safety checks → Android executor → fresh observation and completion verification.

The planned on-device adapter uses a pinned native inference runtime and verified model artifact. The current Python/Ollama adapter remains useful for development comparison. Speech adapters have separate availability, privacy and lifecycle handling. Model output and app screen text never grant permissions.

Persistent data is limited to user settings and installed model metadata/files. Tasks, consents, observations and voice turns are transient. Stop, lock, process death and private-input detection invalidate outstanding work. Sensitive screen/audio data must not enter release logs or repository history.

## Delivery sequence

| Stage | Deliverable | Required evidence |
| --- | --- | --- |
| R0 | Open-source foundation | Scope, contribution/security/privacy documents and baseline checks |
| R1 | Model lifecycle | Capability checks, verified import/download, storage/cancel/recovery tests |
| R2 | Phone-only planner | Actual on-device proposals, safe cancellation, release run without USB/backend |
| R3 | Grounded execution | Ambiguous-target rejection, fresh observations, receipts and bounded recovery |
| R4 | Multilingual conversation | Three locales, on-phone speech, stale-approval and cross-app lifecycle tests |
| R5 | Accessible UX | Onboarding, model setup, conversation and settings tested with accessibility tools |
| R6 | Live task acceptance | Supported food app/cart evidence and honest compatibility matrix |
| R7 | Release hardening | Security/performance review, signed artifact, clean-install/update and release notes |

Each stage may contain multiple small feature PRs. A dependency cannot be considered complete merely because its interface or tests exist.

## Stable release gates

1. Real phone-only reasoning on the release variant with USB disconnected and the laptop backend stopped.
2. English/Kannada/Hindi recognition, speech output, user replies and safe Stop tested physically; native-speaker review of translated safety prompts.
3. Five consecutive live requested-item-to-cart runs on a supported app, with current item/quantity/total proof and no order/payment. Fixtures are identified separately.
4. Permission denial/revocation, screen lock, process death, malformed output, malicious screen text, stale approvals and changed prices fail safely.
5. TalkBack, 200% font scale, keyboard/insets and representative user usability testing passed.
6. Published device/OS/model/app-version matrix and measured latency, memory and thermal behavior. No universal compatibility or real-time claim.
7. CI, external review and valid review findings resolved on the exact release commit.
8. Maintainer-controlled signing, artifact checksums, source provenance, model-license compliance and accurate privacy/setup/support documentation.

Play Store distribution requires a separate accessibility-policy review. A public source repository or signed APK does not establish Play eligibility. See [Google's AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491).

Future work includes additional apps/languages, separately reviewed visual observation and optional remote acceleration. None replaces the phone-only first-release requirement. Ambient recording, automatic payments and universal device control are excluded.

## Reference projects

- [AndroidWorld](https://github.com/google-research/android_world), Apache 2.0: transition settling and independent task evaluation. Inspected `agents/base_agent.py` at `2dfc5ca3dc590efe9c72ce768f2b04182f2f75c7`.
- [Mobilerun](https://github.com/droidrun/mobilerun), MIT: modular device tools and app capabilities. Reference revision `1935ada390f4e82c93daa97bd2c58f4794fc1699`.
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM): candidate Android runtime; compatibility and performance require HandsFree device measurements.

These are design references. No upstream implementation is copied by this document. Any later reuse must retain its license and attribution.
