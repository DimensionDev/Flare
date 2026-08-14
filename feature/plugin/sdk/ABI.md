# Flare Social Plugin API v1

This directory is the authoring contract for `.fpp` social plugins. The Host
implementation remains authoritative when this document and code disagree.

## Package

An API v1 package is a ZIP with a `.fpp` extension and exactly these files:

```text
manifest.json
plugin.js
assets/icon.png
locales/<bcp47>.json   # optional
```

`plugin.js` is one self-contained classic script. Static modules, dynamic
imports, remote code, filesystem access, and native access are unavailable.
Run `node pack.mjs <directory> [output.fpp]` to produce a deterministic package
using only Node's standard library.

## Registration

The script calls `definePlugin` once. It registers named services; it does not
implement a universal dispatcher:

```js
definePlugin({
  detector: { async detect(request, context) {} },
  login: {
    oauth: {
      async begin(request, context) {},
      async resume(request, context) {},
    },
  },
  capabilities: {
    timeline: { async page(request, context) {} },
    post: {
      async detail(request, context) {},
      async mutate(request, context) {},
    },
  },
});
```

The manifest is the static upper bound. Every declared known operation must
have the corresponding function, or installation fails. Login returns the
subset supported by that account and instance. Unknown capability IDs remain
visible as compatibility warnings but are ignored by this Host.

Current method paths are:

| Capability | Service operations |
| --- | --- |
| `flare.datasource.timeline/v1` | `timeline.page` |
| `flare.datasource.search/v1` | `search.posts`, `profiles`, `discoverPosts`, `discoverProfiles`, `discoverHashtags` |
| `flare.datasource.profile/v1` | `profile.byId`, `byHandle`, `timeline`, `following`, `followers` |
| `flare.datasource.post/v1` | `post.detail`, `context`, `delete`, `mutate` |
| `flare.datasource.relation/v1` | `relation.state`, `mutate` |
| `flare.datasource.compose/v1` | `compose.publish` |
| `flare.datasource.notification/v1` | `notification.page`, `badge` |
| `flare.datasource.list/v1` | `list.page`, `detail`, `create`, `update`, `delete`, `timeline`, `members`, `memberships`, `addMember`, `removeMember` |
| `flare.datasource.direct-message/v1` | `directMessage.rooms`, `room`, `messages`, `send`, `delete`, `leave`, `create`, `badge`, `canSend` |

Additional Host-known capability IDs are defined in `PluginAbiV1.kt`. Use
`flare-plugin.d.ts` for the complete v1 JavaScript types.

Capabilities that map to a full Host interface must declare its required
operation set; installation rejects partial interfaces. Notification filters
(`all`, `mention`, `comment`, `like`) and relation actions (`follow`, `block`,
`mute`) are static arrays on their capability manifest declarations. They are
not asynchronous JavaScript methods because Flare needs them synchronously to
decide which controls to show.

## Invocation and state

Every method receives immutable request and context values. JavaScript globals
are disposable cache only. There are no install, update, enable, disable, or
uninstall lifecycle hooks.

Runtime scopes are detector attempt, login flow, account, and guest origin.
Calls in one Runtime serialize. A fatal JavaScript error, timeout, cancellation,
out-of-memory condition, or invalid Wire result retires that Runtime.

All values crossing the boundary are JSON Wire v1 DTOs. IDs use
`{ "id": "...", "host": "..." }`; page responses use `items`,
`olderCursor`, `newerCursor`, and `endReached`. Timestamps are ISO-8601 strings.
Rich text is `{ "format": "plain|html", "value": "..." }`; Flare sanitizes
HTML before rendering.

## Host API

The frozen `flare` object exposes:

- `flare.http.request`: JSON, text, form, and streaming multipart HTTP;
- `flare.credential.read/replace`: the current account credential only;
- `flare.crypto.randomHex/uuid/sha256`;
- `flare.locale.current`;
- `flare.error`: marks a typed error safe to cross the ABI.

Authentication is structured:

```js
await flare.http.request({
  url: context.origin + "/api/v1/account",
  authorization: { type: "bearer", token },
});
```

Do not set raw `Authorization`, `Cookie`, `Host`, or `Content-Length` headers.
Compose bytes never enter JavaScript. Forward the request's asset handle:

```js
await flare.http.request({
  method: "POST",
  url: context.origin + "/api/v1/media",
  body: {
    type: "multipart",
    parts: [{ type: "asset", name: "file", handle: asset.handle }],
  },
});
```

Detector calls may access only the candidate origin. Login calls may also use
exact HTTPS origins listed in `permissions.authOrigins`. Account and guest
calls use the verified account origin. Redirects stay on the current origin.

## Login

For OAuth, Flare owns `flowId`, state, callback validation, persistence,
expiry, replay protection, and relogin identity validation. `begin` returns:

```js
{
  type: "externalBrowser",
  url: authorizationUrl,
  pendingPayload: { /* bounded opaque JSON */ },
}
```

`resume` receives validated callback parameters and returns:

```js
{
  type: "success",
  value: {
    accountId,
    origin: context.origin,
    credential: { accessToken },
    profile,
    capabilities: { "flare.datasource.timeline/v1": ["page"] },
    composeConfig,
  },
}
```

At least one displayable capability must be negotiated. `accountId` must equal
`profile.key.id`, and `profile.key.host` must equal the selected origin's host.

## Errors

Only errors created with `flare.error` are recoverable plugin errors:

```js
throw flare.error({
  code: "RateLimited",
  message: { key: "error.rateLimited", fallback: "Try again later" },
  retryAfterSeconds: 30,
});
```

Codes are `AuthenticationRequired`, `NotFound`, `Validation`, `RateLimited`,
`Network`, `Remote`, `Unsupported`, `InvalidResponse`, and `Cancelled`.
Unmarked exceptions are fatal and retire the Runtime generation.

## Manifest text, icon, and Deep Links

Plugin-authored manifest text may be either a final string or:

```json
{ "key": "timeline.home", "fallback": "Home" }
```

Localized keys must exist in the default locale catalog. Resolution uses exact
BCP-47 tag, language-script, language, default locale, then fallback. The only
formatting feature in v1 is named `{argument}` substitution.

The PNG icon is the platform icon on every target. Other manifest icons use
Host semantic enum names. Deep Links, when needed, are manifest-only structured
rules; JavaScript cannot resolve them.

## Lifecycle and trust

Install, update, enable, disable, and uninstall modify Desired State and take
effect after restart. Local packages are always unverified. Repository trust is
Host-owned; a manifest cannot claim official status or broader runtime access.
