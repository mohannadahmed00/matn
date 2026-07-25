# Data Model: Storage & Downloads

Derived from the spec's Key Entities and the Phase 0 decisions. Everything here lives in
`commonMain`. The guiding split (research D5): **the catalog is persisted, availability is not.**

## 1. Persisted schema

### 1.1 New table — `content_pack`

Additive migration `4.sqm` (schema v4 → v5). One row per matn, seeded with the catalog.

```
content_pack
├── matn_id              TEXT NOT NULL PRIMARY KEY   -- FK → matn(id), 1:1
├── pack_id              TEXT NOT NULL UNIQUE        -- delivery slug, e.g. "matn_ajurrumiyya" (D3)
├── declared_size_bytes  INTEGER NOT NULL            -- always-available offline figure (D4)
└── is_starter           INTEGER NOT NULL DEFAULT 0  -- 1 = bundled install-time, non-removable (D7)
```

**Rules**

- `pack_id` is authored, stable, and never derived from a title. Lowercase `[a-z0-9_]`, must be a
  valid Gradle module name and a valid ODR tag (D3). **One identifier, three names**: `pack_id` *is*
  the Android asset-pack Gradle module name and *is* the iOS On-Demand Resources tag. "Content pack"
  (spec, this document), "asset pack" (Android) and "ODR tag" (iOS) all refer to it.
- `declared_size_bytes` is **measured from the matn's actual audio files** and re-measured whenever
  that audio changes — this is what makes SC-002 (within 5% of actual) hold without a live lookup.
  For a matn with no audio it is `0`, which renders as "nothing to install" (spec edge case) rather
  than a broken install target.
- Exactly one row may have `is_starter = 1`. That row's audio ships in Compose resources; it is
  never fetched, never removed, and is excluded from "remove all" (FR-027, FR-029). Its
  `declared_size_bytes` MUST be a real measured value, not `0` — it is the only figure available for
  the starter's contribution to the storage total (§3.1).
- No availability, progress, or occupied-size column exists here — see §2.
- `ON DELETE CASCADE` from `matn(id)`. Nothing cascades *into* progress, bookmarks, or notes: FR-020
  holds because this table has no relationship to any personal-data table.

### 1.2 Existing tables — unchanged

`matn`, `chapter`, `verse`, `audio_asset`, `app_setting`, `saved_session`, `bookmark`, `note`,
`memorization`, `daily_practice` are all untouched. This is the structural guarantee behind FR-019
and FR-020: removing audio deletes files on disk and touches no row anywhere. `audio_asset` rows
survive removal — they describe what the audio *is* (fileRef, duration), not whether it is present.

## 2. Runtime state (not persisted)

### 2.1 `ContentAvailability`

```
sealed ContentAvailability
├── NotInstalled(failure: DeliveryFailure?)   -- failure non-null ⇒ last attempt failed, retryable
├── Installing(progress: DeliveryProgress)
└── Installed(occupiedBytes: Long)
```

Exactly three states (FR-001). There is no partial state, and a failed or interrupted install
resolves to `NotInstalled` carrying its reason (FR-009). Computed per read from the delivery
platform plus the `content_pack` row (D5).

**`occupiedBytes` — where the number comes from**, in priority order:

1. **Starter matn**: always `Installed(occupiedBytes = declared_size_bytes)`. Its audio lives inside
   the app binary, not in a pack directory, so there is no path for `DeviceStorage.sizeOfDirectory`
   to measure — the measured catalog figure is authoritative and is the only correct answer here.
2. **Installed on-demand matn**: `DeviceStorage.sizeOfDirectory(engine.locate(packId))` — the real
   on-disk size, which supersedes both declared and live figures (D4).
3. **Fallback**: if `locate` returns null for a matn the platform reports as installed (a state that
   should not occur), treat it as `NotInstalled(Evicted)` rather than reporting a zero-byte install.

This is what keeps `StorageUsage.totalUsedBytes` honest against the device's real footprint
(SC-003) even though the starter is never installed or removed.

### 2.2 `DeliveryProgress`

```
DeliveryProgress
├── bytesTransferred: Long
├── totalBytes: Long                 -- live size when known, else declared (D4)
├── fraction: Float                  -- derived; 0f when totalBytes == 0
└── phase: Pending | Transferring | WaitingForNetworkPolicy | RequiresConfirmation
```

`WaitingForNetworkPolicy` and `RequiresConfirmation` exist so a Play-parked transfer renders as a
recoverable wait rather than a silent stall (D11, SC-007).

### 2.3 `DeliveryFailure`

