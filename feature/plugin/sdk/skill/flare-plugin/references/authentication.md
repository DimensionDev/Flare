# Detection and authentication recipes

These recipes describe the five supported interaction kinds. Website paths and
helpers in sketches are adaptation points, not universal endpoints. Use the
[GitHub template](../assets/github-template/plugin.js) for a complete token-login
implementation and [Pixelfed](../examples/pixelfed/plugin.js) for complete OAuth.
Never accept a submitted token/password as proof of identity without verifying
it against the website.

## Detection

Declare `platform.detector: { "priority": 0 }` only when implementing
`detector.detect(request, context)`. Inspect a documented endpoint on the
candidate origin. Return `match: "none"` when evidence does not match;
`"exact"` for the intended software and `"compatible"` only for known compatible
behavior. Return the canonical HTTPS origin and optional software/instance data:

```js
// Result after verifying the website's software identity.
const detected = {
  match: "exact",
  canonicalOrigin: context.origin,
  software: "ExampleSoftware",
  instance: { domain: context.origin.slice("https://".length), title: "Example" },
};
```

Do not claim every reachable host. Detection has no credential access and may
request only the candidate origin. Omit the declaration and method if explicit
platform selection is sufficient, as in the template.

## State transitions

The method key is the manifest method **ID**, not necessarily its interaction
name. `id: "token"` registers `login.token.begin`, for example.

| Interaction | Calls | Allowed outcome |
| --- | --- | --- |
| OAuth | begin → browser callback → resume | begin: externalBrowser or success; resume: success |
| Password | begin(values) | success |
| CredentialImport | begin(values) | success |
| Form | begin(values) | success |
| WebCookie | begin → repeated check(snapshot) | begin: webCookie; check: pending or success |

`pending` is not a general multi-step form mechanism. A form is submitted once;
include all fields required to complete that submission. Expected login errors
use `flare.error`; do not return an invented `error` transition.

All successful methods return:

```js
// profile and credential come from a verified remote login.
const success = {
  type: "success",
  value: {
    accountId: profile.key.id,
    origin: context.origin,
    credential,
    profile,
    capabilities: { "flare.datasource.timeline/v1": ["page"] },
  },
};
```

