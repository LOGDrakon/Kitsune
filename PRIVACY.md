# Privacy

What leaves your phone, and where it goes. Every statement here can be checked in the source code.

## Stays on your phone

- Conversations, characters, universes, lore, memories, images, your profile (name, age, description),
  your settings. They live in an SQLCipher-encrypted database (`core:data`) and an encrypted image
  store (`core:security`), whose key is derived at unlock time and never stored in the clear.
- Your AI providers' API keys, in encrypted preferences (`SecureStorage`).

## Sent to your AI provider — and only to it

When you write, generate or translate, the request goes **directly** from your phone to the provider
you configured (`core:network/provider/LlmHttpClient.kt`). It contains what the model needs: the
system prompt (character sheet, story summary, lore, your profile details if you filled them in) and
the recent messages. Kitsune has no server in between.

Each provider has its own retention and training policies. With OpenRouter, you can exclude providers
that collect data or require zero data retention in **Settings → AI providers**.

## Sent to the marketplace server — only if you use it

The marketplace is optional (**Settings → Marketplace**). When it is off, Kitsune contacts no server at
all. When it is on, nothing is sent until you open the marketplace; then:

- **An anonymous account** is created on the server, identified by a random id and your device's
  `ANDROID_ID` (an identifier Android derives from the device and the app's signing key; it is used to
  enforce bans against abusive accounts).
- **What you publish** — a character or universe sheet and its images, your username, your reviews,
  reports and idea proposals — is stored on the server and visible to others.
- **What you browse** — listing searches, downloads, follows.

The server never receives your conversations. Its source code is public:
[Kitsune-Server](https://github.com/LOGDrakon/Kitsune-Server). You can point the app at another
instance, including your own.

## Never

- No analytics, no crash reporting, no advertising SDK, no telemetry.
- Bug reports are not sent anywhere automatically: the app builds an anonymised report and opens the
  share menu, so you decide where it goes and can read it first.

## Backups

**Settings → Backup** writes everything (stories, images, settings, providers and their keys) to a file
encrypted with a passphrase you choose (Argon2id + AES-GCM). Without the passphrase the file cannot be
read. Store it wherever you like; nothing is uploaded.
