# Verso UI Design Guidelines

Use this guide as the reference for UI design, refinement, and redesign work in Verso. The user's specific brief and Verso's established design system always take precedence. Treat these as working principles, not as a mandate to redesign unrelated parts of the app.

## Design stance

- Finish the requested surface end to end: visual hierarchy, interaction states, content, accessibility, and verification all matter.
- Choose the right level of ambition. For an existing app surface, refine with precision and restraint; preserve recognizable patterns and make behavior predictable. Make a larger visual departure only when the brief calls for a redesign.
- Make a few coherent decisions instead of accumulating decorative effects. Every visual treatment should clarify hierarchy, state, or function.
- Preserve product meaning, content, and behavior unless the brief explicitly changes them. Keep out-of-scope areas intact.

## Verso design system

### Chrome & Top Bar
- **Borderless progressive blur:** Use `SimpleTopBarBlurBackdrop` across both top-level tabs (`UnifiedTopBar`) and detail, library, album, playlist, and artist pages (`PinnedBackTopBar`, `DetailListTopBar`). Avoid hard bottom edge lines on top bars.
- **Floating pill controls:** Top-level account/menu actions live in a single translucent pill (`playerChipContainer` with subtle `1.dp` `onSurface` 8% border); back buttons on detail and artist pages use a visible circular background so they remain legible over any cover backdrop.
- **Consistent titles:** Detail, album, playlist, and artist pages display the item title next to the back button in `Outfit`.

### Typography
- **Outfit** (`displayLarge`, `headlineMedium`, `titleLarge`, `titleMedium`): display titles, screen headers, section headings, and card titles.
- **Geist** (`bodyLarge`, `bodyMedium`, `labelLarge`, `labelMedium`): body copy, metadata, track counts, durations, and action labels.
- Always use `elovaireScaledSp` or `MaterialTheme.typography` tokens so text scales with `LocalTextScale`.

### Color, Artwork & Surfaces
- **Cover-derived atmosphere:** Artist, album, and playlist pages extract contrast-safe accent colors (`rememberArtworkPaletteAccent`, `rememberArtworkGradient`) for atmospheric header washes and active playback row highlights.
- **Brand blue & category accents:** Preserve Verso's brand-blue accent for primary interactive states and switches, and the 4-tone category palette for section accents.
- **Artwork & card clipping:** Apply `RoundedCornerShape` clipping only to the `ArtworkImage` container—never clip the outer `Column` holding title and artist text below the artwork, so text is never clipped by bottom corners.
- **Single-source state confirmation:** Avoid duplicate status badges/tags when an action button (such as Download / Downloaded) already communicates the state.
- **Symmetrical action bars:** Collection headers (`Shuffle`, `Radio`, centered `Play`, `Download`, `Add to playlist`) should be balanced and fit cleanly on narrow screens.

### Spacing & Layout
- Follow `ElovaireSpacing` (4dp grid, `screenHorizontalPadding`) and `ElovaireRadii` (`artwork`, `card`, `module`, `pill`).
- Use spacing to show grouping: related content belongs closer together than separate groups.

## Before changing a surface

1. Read the request closely and identify what must change, what must remain, and where the UI lives in the user's actual workflow.
2. Inspect the existing screen, neighboring screens, shared components, assets, and design tokens. Treat the running app and current code as the source of truth; do not invent a parallel design language.
3. Select the smallest coherent change that satisfies the brief. Reuse project patterns and platform conventions where they fit.
4. Check the complete surface and its states, not just the first screenshot: default, pressed/focused, selected, disabled, loading, empty, and error states as applicable.

## Visual craft & accessibility

- Keep text readable at its actual shipped size. Target at least 4.5:1 contrast for normal text and 3:1 for large text and meaningful UI graphics, while respecting Verso's established palette.
- Use surfaces, borders, shadows, blur, and gradients only when they help distinguish layers or improve legibility.
- Use the existing Lucide icon family with consistent scale, stroke, and alignment.
- Check touch targets, text scaling, screen-reader labels (`contentDescription`), focus order, and interaction feedback.

## Interaction and motion

- Make controls behave as their appearance promises. Keep actions discoverable, state changes legible, and repeated patterns consistent.
- Animate to explain a change of state, continuity, or spatial relationship—not merely to decorate. Keep motion brief, smooth, and interruptible (150–250 ms for ordinary UI transitions).
- Prefer native Android and Jetpack Compose motion and interaction patterns. Verify gestures alongside nearby gestures and scrolling.

## Verso-specific guardrails

- Do not add card containers, gradients, blur, animation, or accent color by default; use each only when it improves the requested hierarchy or behavior.
- Preserve existing functionality and data behavior during visual work. For shared components, check all affected callers.
- Follow project localization and UI-copy rules in `AGENTS.md` for every new user-facing string, including accessibility descriptions.
- Respect explicit exclusions in the brief, including screens the user asks not to redesign.

## Verification

- Run the narrowest relevant build or test supported by the repository.
- Inspect the rendered result at realistic device sizes and configurations.
- Exercise the requested interactions, edge cases, and neighboring touch/scroll behaviors on an emulator or device when available.
