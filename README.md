# Kitsune

Kitsune is a free and open-source Android app for AI roleplay and interactive storytelling: create
characters and whole fictional universes, then write stories with them.

- **Bring your own AI.** Kitsune has no AI server. You add the API key of the provider you choose —
  [OpenRouter](https://openrouter.ai), [Mammouth](https://mammouth.ai), OpenAI, Mistral, DeepSeek,
  Groq, Together, NanoGPT, a local Ollama / LM Studio, or any OpenAI-compatible service — and your
  messages go straight from your phone to that provider.
- **Nothing for sale.** No subscription, no in-app purchase, no ads, no telemetry.
- **Your stories stay on your phone**, in an encrypted vault.
- **An optional community marketplace** to share and download characters and universes, served by
  [Kitsune-Server](https://github.com/LOGDrakon/Kitsune-Server). You can switch it off, or point the
  app at your own instance.

The interface is available in French, English and 13 other languages.

What the project will and will not do — no engagement tricks, simple-by-default settings, private
fiction left alone, a marketplace that does not need an army of moderators — is written down in
[PRINCIPLES.md](PRINCIPLES.md).

## Features

**Writing**
- A four-layer long-term memory — recent messages, a self-compacting chapter summary, structured lore
  sheets (characters, places, factions, events) and semantic retrieval — so long stories stay coherent.
- Ensemble scenes with a whole cast, a story director, reply suggestions, branching ("what if?") forks,
  a rewind, a chronology of key moments, novel mode with PDF export.
- Writing-style packs (cinematic, stage play, light novel, visual novel), story presets, a "never write"
  list, a safe word.
- Memory and reply length you choose yourself: three presets for small, standard and large-context
  models, or the exact values in the advanced settings.
- Character and universe generation, an inspiration wizard, sheet translation, and
  [Character Card V2](https://github.com/malfoyslastname/character-card-spec-v2) import and export
  (the format used by SillyTavern and most character sites).

**Models and providers**
- Several providers at once, and one model per kind of task: the story, Pro mode, summaries, lore,
  embeddings, generation, translation, images, scene descriptions…
- A model picker with search, provider filter, sorting and price/context filters.
- Full [OpenRouter provider routing](https://openrouter.ai/docs/features/provider-routing): preferred
  sort (price, throughput, latency), allowed quantizations, minimum throughput, maximum latency,
  preferred / exclusive / excluded providers, no data collection, zero data retention — plus an
  inspector showing which providers serve a model, with their precision, speed, uptime and price.
- Image generation with per-character visual continuity.

**Privacy and security**
- SQLCipher database whose key is derived at unlock time (Argon2id PIN and/or biometrics, HKDF) and
  never stored in the clear; every image encrypted individually.
- Auto-lock, screenshot protection, discreet mode, and a panic PIN that opens a separate decoy app.
- Encrypted backups to a file, protected by a passphrase, to move to another phone.
- See [`PRIVACY.md`](PRIVACY.md) for exactly what leaves the device.

## Getting started

1. Install the app (see Releases) or build it: `./gradlew assembleDebug` (JDK 17, Android SDK 35).
2. Create your vault PIN.
3. In **Settings → AI providers**, add a provider and its API key. With OpenRouter, sensible default
   models are pre-selected; with any other provider, pick a chat model in **Settings → Models**.

### Building against your own marketplace server

The default marketplace server is set at build time:

```
./gradlew assembleRelease -Pkitsune.marketplaceUrl=https://your-server.example
```

Users can also change it at runtime in **Settings → Marketplace**.

## Content

Kitsune is intended for adults (an age check runs at first launch). It does not filter what you write.
The only content rule it states itself — in the system prompt, never by blocking your text — is that
no sexual content may involve minors; characters must be adults. Your AI provider applies its own
policies on top.

## Project layout

A multi-module Gradle project (`core:*`, `feature:*`, `app`) in Kotlin and Jetpack Compose, wired with
Hilt. See [`ARCHITECTURE.md`](ARCHITECTURE.md) and [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).

```
./gradlew test                          # unit tests
./gradlew :feature:chat:compileDebugKotlin
./gradlew assembleDebug
```

## Contributing

Issues, ideas and pull requests are welcome; please read [PRINCIPLES.md](PRINCIPLES.md) first, it ends
with the questions every new feature has to answer. Bug reports can be generated from the app
(**Settings → Support**): they are anonymised (names redacted, no key or identifier) and open in the
share menu so you can read them before posting.

## Support

Kitsune sells nothing. If you want to help pay for the community marketplace server, you can
donate on [Open Collective](https://opencollective.com/kitsuneapp). The money goes to the project's
collective, not to a person, and every expense it pays (server, domain) is public.

## License

Copyright © 2026 LOGDrakon.

Kitsune is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) or (at your option) any later version.
