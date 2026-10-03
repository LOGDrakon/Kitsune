# Architecture

Kitsune is a multi-module Gradle project: `core:*` / `feature:*` / `app`, wired together with Hilt.

**Module dependency direction is always `feature → core`, never `feature → feature`.** Only `app` (the single-Activity host, navigation graph, and top-level DI wiring) is allowed to depend on more than one `feature` module — it composes everything at the top. This keeps every feature module independently buildable/testable and prevents screen-to-screen coupling from creeping in.

## Design system and information architecture (v2)

v2 rebuilt the app's surface and kept its infrastructure. Two documents carry the detail —
[`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) for the visual layer and
`KitsuneBackend/docs/PRICING_V2.md` for the commercial one — but the two structural facts belong here.

### One shell, five destinations

Everything past the vault unlock hangs off `KitsuneShell`. Five tabs, each answering a different
question, and **nothing appears in two of them**:

| tab | answers |
|---|---|
| Histoires | what am I in the middle of? |
| Créer | what have I made, and what do I want to make? |
| Découvrir | what has everyone else made? |
| Profil | who am I here, and what do I have? |
| Réglages | how does this behave? |

That invariant is the architecture, not a style preference. v1 split personas and universes across two
tabs even though authoring a cast and authoring a world are one activity; it put a community
marketplace as a peer of the user's own private library; and it left ten screens reachable only from
menus nested two levels down. The consequences of fixing it were all *removals*: the store, the credit
balance, proposals, messages, followed creators and badges left Settings for Profil, and the tone
library left Settings for Créer.

The tab screens are deliberately thin — they reuse the existing feature-module ViewModels and only
rebuild the UI, which is why the rewrite touched no repository, no use case and no database code.

### A design system, not a theme

`core:designsystem` provides semantic colour tokens, a serif/sans type scale built for prose, and
spacing/shape/motion scales, reached uniformly as `KitsuneTheme.colors` / `.type` / `.spacing` /
`.shape` / `.motion`. A Material `ColorScheme` is still derived from the tokens so stock Material 3
components inherit the look rather than rendering Material's defaults.

The load-bearing rule is that **the accent means one thing**: gold marks the single active or live
element on a screen. v1 used Material's `primary` as "make this stand out", which put the brand maroon
on every button, app bar, chip and badge — and left nothing able to mean *this one*.

## Module graph

### `core:*`

| Module | Purpose |
|---|---|
| `core:common` | Cross-cutting utilities (dispatcher provider, etc.) with no Android framework dependency beyond what's unavoidable. |
| `core:security` | The vault — Keystore-backed master key, biometric auth, Argon2id app PIN, HKDF-based passphrase derivation, encrypted image storage, encrypted preferences, panic PIN / decoy system, age verification. See [Vault & security architecture](#vault--security-architecture). |
| `core:data` | Room entities/DAOs/repositories, the SQLCipher-encrypted database itself, migrations. Exposes database open/close state as a `StateFlow` so it can be locked/unlocked in step with the vault. |
| `core:network` | The client for Kitsune's own backend's AI proxy (Retrofit + kotlinx.serialization) — chat completions, live model catalog consumption (no static/hardcoded pricing table, always the backend's real-time OpenRouter catalog), image generation, and the persona visual-continuity system. The backend itself proxies every AI call to OpenRouter (originally Mammouth.ai, then five direct providers) — the app never talks to an upstream LLM provider directly. |
| `core:memory` | The four-layer long-term memory pipeline, fully decoupled from `feature:chat` behind use cases. See [Long-term memory pipeline](#long-term-memory-pipeline). |
| `core:moderation` | Local keyword pre-filter for hard-blocked content categories, safe-word handling. |
| `core:diagnostics` | Anonymized, redacted, end-to-end-encrypted bug report generation. |
| `core:designsystem` | The design system: semantic colour tokens, the serif/sans type scale, spacing/shape/motion scales, and ~30 shared components. See [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md). |
| `core:backend` | Retrofit client for Kitsune's own backend (accounts, credits, marketplace, badges, purchases) — not the AI provider. |
| `core:billing` | Google Play Billing integration for real-money purchases (credit packs, subscription). |
| `core:background` | WorkManager-based jobs so long-running AI generation (personas, universes, NPCs) survives the app being closed. |
| `core:models` | Shared UI helpers for browsing/filtering/picking AI models from the live catalog. |
| `core:transfer` | Cross-device account transfer via QR code pairing, with its own independent encryption scheme (no dependency on Google Play Services). |

### `feature:*`

One module per screen area — Compose screens + ViewModels, depending only on `core:*` modules: `onboarding`, `auth`, `persona`, `universe`, `chat`, `settings`, `marketplace`, `store`, `cosmetics`.

### `app`

`KitsuneNavHost`/`Routes` (a single-Activity navigation graph, string routes with transitions), the Hilt entry point, and top-level DI wiring.

## Vault & security architecture

The local database's SQLCipher passphrase is **not a stored secret** — it's derived at unlock time and never persisted in the clear. Which factors feed the derivation depends on the currently selected security mode:

- **Both PIN and biometric** (default): `HKDF(randomDbKey + Argon2id(PIN), "kitsune-vault-passphrase-v1")` — `randomDbKey` is unwrapped via a biometric-gated Keystore cipher on every unlock.
- **PIN only**: same formula, a distinct HKDF info string — `randomDbKey` is read directly from encrypted local storage instead, no biometric prompt at all.
- **Biometric only**: `HKDF(randomDbKey, "...-biometric-only-v1")` — no PIN material folded in at all.

Each mode uses a **distinct HKDF info string**, specifically so a passphrase derived under one mode's rules can never accidentally satisfy another's. Switching security mode is a real database re-encryption (`PRAGMA rekey`), not a UI-level gate.

A **panic PIN** exists as a duress feature: entering it routes to a fully separate, independently-encrypted decoy notes app rather than a fake static screen — and it's designed so that no code path under any security mode can ever derive a working real-database passphrase from it.

Every image (persona avatars, galleries, chat backgrounds) is individually encrypted at rest and referenced only by an opaque id — never stored as a plain file path.

## Long-term memory pipeline

Four layers, assembled adaptively into the chat's system prompt based on the selected model's context window:

1. **Raw window** — the last N messages, sent verbatim. N scales up for models with larger context windows.
2. **Rolling summary** — merges new events into an existing running summary once a backlog threshold is crossed, and self-compacts once the summary itself grows past a size threshold.
3. **Lore entries** — structured entity sheets (characters including minor NPCs, locations, factions, events, objects), extracted alongside the rolling summary and matched by name to update rather than duplicate.
4. **Semantic retrieval** — message chunks are embedded and indexed on an ongoing basis; relevant older context is retrieved via on-device cosine-similarity search over those embeddings, independent of the summary/lore trigger.

This system replaced an earlier, much heavier design (multiple separate "satellite" memory systems — objectives, journal, relationships, mood, and more) that cost significantly more per update for comparable narrative value. The four-layer system above covers the same ground for a fraction of the LLM calls.

## Conversation forking

Any conversation can be branched at a chosen message into an independent copy, with its own summary/memory state, linked back to the point it was forked from — a way to explore a narrative "what if" without losing or overwriting the original thread.

## Persona visual continuity

Personas carry a structured "visual sheet" (art style, palette, distinguishing physical traits) that's fed back as context into every subsequent image generation for that character — avatar, gallery, and in-scene illustrations stay visually consistent over time instead of drifting with each new generation.
