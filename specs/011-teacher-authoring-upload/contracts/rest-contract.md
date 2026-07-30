# Contract: REST Surfaces

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-002, FR-003, FR-004, FR-005, FR-014, FR-031, FR-043

One Ktor client in `:shared/commonMain/data/remote` covers three surfaces — Supabase Auth,
PostgREST, and Storage — all on the same project. Written for all five targets so Phase 13 inherits
it unchanged (research D1).

---

## 1. Client construction

```kotlin
// data/remote/HttpClientFactory.kt — commonMain, no expect/actual.
// CIO is published for JVM, Android, and all iOS variants, so one engine serves every target.
// An expect/actual seam here would fail to compile the moment androidMain/iosMain lacked an actual.
fun createHttpClient(engine: HttpClientEngine? = null): HttpClient = …

// Shared configuration, one place:
//   - ContentNegotiation + kotlinx-json  (ignoreUnknownKeys = true, explicitNulls = false)
//   - HttpTimeout: 30 s request, 15 s connect
//   - No Ktor Auth plugin — token refresh is explicit (§3.3), because a bearer token that
//     silently refreshes hides Unauthorized from the error mapping FR-005 depends on.
//   - No logging plugin in release paths: FR-003b forbids credentials reaching any log.
```

`SupabaseConfig` (`projectUrl`, `anonKey`, `bucket`) is injected, not compiled in (research D13).
There is one config object because there is one backend; the Firebase era needed a second one, and
keeping two base-URL families in step was a standing hazard.

Pointing `projectUrl` at `http://127.0.0.1:54321` runs everything against a local stack
(`supabase start`) — that is the whole of what the Firebase emulator's `emulatorHost` special-casing
used to do, and it is what makes the policy tests run against the real client.

---

## 2. Base URLs

All three derive from `projectUrl`, so there is nothing to keep in step.

| Surface | Path |
|---------|------|
| Auth | `{projectUrl}/auth/v1` |
| PostgREST | `{projectUrl}/rest/v1` |
| Storage | `{projectUrl}/storage/v1` |

**Every request carries `apikey: {anonKey}`**, including sign-in, where there is no bearer token
yet. It identifies the project, not the caller; row-level security is what authorises.

---

## 3. Auth — `SupabaseAuthClient`

Sign-in and refresh are the same endpoint, distinguished only by `grant_type`.

### 3.1 Sign in (FR-002)

```http
POST {auth}/token?grant_type=password
apikey: {anonKey}
Content-Type: application/json

{ "email": "...", "password": "..." }
```

