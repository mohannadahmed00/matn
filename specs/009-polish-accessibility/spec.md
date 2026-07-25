# Feature Specification: Polish & Accessibility

**Feature Branch**: `009-polish-accessibility`

**Created**: 2026-07-25

**Status**: Draft

**Input**: User description: "read @docs/ROADMAP.md and create a specification for the Phase 9 — Polish & Accessibility"

## Clarifications

### Session 2026-07-25

- Q: Does this phase build scheduled daily-goal reminder notifications, or only the permission rationale and request flow? → A: The permission flow only. Phase 7 deferred both reminders and the permission here, but the roadmap's Phase 9 entry lists only the onboarding and permissions flow. This phase relocates the app's existing launch-time notification request behind an explained, in-context rationale; scheduled daily reminders are a feature in their own right and belong to a later phase, not to a polish phase.
- Q: Is the accessibility scope contrast compliance only, or the full set — contrast, screen-reader support, touch targets, and system font scaling? → A: The full set. Contrast alone leaves the app unusable with a screen reader, and retrofitting labels later means touching every screen a second time. Contrast, screen-reader labelling and ordering, minimum touch targets, and system font-scale support are all in scope for this phase.
- Q: Do tablets get responsive refinement of the existing single-pane screens, or a genuine two-pane layout with the library and reader side by side? → A: Responsive refinement. Column counts, bounded reading width, and margins follow the available window width, with the same screens and the same navigation model. A two-pane large-screen layout changes navigation state, back behaviour, and player placement — a product change to be scheduled separately, not folded into this phase.
- Q: Where does the dark palette come from — authored in Stitch, or derived in code from the light token set? → A: Derived in code from the existing light token set following Material 3 tonal-palette guidance, with the derivation documented in this phase's design notes and `docs/DESIGN-SOURCE.md` open issue #5 closed out. The design source itself already pre-authorizes this ("dark tokens will need to be derived rather than imported"), and Principle VIII's blocking-failure language targets inventing *layout*, not deriving colour values — the same precedent Phase 8 set when it composed a Settings screen that had no capture.
- Q: When is the notification permission actually requested — during onboarding, or later in context? → A: At first playback. Onboarding is purely explanatory and requests no permission at all; the in-app rationale and the system prompt appear the first time the student starts playback, which is when the media-playback notification's purpose is evident. This satisfies FR-021's "at a point where its purpose is evident", makes FR-018's "skipping disables nothing" trivially true, and narrows `docs/PRODUCT-SPEC.md`'s description of onboarding as the surface that requests permissions.
- Q: Is a splash / launch screen in scope, given `docs/DESIGN-SOURCE.md` maps one to this phase? → A: Yes — a static, theme-aware launch surface using each platform's own splash mechanism, styled from the registered *Splash Screen* design (`6aba0b42…`). No logo animation. The launch window must be touched regardless: it currently hardcodes a light theme, which would flash white on a dark-mode cold launch and violate FR-005/SC-003.
- Q: What screen-size and font-scale envelope must layouts survive (FR-014, SC-007)? → A: A minimum window width of 320dp, with no clipping, overlap, or unreachable content guaranteed up to a 200% system font scale on both platforms. Above 200% — iOS's accessibility Dynamic Type sizes — content MUST remain scrollable and reachable but the no-clipping guarantee does not extend there, and the app MUST NOT clamp the student's chosen scale. A hard guarantee at iOS AX5 (~310%) on a 320dp screen is not achievable for Arabic verse text without gutting the reading layout. — **Superseded during planning (research D4)**: this answer assumed Compose sees iOS's native ~310% figure. It does not. Compose Multiplatform 1.11.1 maps `UIContentSizeCategory` onto a `fontScale` that tops out at **1.8** even at AX5, and Android's maximum is 2.0, so nothing above 200% is reachable and the carve-out described a state that cannot occur. FR-014 and SC-007 were tightened to a hard guarantee across the whole range — stricter than this answer, at no extra cost.
- Q: How are the specification's 100% accessibility claims verified? → A: Automate everything automatable. Contrast ratios are computed and asserted in `commonTest` over every token pairing in both themes — pure arithmetic on values that live in `commonMain`, so it fits Principle V and becomes a permanent regression guard against a later token change silently breaking AA. A label and touch-target inventory check is likewise automated. A manual device walkthrough is reserved for the screen-reader journeys, which no test can stand in for.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Study at night in a dark theme (Priority: P1)

