# Backend architecture

`KitsuneBackend` is not part of this repository — it isn't published, because it contains anti-abuse logic (rate limiting thresholds, moderation classification, model-tampering detection) that would lose its effectiveness the moment it's public and reverse-engineerable. This document describes its architecture and its **exact, complete database schema** honestly and in full, so the "what does the backend actually store" question can be answered from real evidence rather than a promise.

## What it is

A Kotlin/Ktor server backed by PostgreSQL. Its jobs:

- **Accounts** — anonymous, device-bound registration (no email/phone required) with an optional Google Sign-In link for cross-device sync.
- **Credits & billing** — the in-app currency ("Ofuda") balance, Google Play purchase verification, subscription status.
- **Marketplace** — community-published personas/universes: listings, reviews, reports, follows, creator badges.
- **A proxy for AI calls** — `/v1/chat/completions` and `/v1/embeddings` forward the client's request to OpenRouter (the AI gateway, itself fronting whichever model vendor — OpenAI, Anthropic, Google, and others — the request is routed to) and relay the response back. This is a genuine proxy, not a passthrough with a copy kept: **no table in the schema below stores message/chat content**. The request payload passes through the server's memory in transit (necessarily, to reach the AI provider) but is never written to disk or logged — see the moderation/telemetry notes further down for exactly what *is* recorded from these calls (never the content itself).

What it deliberately does **not** do: it never sees or stores your local Room database, your personas, your universes, or your chat history — those never leave your device except as the literal request text sent to generate the next AI response, which is not persisted anywhere server-side.

## What is intentionally NOT described here

Three areas exist and are real, but their internals are withheld specifically because publishing them would let someone design around them:

- **Rate limiting** — a server-side limiter caps free-tier usage (messages/images per day). Exact thresholds aren't published.
- **Content moderation** — a server-side backstop (local keyword filter + AI classifier) blocks certain categories of content regardless of what the client sends, as a defense the client-side filter alone can't provide. Its exact prompts/thresholds aren't published.
- **Admin authentication** — the internal admin panel requires mandatory TOTP 2FA and supports WebAuthn/FIDO2 hardware security keys. Implementation details beyond "this exists and is mandatory" aren't published.

## Database schema — every table, in full

Every table that exists in the backend's PostgreSQL schema is listed below, with every column. Nothing is omitted or abridged. Doc comments are reproduced where the original code has one.

### Accounts & sessions

**`users`** — `id`, `anonymous_id` (unique), `google_id` (unique, nullable — only set if you link Google Sign-In), `device_id` (unique, nullable — an OS-level per-app-install identifier, not a hardware serial or IMEI), `username` (unique, nullable, user-chosen), `display_name` (nullable), `tier`, `banned`, `frozen`, `banned_device_id`, `kitsune_plus`, `last_monthly_grant_at`, `confirmed_reported_content_count`, `last_follows_check_at`, `created_at`, `last_seen`.
No email, phone number, or real name column exists.

**`sessions`** — `id`, `user_id`, `refresh_token` (unique), `expires_at`. Standard JWT refresh-token storage.

**`ban_records`** — `id`, `user_id` (nullable), `username` (nullable), `device_id` (nullable), `reason`, `banned_by_admin_id`, `banned_at`, `account_deleted`. Deliberately has **no foreign key to `users`**, specifically so a ban survives the underlying account being deleted — otherwise a banned user could delete their account and immediately re-register on the same device with no trace. This is the one place account deletion doesn't fully erase everything; it's a disclosed anti-abuse exception, see [`DATA_COLLECTION.md`](DATA_COLLECTION.md).

**`usage_sessions`** — `id`, `user_id`, `started_at`, `last_ping_at`, `ping_count`. Engagement/retention analytics (session start/duration), tied only to the pseudonymous account id.

### Marketplace & social

**`creator_follows`** — `id`, `follower_id`, `creator_id`, `created_at`. Following another creator on the marketplace.

**`marketplace_listings`** — `id`, `creator_id`, `type`, `title`, `description`, `price_credits`, `maturity_rating`, `genre`, `tags`, `persona_data`, `universe_data`, `preview_image_url`, `download_count`, `average_rating`, `review_count`, `status`, `moderation_note`, `created_at`, `updated_at`, `published_at`. `persona_data`/`universe_data` here are content the user explicitly chose to publish publicly on the marketplace — not private conversation content.

