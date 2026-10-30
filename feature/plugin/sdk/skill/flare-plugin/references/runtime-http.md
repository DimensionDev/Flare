# Runtime, HTTP, and state

## Execution environment

Flare evaluates one self-contained classic `plugin.js` in an isolated runtime:

| Host target | JavaScript engine |
| --- | --- |
| Native iOS / macOS | System JavaScriptCore |
| Android | AndroidX JavaScriptEngine, supplied by Android System WebView |
| Compose Desktop (including macOS) | Bundled QuickJS |

Android requires a provider supporting message ports, Promise results, and
evaluation beyond the Binder transaction limit. Update Android System WebView
when the Host reports that JavaScript is unavailable; there is no QuickJS fallback.
Engine versions on Apple and Android follow the OS/provider. Use portable
JavaScript and test on each target; newer syntax, `Intl` data, stack traces and
engine-specific globals are not a cross-platform compatibility guarantee.

Register exactly once with `defineFlareSocialPlugin`. Use standard JavaScript data handling
and the frozen `flare` Host facade. Browser/Node facilities (`fetch`, DOM,
`localStorage`, `require`, `process`, `Buffer`, browser URL helpers, timers) are
not part of the plugin contract. Do not assume them from your build environment.
Static/dynamic imports, remote code, `eval`, `Function`, and WebAssembly compilation
are unavailable. A TypeScript declaration file supplies types, not runtime code.
If authoring with a bundler, commit its self-contained classic-script output at
repository root and check the output for unsupported dependencies.

Initialization is also evaluated during installation, without Host services.
Define constants/functions and register synchronously at top level. HTTP,
credential, crypto, and locale calls belong inside registered invocations.
Requests and contexts are deeply frozen. Copy data before modifying it.

Runtime scopes are detector attempt, login flow, account, and guest origin.
Calls in one runtime serialize. Globals may cache derived data but are not
persistent storage. Login begin/resume can use different runtime generations;
OAuth state that your code needs belongs in `pendingPayload`. There are no
lifecycle hooks or general-purpose storage API.
An unmarked exception, invalid Wire response, timeout, cancellation, or memory
failure retires that generation. See [recovery](troubleshooting.md).

## Async completion and errors

Flare waits for the registered method's returned/awaited Promise and for Host
tasks started by that invocation to finish before returning its result, including
when the method fails. This is completion tracking, not automatic error propagation:
a rejected Promise outside the method's `await`/`return` chain does not by itself
fail the invocation. This also applies to a Host call that the method did not await.
Always await required HTTP requests and credential updates so their failures affect
the result. Await dependent follow-up work; detached Promise chains are not a
background-task API and cannot rely on the invocation remaining active.

```js
await flare.credential.replace({ token: newToken });
return result; // returned only after persistence; a failed replacement fails this chain
```

Caught errors remain caught. A returned/awaited `flare.error(...)` remains a typed
plugin error; other returned/awaited failures (including `throw null` or rejected
`undefined`) retire the runtime. Detached failures may have no diagnostic.

Host deadlines and cancellation still stop waiting for a result, cancel invocation
Host work, and retire the runtime. They do not guarantee interruption of executing
JavaScript. In particular, public JavaScriptCore cannot stop a synchronous loop;
its worker may retain CPU/memory until the script returns or the process exits.
There is no uniform per-runtime heap/stack cap or heap-based pool eviction. The
system/provider still imposes its own limits. Android isolates share a sandbox
process: its death can invalidate multiple runtimes, which are recreated on later
calls. Failed operations are not automatically replayed. Keep computation bounded
and persistent state in the Host's existing credential/account APIs.

## Invocation context

`pluginId`, `platformId`, and `packageHash` identify the running package.
`scope` is `detector`, `login`, `account`, or `guest`; `origin` is the normalized
selected HTTPS origin. `accountId` is supplied for account invocations, not new
login or guest calls. `locale`, `approvedOrigins`, `credentialAvailable`, and
`assetHandles` describe this call. Treat them as Host-owned values; do not
construct a different account identity or widen permissions from remote data.

## Origins and permissions

| Scope | HTTP origins | Credential API | Proxy routing |
| --- | --- | --- | --- |
| Detector | Candidate origin | Unavailable | Global |
| New login | Selected origin + declared authOrigins | Unavailable | Global |
| Relogin | Selected origin + declared authOrigins | Unavailable | Existing account |
| Account | Verified account origin | Current account only | Account |
| Guest | Selected guest origin | Unavailable | Global |

Supply absolute HTTPS request URLs without URL credentials or fragments. Same
hostname with a different scheme/port is a different origin. A website whose
authenticated API is on another origin must be designed around the selected
API origin; adding `authOrigins` does not grant cross-origin account requests.