A student memorizing after ʿIshāʾ, in a dim room or in bed, opens the app and finds it already dark
because their device is in dark mode — not a wall of white that forces them to look away. Every
screen they touch — library, matn details, the reading carousel, the player bar, search, notes,
goals, settings, dialogs and sheets — is dark, legible, and calm, with the Arabic text still the
clearest thing on the screen. If they prefer one theme regardless of the time of day, they can pin
the app to light or dark from Settings, and that choice sticks.

**Why this priority**: Dark mode is the single most visible item in this phase, it is contractual
under the constitution's Experience Fidelity principle, and it is the one piece of Phase 9 that a
student notices within a second of opening the app. It is also the largest correctness surface: a
theme is only "done" when *every* screen and state honours it, so it must be established before the
phase's other work layers on top. It stands alone — shipping only this slice is already a real
improvement.

**Independent Test**: Can be fully tested by setting the device to dark mode, walking every screen
and every state of the app (loaded, empty, error, playing, installing, confirming a removal),
confirming no light-theme surface, unreadable text, or invisible icon appears; then pinning light
and dark explicitly from Settings, restarting the app, and confirming the pinned choice survives —
with no onboarding, tablet, or motion work involved.

**Acceptance Scenarios**:

1. **Given** a device set to dark mode, **When** the student launches the app, **Then** every screen
   renders in the dark theme from the first frame, with no flash of a light surface during launch.
2. **Given** the app running in the dark theme, **When** the student visits every screen and opens
   every dialog, bottom sheet, and menu, **Then** none of them renders light-theme colours, and no
   text, icon, divider, or control becomes invisible or illegible against its background.
3. **Given** the student is reading and playing a matn in the dark theme, **When** they look at the
   verse carousel, **Then** the active verse is still the clearest, highest-emphasis element on the
   screen and the highlight remains distinguishable from the neighbouring verses.
4. **Given** the app is open, **When** the device switches between light and dark (system schedule,
   sunset, or manual toggle), **Then** the app follows immediately without restarting, without
   losing screen state, and without interrupting playback or an in-progress installation.
5. **Given** the Settings screen, **When** the student chooses a theme explicitly (follow the
   device, always light, or always dark), **Then** the app applies it immediately and keeps it
   across app restarts and device restarts.
6. **Given** the student has pinned a theme, **When** the device's own theme later changes, **Then**
   the app keeps the pinned theme and does not follow the device.
7. **Given** either theme, **When** the student views any screen, **Then** the app's visual identity
   — the same tokens for colour, type, spacing, and shape — is recognisably the same app, not a
   second, separately styled variant.

---

### User Story 2 - Use the app with accessibility settings turned on (Priority: P2)

A student with low vision — or one simply using their device at a large system font size, or
navigating with a screen reader — can use Matn end to end. Every control announces what it is and
what it does, including the icon-only playback, bookmark, note, and install controls. Text and
meaningful interface elements stay readable against their backgrounds in both themes. Turning the
system font size up does not push Arabic text off the screen or leave labels overlapping each other.
Every tappable element is big enough to hit reliably.

**Why this priority**: Accessibility is contractual in the constitution, and it is the difference
between the app being usable and unusable for a real group of students. It ranks below dark mode
only because contrast must be verified against *both* finished themes, so the theme work has to
exist first.

**Independent Test**: Can be fully tested by measuring the contrast of every text and interface
colour pairing in both themes against the target level, navigating the whole app with the platform
screen reader to confirm no unlabeled control is reachable, setting the system font size to its
largest value on the smallest supported phone and confirming no clipping or overlap, and checking
every interactive element against the platform's minimum touch-target size.

**Acceptance Scenarios**:

1. **Given** either theme, **When** any text is displayed, **Then** its contrast against its
   background meets the accessibility target for its size.
2. **Given** either theme, **When** a non-text element carries meaning (icons, progress indicators,
   the active-verse highlight, availability badges, control boundaries), **Then** its contrast
   against its background meets the accessibility target for non-text elements.
3. **Given** a screen reader is active, **When** the student moves through any screen, **Then**
   every interactive control announces a meaningful name and its current state (for example
   play/pause, bookmarked/not bookmarked, memorized/not memorized, installed/installing/not
   installed), and purely decorative imagery is skipped rather than announced.
