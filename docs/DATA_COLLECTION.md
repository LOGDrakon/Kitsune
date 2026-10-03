# What Kitsune collects, and what it doesn't

This is the precise, honest version of the privacy claim — cross-referenced to [`BACKEND_ARCHITECTURE.md`](BACKEND_ARCHITECTURE.md)'s full database schema and to specific files in this repository, so every line here is something you can go verify rather than take on faith.

## Never collected, at all

- **Real name, email address, or phone number.** Account creation requires none of these — see the `users` table schema: no such column exists. Registration is anonymous and device-bound by default; linking a Google account is optional and only adds a `google_id`.
- **Precise location.** No location field anywhere in the schema, no location permission requested by the app.
- **Contacts, calendar, or any other device data unrelated to the app's own function.**
- **Message/chat content, on the server, ever.** No table in the entire backend schema has a column for conversation text, persona sheets (private ones — published marketplace listings are a different, explicitly-public case), or universe data. See `BACKEND_ARCHITECTURE.md`'s full table list.
- **Third-party analytics or advertising identifiers.** No analytics SDK is linked into the app at all — check any module's `build.gradle.kts` dependency list yourself.

## Collected, and why — always pseudonymous, tied to a device-bound account id, never a real identity

| What | Where | Why |
|---|---|---|
| An anonymous account id + optional device identifier (`ANDROID_ID`, not a hardware serial) | `users` | Lets your account and purchases persist across app restarts and reinstalls without requiring an email/phone. |
| Username / display name, if you choose to set one | `users` | Optional pseudonym, required only if you want to publish to the marketplace (so reports/moderation have something to reference). |
| Credit balance & transaction history | `credits`, `transactions` | The in-app currency you use for AI generation and purchases. |
| Purchase records (SKU, Google order id, amount) | `purchases` | Required to verify and fulfill real-money purchases. No card/bank details — Google Play Billing handles that entirely outside this system. |
| Daily usage counters (messages/images used today) | `rate_limits` | Enforces free-tier daily limits. Counters only, not content. |
| AI-call metadata: model used, token counts, cost, latency, success/failure | `message_telemetry` | Business metrics (cost per credit, model performance) — **no content column exists in this table at all**, structurally incapable of storing what you actually said. |
| Marketplace content you chose to publish (listings, reviews, reports you filed) | `marketplace_*` tables | You explicitly chose to make this public by publishing/reviewing/reporting. |
| Bug reports, but only as ciphertext | `bug_reports` | Encrypted client-side (hybrid RSA/AES) before it ever reaches the server; only decryptable by a private key never shipped in the app. |

## The two honest caveats — disclosed, not hidden

1. **Your message text passes through Kitsune's backend server in transit, on its way to the AI provider (OpenRouter, which routes to the underlying model vendor), to generate a response.** It is never logged or written to any table there (see the schema — there is nowhere for it to go), but "passes through in transit" is a real, different claim from "never touches our infrastructure at all," and we say the more precise version rather than overclaiming.
2. **A banned account's device identifier and username deliberately survive account deletion**, stored in `ban_records` (which has no foreign key to `users`, specifically so it isn't cleaned up when the account is deleted). This is a disclosed, deliberate anti-abuse exception — without it, a banned user could delete their account and immediately re-register on the same device with no trace of the ban. Nothing else about a deleted account persists.

## On-device (never sent anywhere)

Everything else — your actual conversations, personas, universes, images, and local settings — lives in a SQLCipher-encrypted database on your device, unlockable only with a key derived from your own PIN/biometric at unlock time and never stored anywhere in the clear, including by Kitsune. See [`ARCHITECTURE.md`](../ARCHITECTURE.md#vault--security-architecture) for exactly how that derivation works.
