# Design system — Kitsune v2

Read this before writing any UI in this repository. It is short on purpose: the rules matter more than
the inventory, and the inventory is in the code (`core/designsystem/src/main/java/com/kitsune/core/designsystem/`).

---

## Why v2 has one

v1 had a `KitsuneTheme` that wrapped `MaterialTheme` with a maroon/gold `ColorScheme` and
`Typography()` — the untouched Material 3 default. Everything else was decided at the call site.
Concretely, that produced:

- **Padding as literals.** `12.dp` on one card, `16.dp` on the next, `14.dp` in a third, `10.dp` in the
  chat bubbles, `24.dp` in Settings. No two screens shared a page margin.
- **Radii from 4 to 28** with no rule about which meant what.
- **`primary` used as "make this stand out"**, so maroon ended up on every button, every app bar, every
  chip, every badge — and nothing was left to mean *this one*.
- **No type hierarchy.** `titleMedium` and `bodyMedium` did almost all the work; a story title and a
  settings label were rendered identically.
- **No empty, loading or error components.** An empty list was a centred `Text("Aucun persona")`, a
  loading list was a spinner on a blank screen, a failed call was a red `Text` with the exception
  message. Three of the most-seen moments in the app were the three least designed.
- **Nine variants of a list row**, hand-built per screen.

None of that is a taste problem. It is the mechanical reason unrelated screens did not look like the
same app, which is what "fouillis" actually describes.

---

## The direction

**Sombre éditorial.** The app is a *reading* app — a session is an hour of prose — so it is built like
a book rather than like a dashboard.

- **Ground:** a warm near-black ink (`#0C0A09`). Almost every pixel on screen is that ground plus one
  of four text tints.
- **Accent:** the logo's maroon (`#600C1F` on paper, lifted to `#B8394F` on ink so it stays
  readable), used as an **accent and nothing else**. It marks the one active or live thing on a
  screen. If two things on a screen wear the accent, one of them is wrong. (v2 first shipped with an
  antique gold accent and maroon demoted; that was reverted on 2026-10-03 to match the logo.)
- **Gold is the secondary brand colour** (`brand` token): stars, "free" labels, highlights. Never a
  button.
- **Serif for anything that names a thing** (titles, headings, character names), sans for body and all
  chrome. Two families, split by role, never mixed inside one role.
- **Structure comes from spacing, not lines.** Borders are hairlines you have to look for. Depth is one
  level: a card never nests inside a card.

Light theme is a warm paper (`#FAF6F0`) with the same roles flipped, and is a first-class citizen
rather than an afterthought. Discreet mode (deliberately low-contrast, so the screen is unreadable over
a shoulder) keeps its own palette and drops the serif entirely.

---

## The token layer

Reach everything through `KitsuneTheme`, mirroring how `MaterialTheme` reads at a call site:

```kotlin
KitsuneTheme.colors.textDim      // KitsuneColors  — Theme.kt
KitsuneTheme.type.message        // KitsuneTextStyles — Type.kt
KitsuneTheme.spacing.gutter      // KitsuneSpacing — Tokens.kt
KitsuneTheme.shape.md            // KitsuneShapes  — Tokens.kt
KitsuneTheme.motion.standard     // KitsuneMotion  — Tokens.kt
KitsuneTheme.elevation.floating  // KitsuneElevation — Tokens.kt
```

### Colour — semantic, not Material

`KitsuneColors` names roles, because `primary`/`secondary` say nothing about whether a colour is a
*ground*, a *text step* or an *accent* — and that ambiguity is exactly how v1 turned maroon.

| group | tokens |
|---|---|
| Grounds | `background` · `surface` · `surfaceVariant` · `surfaceBright` |
| Lines | `outline` (hairline) · `outlineStrong` (focused/selected) |
| Text, 4 steps | `text` · `textSecondary` · `textDim` · `textFaint` |
| Accent | `accent` · `accentBright` · `accentContainer` · `onAccent` |
| Brand | `brand` · `brandContainer` |
| Feedback | `error`/`errorContainer`/`onError` · `warn`/`warnContainer` · `success`/`successContainer` |
| Chat | `bubbleIncoming`/`onBubbleIncoming` · `bubbleOutgoing`/`onBubbleOutgoing` |
| Misc | `scrim` · `skeleton` · `isDark` |