4. **Given** a screen reader is active, **When** the student plays a matn, **Then** they can start
   and stop playback, move between verses, change speed, and reach the repetition controls without
   encountering an unlabeled or unreachable control.
5. **Given** the system font scale is set to its maximum (200%) on a 320dp-wide window, **When** the
   student opens any screen, **Then** no text is clipped, truncated unintentionally, or overlapping,
   and every control remains reachable and operable.
6. **Given** any screen, **When** the student taps any interactive element, **Then** its touch
   target meets the platform's minimum size, even where the drawn icon is smaller.
7. **Given** the student has set an Arabic reading font size in the app, **When** the system font
   size also changes, **Then** the two combine predictably and the verse text remains legible at
   every combination rather than becoming unusably small or overflowing.
8. **Given** a control that conveys status by colour (availability badges, progress, memorized
   state, goal completion), **When** the student cannot distinguish those colours, **Then** the
   status is still conveyed by text, shape, or icon and not by colour alone.

---

### User Story 3 - Understand the app on first launch and grant only what is needed (Priority: P3)

A student opening Matn for the first time gets a brief, calm introduction: what the app is for, and
— crucially — how content works, that only the starter matn is on the device and other متون are
installed on demand and then work offline forever. When a permission is actually needed, the app
explains why in plain language before the system asks. The student can skip the whole thing. It
never appears again on later launches, and they can read it again from Settings if they want to.

**Why this priority**: First launch is the only chance to explain the download model before a
student wonders why a matn will not play, and requesting permissions cold — as the app does today —
is exactly the pattern that gets them denied. It is independent of the theme and layout work, but
ranks below them because it affects one session per student rather than every session.

**Independent Test**: Can be fully tested by installing the app fresh, confirming the onboarding
appears once and explains the offline/download model, confirming it completes without requesting any
permission, then starting playback for the first time to confirm the notification request is preceded
by an in-app rationale, denying the permission and confirming the app remains fully usable, then
restarting to confirm onboarding does not reappear — and finally re-opening it from Settings.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the student launches the app for the first time, **Then** a
   short onboarding flow appears explaining what the app does and how content is installed and used
   offline.
2. **Given** the onboarding is showing, **When** the student chooses to skip it, **Then** they land
   in the library immediately with no feature disabled and no permission requested.
3. **Given** the student has seen or skipped onboarding, **When** they launch the app again, **Then**
   onboarding does not appear.
4. **Given** the student wants to revisit the explanation, **When** they open Settings, **Then** they
   can view the onboarding content again.
5. **Given** the student has finished or skipped onboarding, **When** they reach the library,
   **Then** no permission has been requested at any point during the flow.
6. **Given** the student starts playback for the first time, **When** the app needs the notification
   permission, **Then** the student first sees a plain-language explanation of what it is for and
   what happens if they decline, and the system prompt appears only after they choose to continue.
7. **Given** the student declines a permission, **When** they continue using the app, **Then** every
   feature that does not depend on that permission works normally, the app does not re-prompt on
   every launch, and Settings shows the permission's current state with a way to change it.
8. **Given** a fresh install with no network connection, **When** the student completes onboarding,
   **Then** the flow completes normally and the student lands on a working library with the starter
   matn playable.
9. **Given** the app requests permissions, **When** the full set is reviewed, **Then** it asks only
   for permissions the app actually uses, and asks for each one at a point where its purpose is
   evident.
10. **Given** onboarding is interrupted, **When** the app is killed mid-flow and relaunched, **Then**
   the student is not left stuck — the flow either resumes or restarts cleanly and can always be
   completed or skipped.

---

### User Story 4 - Study on a tablet without a stretched phone layout (Priority: P4)

A student using a tablet — the natural device for reading long متون — sees a layout that uses the
screen. The library shows more متون per row instead of a few enormous cards. Reading text is held to
a comfortable line length in the middle of the screen rather than stretched edge to edge. Rotating
the device, or running the app side by side with another, re-flows the layout without losing their
place or interrupting playback.

**Why this priority**: The product spec calls for tablet-friendly adaptive layouts, and a stretched
phone layout on a tablet looks unfinished — but every student can already study successfully on a
phone, which is the primary device. This is a quality improvement on a secondary form factor.

