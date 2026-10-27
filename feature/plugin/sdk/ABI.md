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

## GitHub installation and updates

Flare can install from a public `https://github.com/<owner>/<repository>` URL
(an optional `.git` suffix is accepted). Each repository contains one plugin at
its root: `manifest.json`, the self-contained, already-built `plugin.js`,
`assets/icon.png`, and optional `locales/*.json`. Other development files are
ignored. Flare does not run package managers, build scripts, or repository code
outside the existing plugin sandbox.

Publish a stable SemVer tag such as `1.2.3` or `v1.2.3`; removing the optional
lowercase `v` must exactly match `manifest.version`. Pre-release and arbitrary
tags are excluded from the version picker and update checks. A GitHub Release
or uploaded `.fpp` asset is not required.

The Host lists tags, compares semantic versions, resolves the selected commit,
and downloads its source ZIP by commit SHA. It imports the plugin files into a
canonical `.fpp` and applies the same package, registration, compatibility, and
permission checks as local installation. The repository archive is limited to
20 MiB compressed, 50 MiB uncompressed, and 4,096 entries; existing per-file and
plugin package limits still apply. Traversal paths, symlinks, duplicate paths,
and multiple archive roots are rejected, including in ignored files.

GitHub installation records the repository ID, canonical owner/name, tag, and
commit SHA. Updates must retain that repository ID, plugin ID, and platform ID.
Checks only offer updates; downloading and confirming the existing install
review remain separate user actions, and changes take effect after restart.
New permissions, source changes, and a tag pointing to a different commit are
shown in the review. Cancellation or failure leaves the installed plugin intact.

Opening plugin management checks for updates using a five-minute metadata cache;
manual checks bypass it. Checks include all tag pages up to 1,000 tags and report
an error rather than claim a result from an incomplete list. GitHub requests use
the global proxy policy, without account credentials, and respect rate-limit retry
headers. Private repositories,
branch tracking, client-side builds, and automatic installation are unsupported.

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

HTML resource and link URLs must use HTTPS without URL credentials. Relative
URLs resolve against the selected instance. Local files, `content:`, `data:`,
`javascript:`, and other schemes are rejected before rendering.

## Post detail navigation

Each `PostV1` may set `detailType` to `"post"`, `"article"`, or `"gallery"`.
Omitting it defaults to `"post"`.
For example, a timeline may return an article preview with `detailType: "article"`
alongside regular posts and gallery previews. The selected detail receives that
post's `key` and uses the same account or guest origin. Reposted content keeps
its own `detailType`.

The target capability must be available for that account (or declared for guest
access), including its required operations: `post.detail`; `article.detail` and
`comments`; or `gallery.detail`, `comments`, and `recommendations`. Otherwise the
post click does nothing; it does not fall back to another detail type. In media
grids, article and gallery previews use the same detail click target.
Unknown or null `detailType` values are invalid Wire results.

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

Automatic media loading is a separate permission, disabled by default. A plugin
that returns avatars, images, audio, video, covers, or HTML images must declare
`"permissions": { "mediaNetwork": true }`. Installation review explicitly grants
media requests to any website, including CDNs and federated servers; these URLs
can transmit the plugin account's data. Adding this permission during an update
requires confirmation. It does not expand `flare.http.request` origins or permit
local-file resources. Text-only plugins do not need it.

Flare owns HTTP routing: detector, guest, and new-login requests follow the
global proxy policy; account requests and relogin requests follow that account's
policy, including OAuth resumed after restart. Plugins cannot select a proxy.
Proxy failures never fall back to direct connections, and changing a route may
cancel its in-flight requests, including uploads.

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
The Host retains this login profile for compose account selection when a plugin
does not provide profile lookup. Login cannot replace an existing account owned
by another platform with the same account ID and host.

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
