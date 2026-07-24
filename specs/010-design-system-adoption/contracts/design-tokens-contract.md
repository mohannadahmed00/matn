# Contract: Design Tokens

**Feature**: Phase 10 — Design System Adoption | **Date**: 2026-07-24

The token surface every screen composable is allowed to read color/type/shape/spacing from.
Values are the canonical set extracted from Stitch, recorded in `docs/DESIGN-SOURCE.md`.

## 1. Color

Exposed via `MaterialTheme.colorScheme` (Compose M3's own contract) after `Color.kt`'s
`ColorScheme` values are replaced with the M3 role set in `docs/DESIGN-SOURCE.md` § Design
tokens. No screen constructs a `Color(0x...)` literal directly — every color reference is
`MaterialTheme.colorScheme.<role>`.

**Guarantee**: a code-review grep for `Color(0x` outside `theme/Color.kt` MUST return zero matches
in any file touched by this phase (Constitution VIII).

## 2. Typography

Exposed via `MaterialTheme.typography`, extended in `Type.kt` with three families:

| Compose `TextStyle` role | Font | Used for |
|---|---|---|
| `displayLarge`/`headlineLarge`-derived Arabic styles | Amiri | Verse text (`body-ar`/`display-ar`) |
| `headlineMedium`/`bodyLarge` | Source Serif 4 | Headings, Latin/mixed UI copy |
| `labelSmall`/`labelMedium` | Plus Jakarta Sans | Buttons, chips, nav labels |

**Guarantee**: no screen sets `fontSize = <literal>.sp` or `fontFamily = FontFamily(...)` inline;
every text style comes from `MaterialTheme.typography.*` or the existing `ReadingFontSize` scale
in `FontScale.kt` (which itself must resolve through the same type roles, not raw `.sp`).

## 3. Shape

New `theme/Shape.kt`:

```kotlin
object MatnShapes {
    val lg = RoundedCornerShape(8.dp)    // Stitch "lg": 0.5rem
    val xl = RoundedCornerShape(12.dp)   // Stitch "xl": 0.75rem
    val full = RoundedCornerShape(percent = 50) // Stitch "full": 9999px
}
```

**Guarantee**: no screen calls `RoundedCornerShape(<literal>.dp)` directly — every corner radius
is `MatnShapes.lg/xl/full`.

## 4. Spacing

New `theme/Spacing.kt`:

```kotlin
object MatnSpacing {
    val unit = 8.dp
    val gutter = 24.dp
    val marginMobile = 20.dp
    val marginDesktop = 64.dp
}
```

**Guarantee**: `.padding(<literal>.dp)`/`.size(<literal>.dp)` calls in touched screens use
`MatnSpacing.*` (or a small multiple of `MatnSpacing.unit`, e.g. `MatnSpacing.unit * 2`) instead
of a bare literal. Icon sizes tied to platform icon guidelines (e.g. a 24dp `Icon`) are exempt —
this contract governs *layout* spacing/radii, not icon intrinsic size.