Only GET/HEAD redirects are supported, up to five, staying on the current origin
even if another origin is approved for login. Redirected POST uploads/token
exchanges must use their final endpoint directly.

`mediaNetwork: true` separately permits automatic remote images/avatars/covers/
audio/video, including HTML images and CDN/federated destinations. Installation
review discloses this broad media access. Media can expose data embedded in its
URL; keep secrets out of media and link URLs. Permission changes follow the
running package immediately, including cached/nested media. Local-file and
non-HTTPS resources remain unavailable.

Flare controls proxies, including resumed OAuth. A proxy failure does not fall
back to a direct request. A route change can cancel an in-flight upload or call.

## HTTP request and response

```js
// Inside an account method; adapt the path and credential fields to the website.
const credential = await flare.credential.read();
const response = await flare.http.request({
  method: "GET",
  url: context.origin + "/api/me",
  authorization: { type: "bearer", token: credential.accessToken },
});
if (response.status === 401) {
  throw flare.error({ code: "AuthenticationRequired", message: { value: "Log in again" } });
}
if (response.status < 200 || response.status >= 300) {
  throw flare.error({ code: "Remote", message: { value: "The website rejected the request" } });
}
let data;
try {
  data = JSON.parse(response.body);
} catch (_) {
  throw flare.error({ code: "InvalidResponse", message: { value: "Expected JSON" } });
}
```

The result is `{ status, headers, body }`: body is a UTF-8 string and each header
value is an array of strings. Inspect status yourself; a 4xx/5xx response is not
automatically a thrown plugin error. Handle legitimate empty/204 responses
without JSON parsing. Header names should be looked up case-insensitively.
Do not use `response.ok`, `.json()`, `.text()`, or binary Response APIs.

`method` defaults to GET; allowed methods are GET, POST, PUT, PATCH, DELETE, HEAD.
GET/HEAD cannot have a body. `timeoutMillis` may shorten the invocation's timeout,
not extend it. The invocation deadline covers the whole method, including all
HTTP calls, so keep pagination and uploads bounded.

Authentication uses `authorization: { type: "bearer", token }` or
`{ type: "basic", username, password }`; Cookies use
`cookies: [{ name, value }]`. Never put Authorization/Cookie/Host/Content-Length
directly in headers. Connection/keep-alive/transfer/upgrade and proxy-/sec-
headers are also controlled by the Host. Response headers exclude sensitive and
hop-by-hop headers, including Set-Cookie: there is no implicit Cookie jar to
build from a login response. Use the Cookie login flow when browser Cookies are
required, or the website's documented token API.

### Body recipes

These are request `body` fragments; replace website fields with verified ones.

```js
const jsonBody = { type: "json", value: { text: "Hello" } };
const formBody = { type: "form", values: { grant_type: "authorization_code", code: "CODE" } };
const textBody = { type: "text", value: "hello", contentType: "text/plain" };
```

Multipart media uses Host-owned asset handles, not file bytes:

```js
// Inside compose.publish; one actual request asset is shown.
const asset = request.assets[0];
const upload = await flare.http.request({
  method: "POST",
  url: context.origin + "/api/media",
  authorization: { type: "bearer", token: credential.accessToken },
  body: {
    type: "multipart",
    parts: [
      { type: "asset", name: "file", handle: asset.handle, fileName: asset.fileName },
      { type: "text", name: "description", value: asset.description || "" },
    ],
  },
});
```

Parse/check the upload response before publishing. Handles are valid only for
the current invocation, cannot be synthesized or persisted, and cannot be read
as bytes from JavaScript. The Host streams the asset.

## Credentials, crypto, and locale

`await flare.credential.read()` returns only the current account's JSON payload.
`await flare.credential.replace(next)` atomically replaces the **whole** payload
and resolves true. Merge retained refresh-token/client fields yourself when
necessary. Initial login returns `credential` in `LoginSuccessV1` instead; neither
detector nor login nor guest has credential access. An account cannot read or
replace another account's credential.

`await flare.crypto.randomHex(size)` uses **bytes** as its size and returns twice
as many hexadecimal characters. `uuid()` returns a UUID string. `sha256(value)`
hashes the UTF-8 string and returns hex, not a byte array or base64url PKCE value.
Implement any provider-required PKCE encoding explicitly using compatible JS;
do not assume Web Crypto or Buffer. `await flare.locale.current()` returns the
Host locale, also supplied as `context.locale`.

Host bridge failures carry codes such as `host.unavailable`,
`host.invalid-request`, and `credential.denied`. Catch only failures you can
classify meaningfully: the [template helper](../assets/github-template/plugin.js)
maps unavailable transport to a typed Network error while leaving programming
and permission mistakes visible. Do not mask every error as an empty page.
