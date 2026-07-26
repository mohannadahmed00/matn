# Contract: REST Surfaces

**Feature**: `specs/011-teacher-authoring-upload` | Satisfies FR-002, FR-003, FR-004, FR-005, FR-014, FR-031, FR-043

One Ktor client in `:shared/commonMain/data/remote` covers four surfaces. Written for all five
targets so Phase 13 inherits it unchanged (research D1).

---

## 1. Client construction

```kotlin
// data/remote/HttpClientFactory.kt — commonMain, no expect/actual.
// CIO is published for JVM, Android, and all iOS variants, so one engine serves every target.
// An expect/actual seam here would fail to compile the moment androidMain/iosMain lacked an actual.
fun createHttpClient(config: FirebaseConfig): HttpClient = HttpClient(CIO) { … }

// Shared configuration, one place:
//   - ContentNegotiation + kotlinx-json  (ignoreUnknownKeys = true, explicitNulls = false)
//   - HttpTimeout: 30 s request, 15 s connect
//   - No Ktor Auth plugin — token refresh is explicit (§3.3), because a bearer token that
//     silently refreshes hides Unauthorized from the error mapping FR-005 depends on.
//   - No logging plugin in release paths: FR-003b forbids credentials reaching any log.
```

`FirebaseConfig` (`projectId`, `apiKey`, `storageBucket`, optional `emulatorHost`) is injected, not
compiled in (research D13). When `emulatorHost` is set every base URL points at it, which is what
makes the rule tests run against the real client.

---

## 2. Base URLs

| Surface | Production | Emulator |
|---------|-----------|----------|
| Identity Toolkit | `https://identitytoolkit.googleapis.com/v1` | `http://{host}:9099/identitytoolkit.googleapis.com/v1` |
| Secure Token | `https://securetoken.googleapis.com/v1` | `http://{host}:9099/securetoken.googleapis.com/v1` |
| Firestore | `https://firestore.googleapis.com/v1` | `http://{host}:8080/v1` |
| Storage | `https://firebasestorage.googleapis.com/v0` | `http://{host}:9199/v0` |

---

## 3. Identity Toolkit — `IdentityToolkitClient`

### 3.1 Sign in (FR-002)

```http
POST {identity}/accounts:signInWithPassword?key={apiKey}
Content-Type: application/json

{ "email": "...", "password": "...", "returnSecureToken": true }
```

Response → `TeacherSession`: `localId`→`uid`, `displayName`, `email`, `idToken`, `refreshToken`,
`expiresIn` (seconds, → `idTokenExpiresAt`).

On success the **refresh token only** is written to `SecretStore` (FR-003a, research D7). The ID
token stays in memory.

### 3.2 Error mapping — the FR-005 messages

Identity Toolkit returns a machine-readable `error.message`. Mapping it is what turns a 400 into
something a teacher can act on:

| `error.message` | `RemoteError` | Teacher-facing meaning |
|-----------------|---------------|------------------------|
| `EMAIL_NOT_FOUND`, `INVALID_PASSWORD`, `INVALID_LOGIN_CREDENTIALS` | `Unauthorized` | Email or password is wrong (deliberately not distinguished) |
| `USER_DISABLED` | `Forbidden` | This account has been disabled |
| `TOO_MANY_ATTEMPTS_TRY_LATER` | `Server`, retryable | Too many attempts; wait and retry |
| Connection failure / timeout | `Network`, retryable | Cannot reach the server (FR-001 scenario 5) |

FR-001 scenario 2 requires wrong credentials to produce an understandable error **and store no
session** — the `SecretStore` write happens only after a successful response, so a failed sign-in
cannot leave a credential behind.

### 3.3 Token refresh (FR-003)

```http
POST {secureToken}/token?key={apiKey}
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&refresh_token={refreshToken}
```

`TokenRefresher` policy:
- Refresh proactively when the ID token is within 5 minutes of expiry, before the request that needs
  it — so a long editing session never fails mid-save on an expired token.
- One refresh in flight at a time; concurrent callers await the same result.
- A refresh rejected with `TOKEN_EXPIRED` / `INVALID_REFRESH_TOKEN` clears the `SecretStore` entry and
  surfaces `Unauthorized`, which the UI turns into a re-authentication prompt **without discarding
  on-screen work** (FR-032, and the session-expiry edge case).

### 3.4 Sign out (FR-004)

