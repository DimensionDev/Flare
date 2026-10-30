# Troubleshooting and limits

First identify the stage: repository/version discovery, archive/manifest
validation, registration, immediate activation, login, invocation, or UI
mapping. Record the running plugin version, operation, error code, and a redacted
input/response. Avoid collecting credentials or private content. `flare` has no
logging API; do not invent `flare.log` or assume a developer console is available.

## Common symptoms

| Symptom / message | Check and repair |
| --- | --- |
| No stable version tags | Public repo, stable SemVer tag, matching manifest.version; branches/prereleases are not install choices |
| Missing required file / nested plugin | Put manifest.json, plugin.js, one supported assets/icon.<extension> at the tagged repository root |
| Unknown field / unsupported schema | Use API/schema 1 and exact supported fields; strict JSON rejects extra keys |
| Missing method / incomplete capability | Compare full capability ID, required operation set, registration path, and login negotiation |
| Host API called during registration / undefined global | Keep top-level work to definitions and synchronous registration; move HTTP/crypto into methods |
| Platform ID already registered | Choose a distinct stable platform ID; do not impersonate a built-in platform |
| Installed change has no effect | Changes take effect immediately after successful activation; check validation or activation errors and the running version |
| Login profile identity mismatch | Verify profile.key.id equals accountId, and profile.key.host matches the selected origin |
| Form login did not complete | Password/CredentialImport/Form begin must return success or throw a typed error |
| Cookie login pending indefinitely | Declared probe URL/name/path scope, browser HTTPS, actual server login, and check's profile verification |
| OAuth state/expiry/package error | Use supplied state/redirectUri, resume with pendingPayload, and restart login after a package change or expiry |
| HTTP origin is not approved | Use an absolute selected-origin API URL; authOrigins adds login origins only |
| Request header is not allowed | Use structured authorization/cookies and let the Host manage transport headers |
| JSON parse error | Inspect status/content type/body; handle 204/empty responses and HTML error pages explicitly |
| Missing images with working text | mediaNetwork, current running grant, valid HTTPS URLs, and correct media DTOs |
| Missing tabs/search/controls | Required capabilities, static fields, negotiated account subset, default timeline/profileTabs, and operation completeness |
| Post click does nothing | detailType and target capability; link requires an absolute HTTPS URL |
| Infinite/repeated history | Correct cursor namespace, refresh handling, server exhaustion, and explicitly declared directions |
| Notifications collapse together | Use notification IDs, not the referenced post ID, and preserve actor/kind/timestamp |
| DM polling repeats or crosses rooms | Newer page cursor is separate from roomEntityToken; advance empty successful pages and isolate rooms |
| Actions show stale state | Return complete authoritative updatedPost/updatedRelation or appropriate invalidation; preserve action tokens |
| Runtime retired/paused | Find an unmarked exception, invalid Wire output, or exceeded budget before retrying |
| JavaScript unavailable on Android | Update Android System WebView; the provider must support message ports, Promise results, and large evaluations |
| Invocation succeeds despite an async error | Put required work in the method's await/return chain; detached rejections do not automatically fail the call |

## Typed errors

Only errors created with `flare.error` cross as recoverable plugin errors:

```js
throw flare.error({
  code: "RateLimited",
  message: { key: "error.limited", fallback: "Try again in {seconds} seconds", args: { seconds: 30 } },
  retryAfterSeconds: 30,
});
```

| Code | Typical use |
| --- | --- |
| AuthenticationRequired | Rejected/expired credentials; an account call can trigger relogin |
| NotFound | The requested entity does not exist |
| Validation | Invalid user input, with a useful non-sensitive message |
| RateLimited | Website throttling; optional retryAfterSeconds |
| Network | A classified network failure |
| Remote | Other website HTTP/API failure |
| Unsupported | An operation/parameter combination the adapter cannot handle |
| InvalidResponse | Malformed or semantically invalid website data |
| Cancelled | An explicitly represented plugin cancellation |

Use a WireText message, not a bare string. Retry delay is 0..604800 seconds;
remoteCode is optional and at most 512 characters. HTTP Retry-After may be a date
or seconds: the template handles bounded numeric seconds and otherwise omits
the hint. The field communicates a delay, not a promise of automatic retries.
Avoid returning raw server messages containing credentials or private data.

Unmarked exceptions and invalid DTOs retire the runtime. Three counted fatal
failures within 60 seconds pause the plugin for that process. Cancellation can
retire a generation without counting as a failure; a deadline timeout can count.
An unavailable Android provider or collateral sandbox death does not count as
plugin misconduct. Retry after the provider/sandbox recovers; writes are not replayed.
Fix the underlying issue, then use the Host's retry mechanism where available or
restart. Global state is disposable, so recovery must not depend on it.

## Limits

These are current implementation bounds, not targets. Byte limits apply to UTF-8
or serialized payload bytes; string limits are field-length checks. Keep normal
requests/responses well below them.

| Resource | Bound |
| --- | --- |
| GitHub source archive | 20 MiB compressed, 50 MiB uncompressed, 4096 entries |
| Canonical plugin package | 20 MiB compressed, 50 MiB uncompressed, 256 files |
| manifest / plugin.js / icon | 256 KiB / 4 MiB / 2 MiB |
| Icon dimensions | 1..1024 for each dimension |
| Locale files | 32 catalogs; 256 KiB each; 2048 keys each; 4096 characters per value |
| Registration evaluation | 2-second caller deadline |
| Runtime initialization | 2 seconds |
| Runtime memory / stack | Engine/system limits; no uniform Host cap or heap-based pool eviction |
| Invocation deadline | Detector 10 seconds; normal 30 seconds; extended 120 seconds where selected by the Host |
| Invocation JSON request / response | 1 MiB / 5 MiB |
| HTTP response body | 4 MiB |
| HTTP request headers | 32 headers, 8192 characters/value, 64 KiB aggregate name/value length |
| HTTP text/JSON/form body | 2 MiB; form also max 256 entries |
| HTTP redirects | 5; GET/HEAD, same origin only |
| Page request limit / returned items | 1..100 / at most 200 |
| ID / host | 512 / 253 characters |
| Cursor / entity token / action token | 4096 / 8192 / 8192 characters |
| Credential / OAuth pending JSON | 64 KiB each |
| OAuth pending lifetime | 15 minutes |
| Rich text | 1000000 characters |
| Post media / gallery images | At most 100; gallery must have at least 1 media entry |
| Inline-parent presentation depth | 4 recursive levels beyond the root item |
| Inline parents / quotes / user list / reactions | At most 100 each; user lists cannot be empty |
| Poll options | 2..100, distinct valid ownVotes indices |
| Crypto randomHex input / sha256 input | 1..1024 random bytes / 1 MiB UTF-8 text |

Multipart calls have at most 64 parts; text parts are at most 256 KiB. Assets have
their own Host/compose constraints, up to 1 GiB per asset at the bridge boundary;
the website and negotiated composer commonly impose much smaller limits.
Repeated HTTP requests do not reset an invocation deadline. See
[runtime/HTTP](runtime-http.md) before choosing a timeout or upload strategy.
Deadlines retire a runtime and cancel its Host work; they do not guarantee that
busy JavaScript stops. A JavaScriptCore loop can require an app restart to release
its worker. Avoid recursion/large allocations and test bounded computation on all
three engines.
