# Security policy

HandsFree is currently a development MVP. No stable supported security release exists yet. Do not use it to authorize payments, enter protected credentials or perform unattended consequential tasks.

## Report privately

Use GitHub's **Security → Report a vulnerability** on this repository if available. If private reporting is unavailable, contact the maintainer using the contact information on their GitHub profile; do not post exploit details, credentials, screen contents or personal data in public issues. A public issue may request a private contact without describing the vulnerability.

Include the affected commit/version, Android version, reproduction steps and a minimal sanitized example. We have not committed to a response-time SLA. Maintainers should acknowledge, reproduce, fix, add regression coverage and coordinate disclosure before publishing exploitation details.

## Security invariants

- Screen content and model output are untrusted, including instructions embedded in third-party UI.
- Validate current app scope, observation identity, target capability and consent immediately before execution.
- Stop invalidates pending work; old transcripts must not approve new actions.
- Passwords, OTPs, PINs, financial authorization and final consequential actions remain user-controlled.
- No root, silent installation, permission bypass, arbitrary generated shell code or blind coordinate fallback.
- No embedded shared API keys, signing keys or sensitive release logs.
- Model artifacts need pinned identity and integrity verification before activation.

The current laptop bridge is for local development. Keep Ollama and the bridge bound to loopback; do not expose them as public services. See [PRODUCT.md](PRODUCT.md) for the phone-only release requirements and [PRIVACY.md](PRIVACY.md) for current data handling.