A Material `ColorScheme` is still built from these (`KitsuneColors.toMaterial()`) so stock M3
components inherit the look instead of rendering Material's default purple. The notable mapping:
`primary` is the **accent**, and `primaryContainer` is the accent's *tint* — which is what stops a
stock `Button` from being a slab of gold.

**Text-on-surface contrast** (WCAG AA needs 4.5:1 for body, 3:1 for large text): `text` on
`background` is ~15:1, `textSecondary` ~8:1, `textDim` ~4.6:1. `textDim` is therefore the floor for
anything a user has to read — `textFaint` is for disabled states and decorative glyphs only, and must
never carry information that exists nowhere else.

### Type — built for prose

Full scale in `Type.kt`. The numbers that matter:

- `bodyLarge` is **16sp/26sp** — a 1.6× line height against Material's 1.5×. That single value is most
  of the difference between "wall of text" and "page".
- `KitsuneTheme.type.message` (16/27) is the in-chat style and the most-read text in the app.
- `displaySmall` (28sp serif) is the `PageTitle` size — the big heading that scrolls away.
- `KitsuneTheme.type.eyebrow` (11sp, +0.1em tracking, uppercase at the call site) is every section
  heading in the app.
- `titleLarge` stays serif (it names things); `titleMedium`/`titleSmall` are sans, because at those
  sizes they are labels doing structural work.

Both families are platform families (`FontFamily.Serif` → Noto Serif, `FontFamily.SansSerif` →
Roboto). No bundled font asset, no APK weight, no licensing question.

### Spacing — one page margin, everywhere

4dp-based. `hair 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 · xxxl 48`, plus:

- **`gutter` = 20dp** — the horizontal page margin, on every screen, no exceptions. Keeping it
  identical is what stops the app feeling like a collection of separate apps.
- `touchTarget` = 44dp, `scrollBottom` = 96dp (so the last row clears the bottom bar and the FAB).

If a layout needs a value that is not on the scale, the layout is usually wrong, not the scale.

### Shape — radius encodes size, not emphasis

`sm 8` (chips, small controls) · `md 14` (cards, rows, fields, dialogs — the signature radius) ·
`lg 20` (hero cards, media) · `sheet` (top-rounded) · `pill` (buttons, tabs) · `bubbleIncoming` /
`bubbleOutgoing` (asymmetric: square on the side the bubble grows from, so who is speaking is readable
from the silhouette alone).

### Motion — two durations

`instant 120` (press, check, chip) · `standard 220` (the default: enter/exit, crossfade) ·
`deliberate 380` (full-screen context change only). v1 ran *everything* at 300ms including a push into
a settings sub-page; at the tenth repetition that is the difference between quick and sluggish.

### Elevation — surface steps, not shadows

On a near-black ground, Material's shadow elevation is invisible. Depth comes from the surface colour
stepping up. Shadows are used only where something genuinely floats over scrolling content (FAB, sheet).

---

## Components

In `component/`. Use these; do not hand-roll a variant.