`accountId` equals `profile.key.id`; the profile host equals the selected origin's
host, including a non-default port. The Host checks origin, account ownership,
and relogin identity. At least one displayable, declared capability must be
negotiated with its required operation set. Login can return `composeConfig`
using the [documented merge rules](manifest.md#compose-defaults). Preserve only credentials needed for later requests;
avoid retaining a password if the server issues a reusable token instead.

## Credential import

Use `interaction: "CredentialImport"` and a `Secret` field. The template has the
full manifest, `/api/me` verification, typed errors, and successful identity.
Adapt its token acquisition instructions and payload to the actual website.
Login contexts have no credential store access: return the verified payload;
Flare persists it. Do not call `flare.credential.replace` during login.

## Password

Manifest method fragment:

```json
{
  "id": "password",
  "interaction": "Password",
  "title": "Sign in",
  "fields": [
    { "id": "username", "type": "Username", "label": "Username" },
    { "id": "password", "type": "Password", "label": "Password" }
  ]
}
```

Service sketch (the three helpers are website-specific implementations):

```js
const passwordService = {
  async begin(request, context) {
    const token = await exchangePassword(context.origin, request.values.username, request.values.password);
    const profile = await verifiedProfile(context.origin, token);
    return loginSuccess(context, profile, { accessToken: token });
  },
};
```

`exchangePassword` sends structured form/JSON/basic authentication as specified
by the website and checks the HTTP response. `verifiedProfile` maps the actual
authenticated identity. `loginSuccess` constructs the success shape above.
Register this as `login.password`. Report credential rejection with a typed
AuthenticationRequired or Validation error appropriate to that API.

## Form

Use a form for a different fixed collection of fields, such as username + OTP:

```json
{
  "id": "otp",
  "interaction": "Form",
  "title": "Sign in with a code",
  "fields": [
    { "id": "username", "type": "Username", "label": "Username" },
    { "id": "code", "type": "Otp", "label": "One-time code" }
  ]
}
```

```js
const otpService = {
  async begin(request, context) {
    const credential = await verifyOneTimeCode(context.origin, request.values);
    const profile = await verifiedProfile(context.origin, credential.accessToken);
    return loginSuccess(context, profile, credential);
  },
};
```

Implement those website-specific helpers and register as `login.otp`. If the
website requires a preceding “send code” step, explain how the user obtains the
code outside this form or choose a supported browser flow. Do not invent dynamic
field updates, an extra resume call, or a pending-form transition.

## OAuth

Declare `{ "id": "oauth", "interaction": "OAuth", "title": "Authorize" }`
without fields or Cookie config. Implement `login.oauth.begin` and `.resume`.
Add exact `authOrigins` for a separate authorization/token origin when needed;
this does not expand later account HTTP access.

1. `begin` receives Host-owned `flowId`, `state`, `redirectUri`, and selected
   origin. Use the supplied state and redirect URI. Register a client only if
   the provider supports it, otherwise use the provider-approved configuration.
2. Return an approved HTTPS browser URL and JSON `pendingPayload` containing
   only the bounded data needed to resume (for example client ID and PKCE
   verifier). The begin runtime closes; globals do not carry the flow forward.
3. `resume` receives Host-validated callback parameters and persisted pending
   payload. Handle the provider's denial/error response. Exchange the code,
   verify the resulting account, and return success.

```js
// begin result fragment; build authorizationUrl from the provider's contract.
const browserTransition = {
  type: "externalBrowser",
  url: authorizationUrl,
  pendingPayload: { clientId, verifier },
};
```

The Host owns callback validation, expiry, persistence, replay protection, and
relogin identity. It rejects a flow if the running package changed. The plugin
still implements the provider's token exchange and PKCE requirements; SHA-256
returns hex, so see [crypto encoding](runtime-http.md#credentials-crypto-and-locale).
Never hardcode a callback URI from a sample. See Pixelfed for dynamic client
registration, state propagation, code exchange, and profile verification.

## WebCookie

Manifest method fragment:

```json
{
  "id": "browser",
  "interaction": "WebCookie",
  "title": "Sign in in the browser",
  "cookie": {
    "startUrl": "$accountOrigin/login",
    "probes": [{
      "url": "$accountOrigin/api/me",
      "cookies": [{ "name": "session", "required": true }]
    }]
  }
}
```

Service sketch; `fetchVerifiedCookieProfile`, `mapProfile`, and `loginSuccess`
must be implemented for the selected site's profile API and result shape:

```js
const browserService = {
  async begin(request, context) {
    return { type: "webCookie", startUrl: context.origin + "/login" };
  },
  async check(request, context) {
    const probeUrl = context.origin + "/api/me";
    const cookies = request.cookies.cookies
      .filter(cookie => cookie.sourceUrl === probeUrl)
      .map(({ name, value }) => ({ name, value }));
    const profile = await fetchVerifiedCookieProfile(probeUrl, cookies);
    if (!profile) return { type: "pending" };
    return loginSuccess(context, mapProfile(profile, context), { cookies });
  },
};
```

Register as `login.browser`. The returned start URL must exactly match the
resolved manifest start URL. The Host collects Cookies only for declared probes
and filters domain/path/HTTPS scope. `sourceUrl` identifies the probe: do not
combine Cookies from distinct origins merely because their names match. Use
structured `cookies` in the verification request, and retain only the values
appropriate for later account-origin requests. Required missing Cookies delay
checking; a present Cookie still needs server-side identity verification.

Return pending only for a verified “not authenticated yet” state. Map rate limits,
network failures, and malformed responses to their actual typed errors instead
of silently polling forever. The Host prevents overlapping checks and owns the
session's disposal; the plugin supplies no lifecycle hook.

## Refresh and relogin

Refresh access tokens within an account method using the website's documented
flow and the account's allowed origin. After successful verification, atomically
replace the full credential payload, retaining required fields. Retry the
original operation only when safe (especially for writes); avoid unbounded loops.
An invalid/expired credential that cannot be refreshed should raise
AuthenticationRequired, which the account adapter routes to relogin. The Host
requires the same account identity on relogin; schema changes are described in
[publishing](publishing.md#updates-and-compatibility).
