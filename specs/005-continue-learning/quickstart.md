# Quickstart: Validating Continue Learning & State Persistence

**Feature**: Phase 4 — Continue Learning & State Persistence | **Date**: 2026-07-24

How to prove this phase works. §A is automated and gates the PR. §B needs a real device or
simulator, because process death and OS eviction cannot be faked in `commonTest`.

Contracts referenced here live in
[contracts/session-persistence.md](./contracts/session-persistence.md); types in
[data-model.md](./data-model.md).

---

## Prerequisites

- The repo builds on `main`/`develop` as it stands (Phases 0–3 merged).
- At least one seeded matn with per-verse audio — the Phase 0 seed content.
- No network required at any point; airplane mode is a valid way to run all of this.

---

## A. Automated — `commonTest`

```bash
./gradlew :shared:allTests
```

Or the host-only JVM subset while iterating:

```bash
./gradlew :shared:testDebugUnitTest
```

### A1. Resolution rules — pure table tests

`ResumeTargetResolverTest` covers R1–R9 with **no fakes, no database, no coroutines**. This is the
correctness core of the phase and mirrors Phase 3's `RepetitionPlannerTest` treatment.

Expect coverage of: verse present · verse deleted with a predecessor · first verse deleted
(falls forward) · matn emptied · loop endpoint deleted · resolved verse outside its own loop ·
`Unlimited` round trip · null session.

### A2. Write policy

`SessionStateRecorderTest` drives a `MutableStateFlow<PlaybackState>` against a fake repository,
using the coroutines virtual-time scheduler to advance the throttle — no controller, no engine, no
database, no clock.

Assert W1–W7, and specifically that **a pause, a speed change, and a position tick alone produce
zero writes** (W3). This is the guard against the throttling regressing into per-frame I/O.

### A3. Round-trip persistence

`SessionStateRepositoryTest` runs against the in-memory SQLDelight driver
(`newTestDatabase()`), asserting S1–S5 and P1–P5.

The two that matter most:

- **`Unlimited` survives** as `Unlimited` — not `0`, not null, not `1` (S4, FR-007).
- **Dismiss clears the pointer and nothing else** — every `matn_session` row still readable
  afterwards (P2, FR-017a). This is the test that stops a future refactor turning dismissal into
  data loss.

### A4. Migration

`MigrationTest` creates a database at **version 1**, runs `Schema.migrate(1, 2)`, and asserts
`matn_session` is queryable afterwards. Without this, the migration path added in research D3 is
unverified — and the failure mode it guards against only appears on upgrade, never on the fresh
installs that development normally exercises.

### A5. Zero regression

The Phase 2 and Phase 3 suites must pass **unchanged**. Do not edit an existing assertion to make
this phase pass; a failure there is a real behavior change, not a test that needs updating.

---

## B. Manual — device / simulator

Automated tests cannot kill the process, and the phase's central promise is precisely that state
survives that.

### B1. The headline loop *(FR-018 → FR-022, SC-001, SC-006)*

1. Open a matn, play into verse ~5, let it run a few seconds in.
2. **Force-close from the app switcher** — not a back-out, not a clean exit.
3. Reopen.

**Expect**: a Continue Learning entry on Home naming that matn and verse. One tap lands on the
reading screen at that verse, and **audio resumes from where it stopped, mid-verse** — not from the
verse's start, and with no second tap.

### B2. The drill resumes, not just the verse *(FR-019, FR-020, SC-002)*

Set a distinctive drill — verse repeat 7, an A–B loop over verses 12–18 — then force-close and
resume.

**Expect**: counters still 7, loop still 12–18 and visually marked, mode indicator reading A–B Loop.
Landing on the right verse with default counters is a **failure**, not a partial pass — it is the
bookmark-instead-of-drill outcome this phase exists to prevent.

### B3. Browsing does not displace progress *(FR-002a, SC-009b)*

Listen in matn A. Return Home. Open matn B, scroll it, **do not press play**. Back out.

**Expect**: Continue Learning still points at **matn A**.

### B4. Configured but never played *(FR-002b)*

Open matn C, set verse repeat to 5, never press play. Force-close. Reopen, open matn C directly.

**Expect**: verse repeat is still 5, **and** Continue Learning does *not* point at matn C.
This pair is the one that catches over-gating persistence on playback.

### B5. Dismiss is non-destructive *(FR-017a, SC-009a)*

Dismiss the entry, then open that matn directly from the library.

**Expect**: its verse, position, and settings all restore. Dismiss hides the shortcut; it must not
erase anything.

### B6. Resume during a call *(FR-022a)*

Start a call (or have another app hold audio focus), then tap Continue Learning.

**Expect**: the session restores correctly at the right verse and settings, but **paused** — never
playing over the other app, never a silent failure to resume.

### B7. Persistence never disturbs playback *(FR-011, SC-008)*

Run an unlimited (∞) drill for ~10 minutes.

**Expect**: no audible stutter, gap, or hitch attributable to writes; verse transitions stay
gapless. Confirm write frequency is throttled rather than per-tick.

### B8. Upgrade in place *(research D3)*

Install the **pre-Phase-4** build, use it, then install this build **over it** without uninstalling.

**Expect**: the app launches and Continue Learning works. A crash here means the migration is
wrong — and it is invisible to every fresh-install test.

---

## Done when

- [ ] `./gradlew :shared:allTests` green, including A4 migration and A5 zero-regression
- [ ] B1–B8 pass on a physical Android device
- [ ] B1, B2, B6 pass on an iOS simulator
- [ ] Airplane mode throughout — zero network requests (SC-010)