**`marketplace_images`**, **`marketplace_purchases`**, **`marketplace_reviews`**, **`marketplace_reports`**, **`marketplace_listing_translations`** — supporting tables for the listing gallery, purchase/download records, reviews, content reports, and a translation cache for published listing text (all scoped to public marketplace content, not private conversations).

**`creator_badges`** — `id`, `user_id`, `badge_type`, `awarded_at`. Achievement badges (download milestones, ratings, etc.) shown on public creator profiles.

**`user_messages`** — `id`, `user_id`, `subject`, `body`, `related_listing_id` (nullable), `sent_by_admin_id`, `created_at`, `read_at`. One-way moderation messages from an admin to a user (e.g. "your listing was removed") — read-only for the user, not a two-way chat.

**`user_warnings`** — `id`, `user_id`, `reason`, `related_listing_id` (nullable), `issued_by_admin_id`, `created_at`. Internal moderation record, not itself shown to the user.

### Credits & purchases

**`credits`** — `user_id`, `balance`, `lifetime_earned`.

**`transactions`** — `id`, `user_id`, `amount`, `type`, `reference` (nullable), `timestamp`. Credit grant/spend history.

**`rate_limits`** — `user_id`, `date`, `messages_used`, `images_used`. Daily usage *counters* only — the thresholds they're checked against live in server code, not this table.

**`purchases`** — `id`, `user_id`, `sku`, `google_order_id` (unique), `purchase_token` (nullable), `amount_cents`, `currency`, `timestamp`, `verified`. No card/bank details — Google Play Billing handles payment instrument data entirely outside this system.

**`cosmetics_unlocked`** — `user_id`, `cosmetic_id`, `unlocked_at`.

### AI-call metadata (never content)

**`message_telemetry`** — `id`, `user_id`, `operation_type`, `model_id`, `chat_mode`, `content_rating` (an SFW/mature classification only, not text), `prompt_tokens`, `completion_tokens`, `total_tokens`, `credits_charged`, `cost_usd_micros`, `latency_ms`, `error_message` (nullable, a generic error string, never message content), `success`, `timestamp`. This table has **no text/content column of any kind** — it exists purely to track usage volume and real API cost, and that's structurally all it *can* record.

### Transfers & support

**`transfer_sessions`** — `id`, `user_id`, `upload_token` (unique), `pull_token` (unique), `status`, `blob_path` (nullable), `blob_size_bytes` (nullable), `created_at`, `expires_at`, `uploaded_at`, `claimed_at`, `completed_at`. Backs cross-device account transfer via QR pairing — the blob referenced here is opaque, PIN-encrypted client-side; the server never has the transfer PIN and can never decrypt it.

**`bug_reports`** — `id`, `user_id`, `encrypted_key`, `iv`, `ciphertext`, `status`, `created_at`. Every field here is ciphertext or cryptographic metadata — the report body itself is hybrid RSA/AES-encrypted client-side before it ever reaches this table; only the holder of a private key that is never shipped in the app or this repo can decrypt it.

### Proposals & announcements

**`proposals`**, **`proposal_votes`** — `id`/`user_id`/`title`/`description`/`status`/`created_at` and a vote-per-user table for a user-driven "suggest and vote on features" system.

**`announcements`**, **`announcement_reads`** — in-app announcement broadcasting and per-user read tracking, with an optional one-time credit reward on dismissal.

### Admin-only (internal staff, not end users)

**`admin_accounts`**, **`admin_sessions`**, **`admin_security_keys`**, **`admin_audit_log`** — internal admin panel accounts (mandatory TOTP 2FA, optional WebAuthn/FIDO2 keys), their sessions, and an audit log of admin actions. `admin_audit_log` is the **only** table in the entire schema that stores an IP address (`ip_address`) or user agent, and it's scoped to admin-panel actions by internal staff — never end-user activity.

**`system_settings`** — `key`, `value`, `updated_at`. Admin-tunable configuration (which AI model is used for which operation, etc.), not user data.

---

That's the complete list — every table that exists. If you can think of a category of personal data you'd expect a chat app to collect (real name, email, phone, precise location, contacts, message content) and don't see a column for it above, that's because it isn't collected, not because it was left out of this document.