**Independent Test**: Can be fully tested by running the app on a tablet-sized screen in both
orientations and in a split-screen configuration, confirming the library grid's column count and
the content width adapt at each size, that no screen shows a single stretched column or
uncomfortably long lines of text, and that rotating during playback preserves the active verse,
scroll position, and playback state.

**Acceptance Scenarios**:

1. **Given** a large screen, **When** the student opens the library, **Then** the grid shows more
   items per row than on a phone and the cards keep a sensible size rather than scaling up.
2. **Given** a large screen, **When** the student reads a matn, **Then** the verse text is held to a
   comfortable reading width and centred rather than spanning the full screen width.
3. **Given** any screen on a large display, **When** the student looks at it, **Then** no region is a
   single narrow column of content floating in empty space, and no control bar is stretched so wide
   that its controls are far apart and hard to reach.
4. **Given** the app is open, **When** the student rotates the device or resizes it into split-screen
   or a floating window, **Then** the layout re-flows to the new size, and screen state — active
   verse, scroll position, playback, in-progress input — is preserved.
5. **Given** a large screen at a phone-like width (a narrow split-screen pane), **When** the layout
   is chosen, **Then** it follows the available width rather than the device's classification, so it
   remains usable at any pane size.
6. **Given** either orientation on any supported size, **When** the student uses the app, **Then**
   the right-to-left layout, dark theme, and accessibility guarantees of the previous stories all
   still hold.

---

### User Story 5 - Move through the app without jarring jumps (Priority: P5)

Screens, sheets, and state changes ease in and out instead of snapping. Moving between verses,
opening the repetition sheet, switching tabs, revealing an install progress bar, or completing a
goal ring feels continuous rather than abrupt. A student who has asked their device to reduce motion
gets the same app with the movement taken out — nothing hidden, nothing broken.

**Why this priority**: Motion is the last layer of polish. It makes the app feel finished but
changes nothing about what a student can accomplish, and it must sit on top of the finished themes
and layouts rather than be redone after them.

**Independent Test**: Can be fully tested by navigating every transition in the app and confirming
each one is animated smoothly with no dropped frames or visual tearing, then enabling the device's
reduce-motion setting and confirming non-essential animation stops while every screen, control, and
piece of information remains reachable and readable.

**Acceptance Scenarios**:

1. **Given** the student navigates between screens or switches bottom-navigation tabs, **When** the
   transition plays, **Then** it is smooth and consistent in direction and duration with every other
   transition in the app, and correct for a right-to-left layout.
2. **Given** a sheet, dialog, or prompt appears or is dismissed, **When** it animates, **Then** it
   enters and exits with a consistent, unhurried motion rather than appearing instantly.
3. **Given** a value on screen changes (playback progress, install progress, goal ring, progress
   bars, availability badge), **When** it updates, **Then** it transitions rather than jumping,
   without ever misrepresenting the underlying value.
4. **Given** the reading carousel, **When** playback advances from one verse to the next, **Then**
   the movement is smooth and does not interrupt, delay, or add a gap to the audio.
5. **Given** the device's reduce-motion setting is on, **When** the student uses the app, **Then**
   non-essential animation is suppressed or replaced with a simple fade, and no content, control, or
   state becomes unreachable as a result.
6. **Given** any transition, **When** it runs on a mid-range device, **Then** it does not drop frames
   or delay the student's next interaction.

---

### Edge Cases

- **System theme changes mid-playback or mid-install**: The theme must switch live without
  restarting the screen, interrupting audio, cancelling an installation, or losing unsaved input.
- **App launched cold in dark mode**: No light-coloured launch surface may flash before the app's own
  theme takes over.
- **Pinned theme versus device theme**: An explicit choice always wins over the device setting until
  the student returns the app to "follow the device"; the pinned value survives app and device
  restarts.
- **Both font controls at their extremes**: The app's own Arabic reading-size setting and the
  device's system font scale can be at maximum simultaneously on a 320dp-wide window — layout must
  survive that combination at the maximum 200% system scale, and the minimum combination of both
  (`SMALL` at a 0.8 scale) must not become illegible.
- **Screen reader plus right-to-left**: Reading order, focus order, and swipe navigation must follow
  the visual right-to-left order, not a left-to-right assumption.
- **Screen reader during playback**: Announcements must not fight the audio; state changes must be
  announced without spamming the student on every progress tick.
