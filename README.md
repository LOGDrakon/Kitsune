# Kitsune

Kitsune is a native Android AI roleplay/chat app — create personas and whole fictional universes, then chat with them. It's built by a single independent developer, security-first: everything sensitive is encrypted at rest, on your device, with a key the developer never possesses.

This repository is published **as a technical showcase and as concrete proof of how little data the app actually collects** — every claim below is backed by a file you can go read yourself. See [`LICENSE`](LICENSE) for the terms this code is published under (source-available, all rights reserved — not open source in the OSI sense).

## What makes Kitsune different

- **A real, layered long-term memory system.** Four layers — a raw recent-message window, a self-compacting rolling summary, structured "lore" entity sheets (characters, locations, factions, events), and on-device semantic retrieval over message embeddings — all assembled adaptively into the model's context window, so long-running stories stay coherent instead of degrading into repetition. See [`ARCHITECTURE.md`](ARCHITECTURE.md#long-term-memory-pipeline).
- **A vault, not just "encrypted storage."** The local database's encryption key is derived at unlock time from a biometric factor and/or an Argon2id-hashed PIN via HKDF, and is never persisted anywhere in the clear. Switching between security modes (PIN-only / biometric-only / both) triggers a real re-encryption of the database, not a UI-level gate. A panic PIN routes to a fully separate, independently-encrypted decoy app rather than a fake static screen. See [`ARCHITECTURE.md`](ARCHITECTURE.md#vault--security-architecture).
- **Persona visual continuity.** Personas carry a structured "visual sheet" (art style, palette, distinguishing traits) that's fed back into every subsequent image generation, so a character's portrait, gallery, and in-scene illustrations actually look like the same character over time instead of drifting.
- **Conversation forking.** Any conversation can be branched at a chosen point into an independent copy with its own memory state — explore a different "what if" without losing or overwriting the original.
- **A design system, not a theme.** Semantic colour tokens, a serif/sans type scale built for hour-long
  reading sessions, and one shared component library — so unrelated screens read as the same product.
  The accent colour marks the single active element on a screen and nothing else. See
  [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).
- **Pricing that is published, not discovered.** One currency, four consumable packs, **no
  subscription**, and nothing cosmetic behind a paywall. The full tariff — including everything the
  developer pays for and never bills (the rolling summary, lore extraction, the chronology, the
  returning-user recap, sheet translation, the brainstorming assistant) — is on one screen in the app,
  read live from the server rather than hard-coded. The balance appears in exactly one place, and never
  while you are reading.
- **A real creator economy.** A community marketplace for sharing personas/universes, with a genuine badge/progression system (download tiers, ratings, creator milestones) and public creator profiles — not a bolted-on stub.

## Privacy & security — what's actually provable from this code

- **No chat/persona/universe content is ever sent anywhere for storage.** All of it lives in a local, SQLCipher-encrypted Room database (`core:data`). There is no messages/chats/personas table anywhere on the backend side.
- **The encryption key is never stored anywhere in the clear, and never leaves your device.** See `core/security/src/main/java/com/kitsune/core/security/vault/VaultKeyProviderImpl.kt` — the passphrase is derived at unlock time via HKDF from a Keystore-wrapped random key plus (depending on your chosen security mode) an Argon2id-hashed PIN or a biometric factor.
- **Every image (avatars, galleries, chat backgrounds) is individually encrypted at rest**, never stored as a plain file path — see `core/security/src/main/java/com/kitsune/core/security/storage/EncryptedImageStore.kt`.
- **No third-party analytics or tracking SDK of any kind.** Check `app/build.gradle.kts` and every module's dependency list yourself — no Firebase, no Crashlytics, no ad SDK, nothing that phones home about your usage beyond the app's own backend (see below).
- **Bug reports are end-to-end encrypted** with a keypair where only the developer's private key (never shipped anywhere in this repo or the app) can decrypt them — see `core/diagnostics/`.
- **What does leave the device, honestly**: your message text is sent to OpenRouter (the AI gateway, routing to the underlying model vendor) in real time to generate a response, and passes through Kitsune's own backend server in transit to get there. Neither Kitsune's backend nor this repo ever logs, stores, or persists that content — but "in transit" is not the same as "never touched," and we say so plainly rather than overclaiming. See [`docs/DATA_COLLECTION.md`](docs/DATA_COLLECTION.md) for the full, precise breakdown of what is and isn't collected, table by table.
- The backend itself (`KitsuneBackend`) is not published in this repository — it handles accounts, credits/billing, and the marketplace, and contains anti-abuse logic (rate limiting, moderation) that would lose its effectiveness if published. Its architecture and exact database schema are documented transparently anyway: see [`docs/BACKEND_ARCHITECTURE.md`](docs/BACKEND_ARCHITECTURE.md).

## Tech stack

Kotlin, Jetpack Compose, Hilt (DI), Room + SQLCipher (encrypted local database), Retrofit + kotlinx.serialization, Android Keystore + BiometricPrompt, Argon2id (Argon2Kt), WorkManager, Google Play Billing.

## Architecture

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full module graph and a deeper dive into the systems above.

## License

Source-available, all rights reserved. You may read and clone this repository for reference, educational, and portfolio-evaluation purposes. You may **not** copy, redistribute, sublicense, or reuse this code (in whole or in substantial part), commercially or non-commercially, without prior written permission. See [`LICENSE`](LICENSE).
