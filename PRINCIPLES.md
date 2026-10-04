# Kitsune principles

Kitsune is a community project: an open-source AI roleplay app whose goal is to make this kind of app
**healthier for the people who use it**. Nothing here is sold, so nothing has to be optimised against
the user. These principles explain what the project will and will not do; contributions, feature
proposals and forks are judged against them.

## 1. Your stories belong to you

- Conversations, characters and universes live on the phone, in an encrypted vault. There is no
  Kitsune account and no Kitsune AI server.
- You bring your own key: your messages go straight from your phone to the provider **you** chose.
- No telemetry, no analytics, no third-party SDKs. What leaves the phone, and where it goes, is listed
  in [PRIVACY.md](PRIVACY.md).
- The community marketplace is optional, can be switched off entirely, and can point to any
  self-hosted [Kitsune-Server](https://github.com/LOGDrakon/Kitsune-Server). It never receives
  conversation content.
- Everything can be exported in an encrypted backup, and characters in the open Character Card format.

## 2. Nothing is sold, and nothing competes for your time

- No payment, subscription, tier, ad or "premium" feature. Every feature is available to everyone.
- **No engagement mechanics**: no streaks, daily rewards, "come back" notifications, unprompted
  messages from characters, guilt prompts, artificial scarcity or gamified retention. A character never
  contacts you outside a story you opened yourself.
- The app is judged by the stories it helps write, not by the time spent in it.

## 3. Healthy by design

- **You stay in control of the fiction**: a safe word that stops a scene at once, a "never write" list
  that outranks everything else, edit, rewind, regenerate and fork at any point, director tools to
  steer the story.
- **Fiction is presented as fiction.** Characters are characters and the AI is a co-author. The app
  does not present a character as a real or conscious person, and does not build emotional dependence
  as a feature (no "I missed you" hooks, no jealousy of your real life to keep you talking).
- **Transparency**: settings say what they send to the model and roughly what they cost.

## 4. Simple by default, deep on demand

- Everything works without configuration: add a key and write.
- Every setting has two levels: **simple** (a few presets in plain words) and **advanced** (the real
  values behind them). The advanced level is never required to get a good result.
- A preset never lies: change one of its values and it becomes "Custom".
- No feature is hidden behind a mode, a level or an unlock. Depth is a choice, never a gate.

## 5. Freedom and responsibility

- **Private fiction is not policed.** The app does not read, filter, score or report what you write.
  Adult fiction, dark themes and violence between adult characters are allowed.
- You are responsible for what you generate with your own key, under your provider's terms. Your
  provider's own policies still apply to what you send it.
- **The one line the project itself holds: no sexual content involving minors.** Characters are adults
  (an 18+ floor on every character), the model is instructed accordingly at the top of every story,
  and nothing the project ships or hosts — presets, examples, marketplace listings — sexualises a minor
  or a childlike character, *whatever age is claimed for them*. On the marketplace a character is judged
  by how it is presented, not by the number written in its sheet.
- This line instructs the model; it never blocks or rewrites your messages. Keyword filters and
  automatic classifiers on private text produced false positives on legitimate fiction and are not
  coming back.

## 6. A community without a moderation treadmill

The marketplace is the only shared space, and it is built so that a few volunteers can run it.

- **Structured interactions over free text**: downloads, ratings, votes, follows and reports. There is
  no chat, no direct messages and no comment threads between users; free text that other users read is
  limited to the listings themselves and to idea proposals.
- New creators' first listings go through a review queue; after that they publish directly. Reports go
  to a queue. An optional AI pre-screen can send a listing to review, never refuse it on its own.
- Bans are rare and simple (account and device). A refused or removed listing comes with a reason sent
  to its author.
- **Any new shared or social feature must state its moderation cost before it is built.** If it needs
  someone to read user-to-user text every day, the answer is no.

## 7. Minimal data, minimal liability

- The server stores only what the marketplace needs: an anonymous id, a pseudonym, listings, ratings,
  votes, follows, reports, and a device identifier used against ban evasion. Nothing from
  conversations, no e-mail address, no real name.
- Users can delete their marketplace account and everything attached to it from the app.
- A feature that needs new personal data must justify it, keep the least possible, and say so in
  PRIVACY.md.
- The project hosts no generated content other than marketplace listings, and acts on reports of
  illegal content promptly.

## 8. Respect the user's wallet

- Every AI call is paid by the user, at their provider's price. No hidden or decorative calls.
- Features that add calls in the background say so, and the expensive ones are opt-in.
- The memory pipeline is designed to remove calls, not add them.

## 9. Open and forkable

- The app is GPL v3, the server AGPL v3. Anyone can run their own marketplace or fork the app.
- Ideas come from the community (in-app proposals, GitHub issues); decisions and their reasons are
  written down.
- Donations go to the project's [Open Collective](https://opencollective.com/kitsuneapp), where every
  expense is public. They pay for the server, never for features.

## Checking a new feature

Before proposing or merging a feature, answer these questions:

1. Does it work with no configuration, and does its depth sit behind an "advanced" level rather than
   in the way?
2. Does anything new leave the phone? If so, where to, and is it in PRIVACY.md?
3. How many AI calls does it add, and who decides to make them?
4. Does it try to bring the user back or keep them longer? If so, drop that part.
5. Does it create a space where users read each other's free text? What will moderating it cost?
6. Does it store new personal data on the server?
7. Does it weaken the minors line in anything the project ships or hosts?