No server call. Clear the in-memory session and the `SecretStore` entry, then emit `null` from
`observeSession()`. Every repository method checks for a session first, so subsequent writes fail
locally as `Unauthorized` rather than reaching the network.

---

## 4. Firestore — `FirestoreRestClient`

All requests carry `Authorization: Bearer {idToken}` except anonymous reads.

| Operation | Method + path |
|-----------|---------------|
| Get document | `GET {firestore}/projects/{p}/databases/(default)/documents/matns/{id}` |
| List overviews | `GET .../documents/matns?mask.fieldPaths=…&pageSize=100` (+ `pageToken`) |
| Query published | `POST .../documents:runQuery` with a `published == true` `fieldFilter` |
| Create | `PATCH .../documents/matns/{id}?currentDocument.exists=false` |
| Update | `PATCH .../documents/matns/{id}?currentDocument.updateTime={t}` |

Document bodies use the typed `FirestoreValue` encoding (research D10); the field list is in
[firestore-schema.md](./firestore-schema.md).

**Full-body writes only.** A partial-field `updateMask` would let two concurrent edits interleave
into a document that is neither version — the state FR-033 forbids. Sending the whole document keeps
"either fully updated or unchanged" true by construction.

`updateTime` from every read is retained as `MatnDraft.remoteUpdateTime` and returned on the next
write (research D4).

### 4.1 Status mapping

| HTTP | Firestore `status` | `RemoteError` | Retryable |
|------|--------------------|---------------|-----------|
| 200 | — | — | — |
| 400 | `FAILED_PRECONDITION` | `Conflict` | No — reload, re-apply, retry (FR-043) |
| 400 | `INVALID_ARGUMENT` | `Decode` | No — a client bug; report, do not retry |
| 401 | `UNAUTHENTICATED` | `Unauthorized` | No — refresh once, then re-authenticate |
| 403 | `PERMISSION_DENIED` | `Forbidden` | No |
| 404 | `NOT_FOUND` | `AppError.NotFound` | No |
| 429 | `RESOURCE_EXHAUSTED` | `QuotaExceeded` | No |
| 5xx | — | `Server` | Yes |
| — | connection failure | `Network` | Yes |

The retryable column is exactly what FR-032 requires be reported to the teacher, and what
FR-031c's autosave uses to decide whether the next interval should try again.

---

## 5. Storage — `StorageRestClient`

### 5.1 Cover upload (FR-014, FR-016)

```http
POST {storage}/b/{bucket}/o?uploadType=media&name=matns%2F{matnId}%2Fcover.{ext}
Authorization: Bearer {idToken}
Content-Type: image/png | image/jpeg | image/webp
Content-Length: {bytes}

<binary>
```

Client-side pre-checks before the request, so a rejection is immediate and specific (FR-016): 5 MB
ceiling, and one of the three content types. The same limits are enforced in the Storage rules
(see [security-rules.md](./security-rules.md) §2) so the client check is convenience, not the
boundary.

The object path is deterministic per matn, so a re-upload overwrites and never accumulates orphans.
Uploaded **before** the document patch (research D3).

### 5.2 Storage usage (portal display)

The design's "STORAGE USAGE — 4.2 GB of 10 GB" row is informational (Assumptions). It sums
`size` across `GET {storage}/b/{bucket}/o?prefix=matns/`, paginated. No quota is enforced by this
phase beyond reporting what the backend itself rejects; a quota rejection surfaces as
`QuotaExceeded` with a storage-specific message rather than a generic failure (the storage-exhausted
edge case).

---

## 6. Testing the client (Principle V)

| Layer | How |
|-------|-----|
| Request shape | Ktor `MockEngine` in `jvmTest` asserts method, path, query, headers, and body for every operation above. **Verified**: `ktor-client-mock:3.2.3` is already in the local Gradle cache |
| Error mapping | `MockEngine` returns each status/`status`-code pair in §4.1 and §3.2; assert the resulting `RemoteError` and its `retryable` flag |
| Token refresh | Injected clock; assert proactive refresh inside the 5-minute window, single-flight under concurrent callers, and `SecretStore` cleared on `INVALID_REFRESH_TOKEN` |
| Value codec | `commonTest`, round-trip per type, plus the integer-as-string and RFC 3339 timestamp quirks |
| Rules | Emulator, `jvmTest`, through this same client — see [security-rules.md](./security-rules.md) §3 |

No test in this list needs a network, a device, or the emulator except the last, which is env-gated.