- **Permission permanently denied**: Where the platform stops showing its own prompt, the app must
  explain the state and point to the device settings rather than silently doing nothing.
- **Permission granted, then revoked outside the app**: The app must detect the change on return and
  keep working, with the dependent feature degrading gracefully.
- **Onboarding on a device with no network**: Onboarding is content the app already has; it must
  never wait on, or fail because of, connectivity.
- **Onboarding after data is cleared**: If the student clears app data, onboarding legitimately
  appears again; it must not resurface for any other reason.
- **Very narrow or very wide windows**: Split-screen panes, floating windows, and unfolded foldables
  produce widths the phone and tablet layouts were not designed for — layout must follow the
  available width continuously rather than switching only on device type.
- **Rotation with a dialog, sheet, or text input open**: The open surface and any typed text must
  survive the re-layout.
- **Reduce-motion with essential motion**: Movement that carries meaning (playback progress, the
  active-verse indicator) must remain visible in some form when animation is suppressed.
- **Contrast in the highlighted/active states**: The active verse, selected tab, pressed control, and
  disabled control must all meet contrast requirements in both themes — not just the resting state.
- **Long Arabic titles and author names**: In both themes and at every font scale, they must wrap or
  truncate legibly rather than breaking the layout.

## Requirements *(mandatory)*

### Functional Requirements

#### Theming & dark mode

- **FR-001**: The app MUST provide a complete dark theme covering every screen, dialog, bottom
  sheet, menu, and transient surface, with no screen or state left rendering light-theme colours.
- **FR-002**: The dark theme MUST be defined as a second set of values for the same shared design
  tokens used by the light theme — the same colour roles, typography, spacing, and shape scale — so
  that no screen defines theme-specific values of its own.
- **FR-003**: The app MUST follow the device's light/dark setting by default and MUST update live
  when that setting changes, without restarting, losing screen state, interrupting playback, or
  cancelling an in-progress installation.
- **FR-004**: Students MUST be able to override the theme from Settings with an explicit choice of
  follow-the-device, always light, or always dark.
- **FR-005**: The theme choice MUST persist across app restarts and device restarts, and MUST be
  applied before the first frame is drawn so no incorrect theme is visible at launch. The platform
  launch surface MUST itself be theme-aware — styled from the registered *Splash Screen* design,
  static with no logo animation — so a cold launch never shows a light surface while the dark theme
  is active, or the reverse. Any hard-coded light launch theme MUST be replaced.
- **FR-006**: Every existing screen and shared component MUST derive its colours from the shared
  token set; no screen may hard-code a colour that fails to respond to the active theme.

#### Accessibility

- **FR-007**: All text MUST meet a contrast ratio of at least 4.5:1 against its background, and large
  text at least 3:1, in both the light and the dark theme, in every state including active,
  selected, pressed, and disabled. This MUST be enforced by an automated, repeatable check over the
  full token set in both themes — not by one-time manual inspection — so a later token change cannot
  silently break compliance.
- **FR-008**: Non-text elements that convey meaning — icons, controls, progress indicators, the
  active-verse highlight, availability badges, and control boundaries — MUST meet a contrast ratio
  of at least 3:1 against adjacent colours in both themes.
- **FR-009**: Every interactive control MUST expose a meaningful, localised accessibility label and
  its current state to the platform screen reader, including all icon-only controls (playback,
  verse navigation, speed, repetition, bookmark, note, memorized, install, cancel, remove, and
  navigation).
- **FR-010**: Purely decorative imagery MUST be hidden from the screen reader rather than announced.
- **FR-011**: A screen reader user MUST be able to complete every primary task — browse the library,
  open a matn, play and control playback, navigate verses, search, bookmark, take a note, mark a
  verse memorized, set a goal, and install or remove content — without encountering an unlabeled or
  unreachable control.
- **FR-012**: Screen-reader focus and navigation order MUST follow the visual right-to-left reading
  order on every screen.
- **FR-013**: Every interactive element MUST present a touch target at least the platform's
  recommended minimum size, regardless of the size of the icon or text drawn inside it.
- **FR-014**: The app MUST respect the device's system font-scale setting for interface text and MUST
  NOT clamp or override the student's chosen scale. At a 320dp window width — the minimum supported —
  the app MUST remain free of clipped, overlapping, or unreachable content across the **entire**
  reachable font-scale range, up to and including 200%. No platform can present a larger scale to the
  app, so no degraded mode above 200% is specified or permitted (see Assumptions: *Font-scale range*).