| file | what |
|---|---|
| `Buttons.kt` | `KitsuneButton` (filled accent — one per screen, at most), `KitsuneSecondaryButton`, `KitsuneQuietButton`, `KitsuneDangerButton`, `KitsuneIconButton`, `KitsuneFab` |
| `Surfaces.kt` | `KitsuneCard`, `KitsuneRow`, `KitsuneSection`, `SectionHeader`, `KitsuneDivider`, `KitsuneKeyValue`, `KitsuneNotice` |
| `Page.kt` | `KitsunePage` (the screen shell), `KitsuneTopBar`, `PageTitle`, `rememberCondensedTitle`, `KitsuneNavBar`, `KitsuneBottomActions` |
| `Fields.kt` | `KitsuneTextField`, `KitsuneSearchField` |
| `States.kt` | `KitsuneEmptyState`, `KitsuneErrorState`, `KitsuneLoading`, `KitsuneSkeleton`, `KitsuneSkeletonList` |
| `Chips.kt` | `KitsuneTag` (read-only metadata), `KitsuneFilterChip`, `KitsuneFilterRow`, `KitsuneSegmented`, `KitsuneCount`, `KitsuneDot` |
| `Avatar.kt` | `KitsuneAvatar`, `KitsuneAvatarPair`, `AvatarSize` |
| `Sheets.kt` | `KitsuneSheet`, `KitsuneConfirmDialog`, `KitsuneActionSheet` + `SheetAction` |
| `Ofuda.kt` | `OfudaPill`, `OfudaCost`, `OfudaPackCard`, `OfudaBalanceBlock`, `OfudaPriceTable`, `InsufficientOfudaPanel` |

### Rules worth stating

**The title is not in the app bar.** `KitsunePage` gives you a thin strip carrying navigation and
actions only; the screen's name is a large serif `PageTitle` inside the scrolling content, and it fades
into the bar once it scrolls off (`rememberCondensedTitle`). A pinned 22sp bar title on every screen is
what makes an app read as a form-filling tool.

**Chips are split by role, and only one of them gets the accent.** `KitsuneTag` is read-only metadata
and is never tappable; `KitsuneFilterChip` is a toggle and is the only one that goes gold;
`KitsuneSegmented` replaces `TabRow`, whose full-width indicator cuts the screen in half exactly where
the content starts. v1 used chips for tags, filters, status, counts *and* navigation, so a screen could
show fifteen pills of four different meanings.

**Sheets for choices, dialogs for consequences.** `KitsuneSheet`/`KitsuneActionSheet` for anything the
user is browsing or picking from; `KitsuneConfirmDialog` **only** when something is about to happen
that cannot casually be undone. v1 used `AlertDialog` for confirmations, pickers, paywalls, the welcome
carousel, the daily bonus *and* error details — so a dialog could mean "here are 3 free Ofudas" or
"this will permanently delete your story", with identical framing.

**Empty states never dead-end.** They name the thing that is missing, say one sentence about why you
would want one, and carry the action that creates it.

**The avatar fallback is not a placeholder.** Most personas have no portrait until the user pays to
generate one, so the no-image case is the *common* case: initials on a tint derived from the name,
stable and distinguishable in a list. v1 showed a grey Material `Person` glyph in a grey circle, which
made a fresh library look broken.

**Money has three surfaces and no fourth.** `OfudaPill` (the balance, Profil tab only), `OfudaCost`
(the price, next to the action that spends it), `OfudaPackCard` (the store). There is intentionally no
upsell or "running low" component — see the Monetisation section of `CLAUDE.md`.

---

## Migration status

The token layer is global, so **every** screen already picks up the new palette, type scale and radii —
nothing looks broken. What differs is how much *layout* work each screen has had:

**Rebuilt on the component library:** the shell and all five tabs, the store and the price list, the
chat's chrome (top bar, mode pill, composer, tools sheet) and its message bubbles, and Settings' page
shell and section cards.

**Themed but not yet re-laid-out:** persona creation and detail, universe creation and detail, the
marketplace listing detail, the memory/timeline/branch-tree/novel screens, onboarding, the lock screen,
and the image-generation screens. These still build their own `Scaffold`/`TopAppBar` and their own
padding. Converting one is mechanical — swap `Scaffold`+`TopAppBar` for `KitsunePage`+`PageTitle`, swap
`Card` for `KitsuneCard`, swap literal `dp` for `KitsuneTheme.spacing`, swap `MaterialTheme.colorScheme`
for `KitsuneTheme.colors` — and Settings is the worked example: its entire look changed by rewriting
one helper (`SettingsSectionCard`) plus the page shell.