Response → `TeacherSession`: `user.id`→`uid`, `user.email`, `user.user_metadata.display_name`
(falling back to `full_name`, `name`, then the email's local part), `access_token`, `refresh_token`,
`expires_in` (seconds, → `accessTokenExpiresAt`).

On success the **refresh token only** is written to `SecretStore` (FR-003a, research D7). The access
token stays in memory.

### 3.2 Error mapping — the FR-005 messages

Supabase Auth returns a machine-readable `error_code`. Mapping it is what turns a 400 into something
a teacher can act on. Two body shapes exist — the current
`{"code":400,"error_code":"…","msg":"…"}` and the OAuth-style `{"error":"…","error_description":"…"}`
that an older or self-hosted stack still emits — and both are read before falling back to the status
code.

| `error_code` / `error` | `RemoteError` | Teacher-facing meaning |
|------------------------|---------------|------------------------|
| `invalid_credentials`, `invalid_grant`, `email_not_confirmed` | `Unauthorized` | Email or password is wrong (deliberately not distinguished) |
| `refresh_token_not_found`, `refresh_token_already_used`, `session_expired`, `bad_jwt` | `Unauthorized` | The stored session is no longer valid; sign in again |
| `user_banned`, `signup_disabled` | `Forbidden` | This account cannot sign in |
| `over_request_rate_limit` | `Server`, retryable | Too many attempts; wait and retry |
| Connection failure / timeout | `Network`, retryable | Cannot reach the server (FR-001 scenario 5) |

FR-001 scenario 2 requires wrong credentials to produce an understandable error **and store no
session** — the `SecretStore` write happens only after a successful response, so a failed sign-in
cannot leave a credential behind.

### 3.3 Token refresh (FR-003)

```http
POST {auth}/token?grant_type=refresh_token
apikey: {anonKey}
Content-Type: application/json

{ "refresh_token": "..." }
```

`TokenRefresher` policy:
- Refresh proactively when the access token is within 5 minutes of expiry, before the request that
  needs it — so a long editing session never fails mid-save on an expired token.
- One refresh in flight at a time; concurrent callers await the same result.
- **Supabase rotates the refresh token on every use**, so the newly issued one is written back to
  `SecretStore` each time. Keeping the old one would break the next restore.
- A rejected refresh clears the `SecretStore` entry and surfaces `Unauthorized`, which the UI turns
  into a re-authentication prompt **without discarding on-screen work** (FR-032, and the
  session-expiry edge case).

The refresh response carries the full `user` object. Firebase's Secure Token API did not, and a
session restored from nothing but a persisted refresh token used to show a blank display name and
email until the next sign-in; that limitation is gone.

### 3.4 Sign out (FR-004)

No server call. Clear the in-memory session and the `SecretStore` entry, then emit `null` from
`observeSession()`. Every repository method checks for a session first, so subsequent writes fail
locally as `Unauthorized` rather than reaching the network.

---

## 4. PostgREST — `PostgrestClient`

All requests carry `apikey: {anonKey}` and `Authorization: Bearer {accessToken}`.

| Operation | Method + path |
|-----------|---------------|
| Read one row | `GET {rest}/matns?id=eq.{id}&select=*` |
| List overviews | `GET {rest}/matns?select={overview columns}&order=updated_at.desc` |
| Create | `POST {rest}/matns` with `Prefer: return=representation` |
| Update | `PATCH {rest}/matns?id=eq.{id}&revision=eq.{r}` with `Prefer: return=representation` |

Row bodies are plain JSON — the column list is in [postgres-schema.md](./postgres-schema.md). The
typed `FirestoreValue` wrapper codec that Firestore's REST API required has no counterpart and is
gone.

**Full-row writes only.** A partial-column update would let two concurrent edits interleave into a
row that is neither version — the state FR-033 forbids. Sending the whole row keeps "either fully
updated or unchanged" true by construction.

`revision` from every read is retained as `MatnDraft.remoteRevision` and sent as the update filter
on the next write (research D4). `Prefer: return=representation` brings the bumped value back on the
same round trip.

### 4.1 Status mapping

| HTTP | SQLSTATE / code | `RemoteError` | Retryable |
|------|-----------------|---------------|-----------|
| 200 / 201 | — | — | — |
| 200, empty array on an `UPDATE` | — | `Conflict` or `Forbidden` — see §4.2 | No |
| 409 | `23505` unique violation | `Conflict` | No — reload, re-apply, retry (FR-043) |
| 403 | `42501` RLS violation | `Forbidden` | No |
| 401 | `PGRST301` JWT expired | `Unauthorized` | No — refresh once, then re-authenticate |
| 404 | — | `AppError.NotFound` | No |
| 413 | — | `QuotaExceeded` | No |
| 429 | — | `QuotaExceeded` | No |
| 5xx | — | `Server` | Yes |
| 400, or an unparseable body | — | `Decode` | No — a client bug; report, do not retry |
| — | connection failure | `Network` | Yes |

The retryable column is exactly what FR-032 requires be reported to the teacher, and what
FR-031c's autosave uses to decide whether the next interval should try again.

### 4.2 The one ambiguity

PostgREST reports both "the `revision` precondition failed" and "row-level security hid this row"
identically: `200` with an empty array. `SupabaseCatalogRepository` disambiguates with one extra read
on the failure path — if the row is visible, the revision moved on (`Conflict`); if it is not, the
`select` policy refuses it too (`Forbidden`).

Firestore distinguished these at the wire level (`FAILED_PRECONDITION` vs `PERMISSION_DENIED`). The
extra read is the price of the swap, and it is paid only when a write has already failed.

---

## 5. Storage — `StorageRestClient`

### 5.1 Cover upload (FR-014, FR-016)

```http
POST {storage}/object/{bucket}/matns/{matnId}/cover.{ext}
apikey: {anonKey}
Authorization: Bearer {accessToken}
x-upsert: true
Content-Type: image/png | image/jpeg | image/webp

<binary>
```

Client-side pre-checks before the request, so a rejection is immediate and specific (FR-016): 5 MB
ceiling, and one of the three content types. The same limits are bucket properties
(see [rls-policies.md](./rls-policies.md) §2.3) so the client check is convenience, not the boundary.

The object path is deterministic per matn and `x-upsert: true` overwrites, so a re-upload never
accumulates orphans. Uploaded **before** the row is written (research D3).

### 5.2 Storage usage (portal display)

The design's "STORAGE USAGE — 4.2 GB of 10 GB" row is informational (Assumptions). It sums
`metadata.size` across `POST {storage}/object/list/{bucket}` with `{prefix, limit, offset}` —
Supabase's list endpoint pages by offset, with no page token. No quota is enforced by this phase
beyond reporting what the backend itself rejects; a quota rejection surfaces as `QuotaExceeded` with
a storage-specific message rather than a generic failure (the storage-exhausted edge case).

---

## 6. Testing the client (Principle V)

| Layer | How |
|-------|-----|
| Request shape | Ktor `MockEngine` in `jvmTest` (`PostgrestClientTest`, `SupabaseAuthClientTest`) asserts method, path, query, headers, and body for every operation above |
| Error mapping | `MockEngine` returns each status/code pair in §4.1 and §3.2; assert the resulting `RemoteError` and its `retryable` flag |
| Token refresh | `TokenRefresherTest` with an injected clock: proactive refresh inside the 5-minute window, single-flight under concurrent callers, refresh-token rotation persisted, and `SecretStore` cleared on a rejected refresh |
| Row mapping | `commonTest` `MatnRowTest`: full round trip, explicit nulls on the write payload, `revision` excluded from it, the overview projection decoding from its column subset alone, and `timestamptz` parsing |
| Policies | Local Supabase stack, `jvmTest`, through this same client — see [rls-policies.md](./rls-policies.md) §3 |

No test in this list needs a network, a device, or the local stack except the last, which is
env-gated.