- **FR-015**: The app's own Arabic reading font-size setting MUST continue to work and MUST combine
  predictably with the system font scale, keeping verse text legible at every combination.
- **FR-016**: Status MUST never be conveyed by colour alone; every colour-coded state MUST also carry
  a text, icon, or shape distinction.

#### First-launch onboarding & permissions

- **FR-017**: On first launch after installation, the app MUST present a brief onboarding flow that
  explains what the app is for and how content works — that a starter matn is included, other متون
  are installed on demand, and installed content then works fully offline.
- **FR-018**: Onboarding MUST be skippable at any point, and skipping MUST NOT disable any feature or
  leave any state unset.
- **FR-019**: Onboarding MUST be shown only once per installation, MUST NOT reappear on subsequent
  launches, and MUST be re-openable on demand from Settings.
- **FR-020**: The app MUST show an in-app, plain-language rationale before any system permission
  prompt, stating what the permission is used for and what is lost by declining, and MUST only
  trigger the system prompt after the student chooses to continue.
- **FR-021**: The app MUST request only permissions it actually uses, and MUST NOT request any
  permission at launch. The notification permission MUST be requested at the point of first
  playback — where the media-playback notification it enables becomes evident — and never during
  onboarding, which MUST complete without requesting any permission.
- **FR-022**: Declining a permission MUST leave every unrelated feature fully functional, MUST NOT
  cause a re-prompt on every launch, and MUST NOT block the student from reaching the library.
- **FR-023**: Settings MUST show the current state of each permission the app uses and MUST offer a
  way to change it, including directing the student to the device settings when the platform will no
  longer show its own prompt.
- **FR-024**: Onboarding MUST work with no network connection and MUST NOT depend on any content the
  app does not already have.
- **FR-025**: If onboarding is interrupted, the app MUST recover on next launch to a state where the
  flow can be completed or skipped, and MUST NOT leave the student stuck before the library.

#### Adaptive layout

- **FR-026**: Screen layouts MUST adapt to the available window width rather than to a device
  category, so a narrow split-screen pane on a tablet gets the compact layout and a wide window gets
  the expanded one.
- **FR-027**: On larger widths the library grid MUST show more items per row rather than scaling
  individual cards up.
- **FR-028**: Reading and text-heavy content MUST be constrained to a comfortable maximum line
  length and positioned deliberately within wider windows rather than stretched edge to edge.
- **FR-029**: Control bars, sheets, and dialogs MUST remain reachable on large screens instead of
  stretching to the full window width. Each MUST be bounded to a stated maximum width — defined once
  in the shared token layer, not per surface — and centred within wider windows.
- **FR-030**: Rotation, resizing, and multi-window changes MUST preserve screen state — active verse,
  scroll position, playback state, dialog visibility, and in-progress text input.
- **FR-031** *(cross-check, not a new obligation)*: The adaptive layouts introduced by FR-026 – FR-030
  MUST NOT weaken any guarantee stated elsewhere in this specification — right-to-left layout, the
  active theme (FR-001 – FR-006), and every accessibility guarantee (FR-007 – FR-016) hold at every
  window width. This exists so those guarantees are re-verified at expanded widths rather than only
  at phone width; it adds no requirement of its own.

#### Motion & transitions

- **FR-032**: Navigation between screens, bottom-navigation tab changes, and the appearance and
  dismissal of dialogs, sheets, and prompts MUST be animated with a consistent motion vocabulary —
  shared durations, easing, and direction — defined once in the shared token layer rather than per
  screen.
- **FR-033**: Directional motion MUST be correct for a right-to-left layout.
- **FR-034**: Value changes shown on screen — playback progress, install progress, progress bars, the
  daily goal ring, availability badges, and the active-verse change in the reading carousel — MUST
  transition rather than jump, and MUST never display a value the underlying state does not hold.
- **FR-035**: Animation MUST NOT delay, gate, or interrupt audio playback; verse-to-verse transitions
  MUST remain gapless.
- **FR-036**: When the device's reduce-motion accessibility setting is enabled, the app MUST suppress
  or simplify non-essential animation while keeping all content, controls, and state changes
  perceivable and reachable.
