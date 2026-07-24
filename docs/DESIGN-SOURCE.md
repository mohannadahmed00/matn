# Matn — Design Source of Truth

The canonical visual reference for Matn's UI is the **Stitch** project below. Any model or
developer implementing a screen MUST pull that screen's design from Stitch rather than inventing
a layout — see Principle VIII in [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md).

This file is the **registry** (which screen backs which phase). It deliberately holds no design
content — the designs themselves live in Stitch and are fetched on demand via MCP.

## Project

| Field | Value |
|-------|-------|
| Title | Matn Memorization Platform |
| Project ID | `5201142409061412050` |
| Access | Stitch MCP server (`stitch`), configured in `.mcp.json` and `opencode.json` |

## Access setup

The MCP server is declared in the repo and is secret-free. Each developer authenticates once,
locally, using **either** method:

```bash
# Option A — guided OAuth wizard (bundles its own gcloud; no prior install needed)
npx -y @_davideast/stitch-mcp init

# Option B — API key from stitch.withgoogle.com/settings → API Keys
# Set STITCH_API_KEY in your user/system environment (NEVER commit it)
```

Verify with `npx -y @_davideast/stitch-mcp doctor`, then restart the agent so it picks up the
server.

## Screen registry

Screen IDs are stable; use them with the `get_screen_code` / `get_screen_image` /
`extract_design_context` MCP tools.

### Foundational

| Screen | ID | Notes |
|--------|----|-------|
| Design System | `asset-stub-assets_f38282d0af114c788fa66a0166344c53` | **Read first.** Source for the shared token set (color, type scale, spacing, radii) and the reusable component inventory. |
| Matn Brand Logo | `d3e5ae25b9734ceaa4fdba8c04ed54af` | Brand asset. |

### Phase-mapped screens

| Screen | ID | Phase |
|--------|----|-------|
| Home / Library | `618643f891144557b5a4ddf4bbad0c03` | **1** (library grid); its *Continue Learning* card is **4**; its progress ring is **6** |
| Matn Details (Refined) | `472161bbcdee47adb26d94a1fefcc3a8` | **1** |
| Matn Details | `106e8e5d4cf748898f12a2e554ca07e5` | **1** — earlier iteration, see *Duplicates* below |
| Reading & Playback (Updated) | `ac354abd54c246ed841f63b31a19fbf2` | **2** |
| Reading & Playback | `490d65e3499f4a0ca9d1f6c269eef91a` | **2** — earlier iteration |
| Reading & Playback | `e27cc8b9a4624fbabae6368cb9a98bfe` | **2** — earlier iteration |
| Repetition Setup | `c17c4877123f403292a30c78ae685f6e` | **3** |
| Search Matn | `fa8b63b036c94510ba1be2d90e7a3417` | **5** |
| Bookmarks & Notes | `bc99ab7f8110479086326271d0aa0310` | **5** |
| Progress & Goals | `f059cccd2f634bc9ba2cf4d620e5df80` | **6** |
| Splash Screen | `6aba0b42e95d43e5b6f81928f3e3f7c6` | **8** (first-launch / onboarding) |

## Open issues in the design set

These are unresolved and MUST be settled before the affected phase is implemented.

1. **Duplicates need a canonical pick.** *Matn Details* has 2 variants and *Reading & Playback*
   has 3. The titles suggest `472161bb…` (Refined) and `ac354abd…` (Updated) are the latest, but
   **this has not been visually confirmed** — no one has fetched the designs yet. Confirm before
   building Phase 1 / Phase 2 UI, and delete or clearly retire the superseded screens in Stitch.

2. **`Upload Matn (Timestamp Map)` (`f55899af78974175b345f3c3cd048387`) contradicts a locked
   architectural decision.** The constitution and `PRODUCT-SPEC.md` lock the audio model to *one
   micro-audio file per verse*, and explicitly place a shared/continuous-file-with-timestamps
   model out of scope. This screen designs the rejected model. **Do not implement it.** It should
   be retired in Stitch to stop it being picked up as guidance.

3. **`Upload Matn (Per-Verse)` (`4f1bee3d7518487b986c7c63cb3c07ff`) has no home in the roadmap.**
   Content intake is a production-side concern in `PRODUCT-SPEC.md`, not a v1 app phase. Either
   add a roadmap phase for it or treat it as out of scope for v1.

4. **Phase 4 has no dedicated screen.** *Continue Learning* is a card on Home / Library rather
   than its own design. Phase 4 UI work should derive from the Home screen's card region.

5. **No dark-mode variants exist.** Principle VII makes dark mode contractual and Phase 8 owns it,
   but the Stitch set is light-theme only. Dark tokens will need to be derived rather than
   imported.