```
enum DeliveryFailure
├── NoConnectivity        -- FR-015
├── InsufficientStorage   -- FR-007
├── Cancelled             -- FR-006
├── Evicted               -- OS reclaimed the content (spec edge case)
└── Unknown(code)
```

Every value is retryable from the UI. `Evicted` is not an error state the student caused — it
renders as plain "not installed" with the normal install affordance.

### 2.4 `RemovalOutcome`

```
sealed RemovalOutcome
├── Reclaimed(bytes)                     -- Android: space is back now
└── ReleasedPendingSystemReclaim(bytes)  -- iOS: released; the OS reclaims when it needs space (D2)
```

The one place the platforms genuinely differ. Confirmation copy and the post-removal figure are
driven by this type so neither platform's UI claims something untrue.

## 3. Reporting projections

### 3.1 `MatnStorageEntry`

```
MatnStorageEntry
├── matnId: String
├── title: String
├── bytes: Long          -- the ContentAvailability.Installed.occupiedBytes figure (§2.1)
└── isStarter: Boolean   -- true ⇒ rendered "part of the app", no remove action (FR-027)
```

### 3.2 `StorageUsage`

```
StorageUsage
├── entries: List<MatnStorageEntry>   -- installed only, ordered by bytes DESC (FR-025)
├── totalUsedBytes: Long              -- Σ entries.bytes, starter included (spec clarification)
├── onDemandUsedBytes: Long           -- Σ non-starter entries; 0 ⇒ zero state (FR-028)
└── freeSpaceBytes: Long              -- device remaining (FR-030)
```

`totalUsedBytes` includes the starter so SC-003 holds against the device's real footprint;
`onDemandUsedBytes` is what decides the zero state, so a fresh install still reads as "nothing
added yet".

## 4. Relationships

```
matn (1) ──── (1) content_pack
  │                   │
  │                   └── pack_id ──► delivery platform (asset pack / ODR tag)  [not a DB relation]
  │
  ├── (n) verse ── (1) audio_asset      -- describes audio; unaffected by install/remove
  ├── (n) bookmark | note | memorization -- personal data; unaffected by install/remove (FR-020)
  └── (1) saved_session                  -- resume state; unaffected by install/remove (FR-020, FR-023)
```

The important shape is what is **absent**: no edge runs from `content_pack` to any personal-data
table, so no removal path can reach one.

## 5. State transitions

```
                    install()                    complete
   NotInstalled ───────────────► Installing ────────────────► Installed
        ▲  ▲                        │  │                          │
        │  │      cancel() /        │  │                          │  remove()
        │  └──────failure ──────────┘  │                          │
        │                              │ platform parks transfer  │
        │                     ┌────────┴────────┐                 │
        │                     │ WaitingForNetworkPolicy /         │
        │                     │ RequiresConfirmation             │
        │                     └────────┬────────┘                 │
        │                              │ resumes                  │
        │                              ▼                          │
        │                         (Transferring)                  │
        │                                                         │
        └─────────────────── OS eviction ─────────────────────────┘
```

**Invariants**

- No transition produces a partial state; `Installing → NotInstalled` always releases partial bytes
  (FR-006, FR-009).
- `Installed → NotInstalled` happens by explicit removal *or* OS eviction. Both are detected by
  reading through to the platform, never by a stored flag (D5).
- The starter matn is permanently `Installed`; no transition applies to it.
- Process death during `Installing` resolves to whatever the platform reports on next read — never
  to `Installed` (FR-009).

## 6. Validation rules

| Rule | Source | Where enforced |
|---|---|---|
| Free space ≥ required size before install starts | FR-007 | `InstallMatnContentUseCase`, using the most authoritative size available (D4) |
| Connectivity present before install starts | FR-015 | `InstallMatnContentUseCase` |
| Starter matn cannot be removed or installed | FR-027 | `RemoveMatnContentUseCase`, `RemoveAllContentUseCase` |
| "Remove all" spares the starter | FR-029 | `RemoveAllContentUseCase` |
| Playback blocked unless `Installed` | FR-011 | `EnsureMatnPlayableUseCase` at session start (D9) |
| Duplicate install request is a no-op, not a second transfer | spec edge case | repository, keyed by `packId` |
| Removal never touches personal data | FR-020 | structural — no schema edge exists (§4) |
| Breakdown ordered by size descending | FR-025 | `ObserveStorageUsageUseCase` |
| Starter contributes its measured size to the total | FR-025, SC-003 | `ContentPackRepositoryImpl`, per §2.1 rule 1 |
| Removal wins a race against an in-flight install of the same matn | spec edge case | repository — cancel first, then remove; the install resolves to `NotInstalled(Cancelled)` |