- **FR-037**: Transitions MUST NOT block student input; a student MUST be able to act during or
  immediately after a transition without waiting for it to finish.

### Key Entities

- **Theme Preference**: The student's chosen appearance mode — follow the device, always light, or
  always dark. One value per device, persisted, observed live by the whole app.
- **Theme Token Set**: The light and dark values of the app's shared design tokens (colour roles,
  typography, spacing, shape) plus the shared motion values. The single place a theme is defined;
  screens consume it and never restate it.
- **Onboarding State**: Whether the first-launch flow has been completed or skipped on this
  installation, and how far it progressed if interrupted. Persisted; reset only when app data is
  cleared.
- **Permission State**: For each permission the app uses, its current status (not yet asked, granted,
  denied, permanently denied) as reported by the platform, used to decide whether to show a
  rationale, a system prompt, or a pointer to device settings.
- **Window Width Class**: The classification of the current window's available width that layouts
  respond to. Derived continuously from the live window size, never from the device type.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the app's screens and states — including every dialog, sheet, empty state, and
  error state — render correctly in the dark theme, with zero surfaces showing light-theme colours.
- **SC-002**: A student switching their device to dark mode sees the app follow within one second,
  with no restart, no lost screen state, and no interruption to playback or an in-progress install.
- **SC-003**: An explicitly chosen theme is still in effect after an app restart in 100% of cases,
  and no incorrect theme is visible at any point during launch.
- **SC-004**: 100% of text and meaningful non-text elements meet their contrast targets (4.5:1 text,
  3:1 large text and non-text) in both themes, across resting, active, selected, and disabled states.
- **SC-005**: 100% of interactive controls expose a meaningful screen-reader label and state; a
  screen-reader user completes every primary task in the app without meeting an unlabeled or
  unreachable control.
- **SC-006**: 100% of interactive elements meet the platform's minimum touch-target size.
- **SC-007**: At a 320dp window width, zero screens show clipped, overlapping, or unreachable content
  at any system font scale up to and including 200% — which is the whole reachable range.
- **SC-008**: A first-time student reaches the library within 60 seconds of first launch, whether
  they read the onboarding or skip it, and onboarding appears zero times on subsequent launches.
- **SC-009**: 100% of permission prompts are preceded by an in-app rationale, the app requests zero
  permissions it does not use, and zero permissions are requested at launch or during onboarding.
- **SC-010**: With every permission denied, 100% of features that do not depend on those permissions
  remain fully usable, and the student is prompted no more than once per permission per install.
- **SC-011**: On a tablet-width window the library shows at least twice as many items per row as on a
  phone, and no screen displays a single stretched column or text lines beyond a comfortable reading
  measure.
- **SC-012**: Rotation, split-screen entry, and window resizing preserve screen state — active verse,
  scroll position, playback, and typed input — in 100% of cases.
- **SC-013**: On the reference device named in this phase's validation guide, every transition plays
  through without visible stutter or a frozen frame, and a student can act on the next control within
  a tenth of a second of starting an interaction — without waiting for any transition to finish.
- **SC-014**: With reduce-motion enabled, non-essential animation is absent from 100% of the app,
  and zero content, controls, or state changes become unreachable or imperceptible as a result.
- **SC-015**: Verse-to-verse audio remains gapless with all animation enabled — animation adds zero
  measurable delay to playback.

## Assumptions

- **Font-scale range** *(established during planning, research D4)*: 200% is the largest scale either
  platform can present to the app. Android's system font scale tops out at 2.0, and Compose
  Multiplatform maps iOS's `UIContentSizeCategory` onto a `fontScale` capped at 1.8 — even at AX5,
  which iOS itself renders near 310%. FR-014 and SC-007 therefore guarantee the whole reachable range
  rather than carving out a degraded mode above 200%, because no such mode can be reached.
- **Accessibility target level** *(informed default)*: Contrast and related requirements target
  WCAG 2.1 level AA (4.5:1 normal text, 3:1 large text and non-text elements). This is the standard
  the constitution's "accessibility-contrast compliant" wording points at, and the level both
  platforms' own guidelines align with. Level AAA is not a goal for this phase.
- **Dark tokens are derived, not imported** *(clarified 2026-07-25)*: The Stitch design set
  registered in `docs/DESIGN-SOURCE.md` is light-theme only — its open issue #5 explicitly assigns
  dark mode to this phase and states dark tokens "will need to be derived rather than imported". The
  dark palette is therefore derived from the existing light token set following the platform's
  tonal-palette guidance, rather than authored in Stitch first. The derivation — every role, the rule
  that produced it, and its measured contrast — is recorded in this phase's design notes, and
  DESIGN-SOURCE issue #5 is closed out as part of the phase. Principle VIII's blocking-failure
  language targets inventing *layout*, not deriving colour values, so this is a documented use of the
  design source's own pre-authorization rather than a deviation requiring Complexity Tracking.
- **The launch surface and onboarding derive from the captured splash screen** *(clarified
  2026-07-25)*: The design set registers one Phase 9 screen — *Splash Screen*
  (`6aba0b42e95d43e5b6f81928f3e3f7c6`). It backs the static, theme-aware platform launch surface
  (FR-005) and the onboarding flow's opening panel. The remaining onboarding panels, the
  permission-rationale surface, and the Settings appearance section have no captured design and are
  composed from the existing token set, as Phase 8 did for Settings.
- **Only the notification permission is requested** *(informed default, narrowing the product spec)*:
  The product spec mentions "notifications … and storage", but Phase 8 established that content lives
  in the app's own managed storage and needs no storage permission. This phase therefore requests
  notifications only, and the onboarding's storage-related content is *explanatory* — how the
  download model works — not a permission request.
- **The notification permission is moved, not introduced** *(clarified 2026-07-25)*: The app already
  requests notification permission cold at launch for the media-playback notification. This phase
  relocates that request to first playback, behind an explained rationale, rather than adding a new
  permission. Onboarding itself requests nothing.
- **No new content, screens, or data beyond preferences**: This phase changes how existing screens
  look, adapt, announce themselves, and animate. The only new persisted data is the theme preference
  and the onboarding-completed flag. No domain entity, audio behaviour, or repetition/progress rule
  changes.
- **The reading font-size setting stays**: The app's existing Arabic reading-size control (Phase 2)
  is not replaced by system font scaling; the two coexist, and this phase makes the interface text
  respect the system scale as well.
- **Right-to-left is already established**: The app already forces right-to-left layout app-wide.
  This phase verifies that every new surface and every animation direction honours it, rather than
  introducing right-to-left support.
- **Phone is the primary form factor** *(clarified 2026-07-25)*: Tablet support means the existing
  single-pane screens adapt gracefully to larger windows — column counts, bounded reading width, and
  margins driven by available width. A tablet-specific navigation model (a permanent two-pane
  library-plus-reader view) changes navigation state, back behaviour, and player placement, and is
  out of scope for this phase; it should be scheduled separately if wanted.
- **Reminders are not built here** *(clarified 2026-07-25)*: This phase owns the notification
  permission's rationale and request flow, not scheduled daily-goal reminder notifications. Phase 7
  deferred both to this phase, but the reminder feature itself — scheduling, delivery, a reminder-time
  setting, quiet hours — is a feature phase's worth of work and does not belong in a polish phase.
  The permission this phase asks for has an existing, honest purpose: the media-playback notification
  already shipped in Phase 3.
- **Verification is largely non-device** *(clarified 2026-07-25)*: Contrast ratios over every token
  pairing in both themes, and the inventory of accessibility labels and touch-target sizes, are
  asserted by automated tests in the shared test suite — arithmetic and structure over values the app
  already holds, needing no device or emulator, and standing as a permanent regression guard per
  Principle V. Layout at extreme font scales is verified from state-driven previews of the
  stateless screen composables, per the constitution's preview-coverage requirement. A manual device
  walkthrough is reserved for the screen-reader journeys (FR-011, FR-012), which automation cannot
  substitute for.
- **Prerequisites**: Phase 10 supplies the shared token layer and navigation shell this phase extends
  with dark values and motion; Phases 2–8 supply the screens being polished. This phase is
  cross-cutting — it touches every screen already shipped — and should therefore land after the
  feature phases whose screens it must cover.
- **Out of scope**: New features, additional settings beyond appearance and permission state,
  scheduled daily-goal reminder notifications, a two-pane large-screen navigation model,
  localisation into further languages, high-contrast or colour-blind-specific alternate themes
  beyond the colour-alone requirement (FR-016), and per-screen custom illustrations or artwork.
