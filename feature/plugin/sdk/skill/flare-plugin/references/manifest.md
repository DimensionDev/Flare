# Manifest reference

`manifest.json` is UTF-8, strict JSON. Unknown fields, comments, and trailing
commas are rejected. Property names and enum spellings are case-sensitive.
Defaults below apply when a field is omitted; use omission for optional fields
rather than sending null for non-nullable values. Start from the complete
[template manifest](../assets/github-template/manifest.json).

## JSON Schema and editor support

The self-contained [manifest.schema.json](../manifest.schema.json) uses JSON Schema
draft-07. Editors such as VS Code use it for field completion, descriptions, enum
choices, required properties, ranges and static login/capability combinations.
`npx flare-plugin-sdk@next init my-plugin` adds a `$schema` URL pinned to that SDK
version. The source templates use the npm `next` channel.

For an existing project, add this editor hint at the manifest root:

```json
{
  "$schema": "https://unpkg.com/flare-plugin-sdk@next/skill/flare-plugin/manifest.schema.json"
}
```

Keep the other manifest fields. For reproducible editor behavior, replace `next`
with your chosen SDK version. For offline authoring, install the SDK as a development
dependency and use `./node_modules/flare-plugin-sdk/skill/flare-plugin/manifest.schema.json`
as the `$schema` value; all schema references are local to that file.

Flare accepts `$schema` as optional editor metadata and never fetches or executes
it. Older Flare builds that reject it can still use editor assistance: remove
`$schema` from the manifest and associate the schema in VS Code workspace settings
(`.vscode/settings.json`):

```json
{
  "json.schemas": [{
    "fileMatch": ["/manifest.json"],
    "url": "https://unpkg.com/flare-plugin-sdk@next/skill/flare-plugin/manifest.schema.json"
  }]
}
```

Schema validation does not fill defaults or replace Host validation. Flare also
checks unique IDs/interactions, canonical/approved origins, captured Deep Link
values, referenced timelines, localized catalog keys, relational compose limits,
actual JavaScript registration and runtime behavior. Some text limits use UTF-16
length in the Host rather than JSON Schema's Unicode code point count.

## Root object

| Field | Required / default | Contract |
| --- | --- | --- |
| `$schema` | Omitted | Editor schema URL/path only; never loaded by Flare |
| `schemaVersion` | `1` | Manifest schema, currently exactly 1 |
| `apiVersion` | Required | Runtime ABI, currently exactly 1 |
| `id` | Required | Stable reverse-domain plugin ID; `[a-z][a-z0-9]*(?:\.[a-z][a-z0-9-]*)+` |
| `version` | Required | SemVer; use stable `major.minor.patch` for GitHub distribution |
| `defaultLocale` | `"en"` | BCP-47 locale tag |
| `name` | Required | Manifest text |
| `description` | Omitted | Manifest text |
| `permissions` | `{}` | Permission object below |
| `platform` | Required | Exactly one platform object |

Manifest text is either a nonblank string or `{ "key": "plugin.name", "fallback":
"Example" }`. Every localized key used by the manifest must exist in the default
locale catalog. Plain strings need no locale file. This encoding differs from
runtime `WireTextV1`; see [localization](recipes.md#localization).

## Permissions

| Field | Default | Meaning |
| --- | --- | --- |
| `authOrigins` | `[]` | Up to 16 additional exact HTTPS origins for login, e.g. `https://login.example.org` |
| `mediaNetwork` | `false` | Allows automatic remote media loading, including avatars, HTML images, audio/video, and covers |

Auth origins have no paths, trailing slash, query, fragment, wildcard, or URL
credentials. Use normalized host casing and omit the default `:443` port.
They do not grant account/guest HTTP access to extra origins. Media permission
allows destinations across websites, including CDNs/federated servers; it does
not expand the HTTP API. See [origin rules](runtime-http.md#origins-and-permissions).

## Platform object

| Field | Required / default | Contract |
| --- | --- | --- |
| `id` | Required | Stable platform ID, `[A-Za-z][A-Za-z0-9_.-]{0,63}`; avoid built-in and other plugin platform IDs |
| `name`, `description` | Name required | Manifest text |
| `detector` | Omitted | `{ "priority": 0 }`; priority -10000..10000, requires `detector.detect` |
| `loginMethods` | `[]` | Login declarations below; method IDs unique and at most one method per interaction kind |
| `capabilities` | `{}` | Capability ID → declaration; up to 32 entries |
| `timelines` | `[]` | Static timeline declarations; timeline capability requires at least one |
| `profileTabs` | `[]` | Profile tab declarations; requires `profile.timeline` |
| `deepLinks` | `[]` | Up to 64 structured rules; see [Deep Links](recipes.md#deep-links) |
| `composeDefaults` | Omitted | Required conservative defaults when declaring `compose.publish` |
| `guest` | Omitted | `{ "enabled": true }` enables guest datasource access; object defaults enabled to true |
| `credentialSchemaVersion` | `1` | Integer 1..1000000; changing it requires existing accounts to log in again |
| `timelineSchemaVersion` | `1` | Integer 1..1000000; changes timeline identity; see [upgrades](publishing.md#updates-and-compatibility) |

Other identifiers (method, field, timeline, tab, parameter, capture) generally use
`[A-Za-z][A-Za-z0-9_.-]{0,127}`. Parameter objects have at most 32 string values,
each at most 4096 characters. Plugin/platform IDs have their separate rules above.

### Capabilities

```json
{
  "flare.datasource.timeline/v1": {
    "operations": { "page": { "directions": ["refresh", "older"] } }
  }
}
```

An operation configuration is an object, not a function name or boolean.
`directions` defaults to `[]`, which the current loader treats as unrestricted;
declare the actually supported directions explicitly. Non-paging operations use
`{}`. The [capability matrix](capabilities.md) defines exact names and required
sets. Known capability declarations with missing methods/operations fail
installation; unknown capability IDs produce warnings and are ignored.

`notificationFilters` belongs on the notification capability declaration and
must be nonempty when `page` is declared. Values: `all`, `mention`, `comment`,
`like`. `relationActions` belongs on the relation declaration and must be
nonempty. Values: `follow`, `block`, `mute`. Both default to empty and are static
arrays; placing them on another capability is invalid.

### Timelines and profile tabs

| Field | Timeline default | Profile-tab default |
| --- | --- | --- |
| `id`, `title` | Required ID and manifest text | Required ID and manifest text |
| `icon` | `"Home"` | `"Profile"` |
| `display` | `"List"` | `"List"` |
| `parameters` | `{}` | `{}` |
| `defaultForNewAccount` | `false` | Not a profile-tab field |

IDs must be unique within each list. At least one declared timeline must have
`defaultForNewAccount: true`. `display` is `List` or `Grid`, not lowercase.
`tabCatalog.page` still requires a static timeline and `timeline.page`.

Icons use these Host semantic names:
`Home`, `Notification`, `Search`, `Profile`, `Local`, `World`, `Featured`,
`Bookmark`, `Heart`, `List`, `Messages`, `Channel`, `Like`, `Repost`, `Reply`,
`Delete`, `Follow`, `Block`, `Mute`, `Info`. The platform icon itself is the
package's `assets/icon.<extension>` (PNG, JPEG, WebP, GIF or ICO), not one of these names.

#### Default and manually added tabs

When a new account with timeline access is added, Flare adds every timeline with
`defaultForNewAccount: true` in manifest order. The first default is also the
account's main timeline. Multiple defaults are supported; other static timelines
and dynamic `tabCatalog.page` entries remain available for manual addition.

Default tabs follow the same presentation convention as built-in platforms on
Android, iOS, native macOS and Compose Desktop:

| Where the tab appears | Title | Icon |
| --- | --- | --- |
| First automatically added default | Localized `platform.name` | Packaged platform icon |
| Other automatically added defaults | Declared timeline `title` | Packaged platform icon |
| Default timeline in the manual-add list | Declared timeline `title` | Packaged platform icon |
| Other timelines in the manual-add list | Declared timeline `title` | Declared timeline `icon` |

Keep functional titles such as Home, Discover or Bookmarks in the timeline
declarations; Flare supplies the platform name for the first automatic tab.
The declared semantic `icon` is not used for default timelines in these lists.
Timeline IDs, parameters and `List`/`Grid` presentation are unchanged by these rules.
Profile tabs continue to use their own declared titles and icons.

Automatic addition happens only when creating an account. Signing in again or
updating/reloading a plugin does not recreate existing tabs or overwrite saved
names, icons, ordering or user customizations. Updated declarations apply to
newly added tabs.

### Login methods

Each method has required `id`, `interaction`, `title`; optional `description`;
`fields` defaults to `[]`; `cookie` is omitted except for WebCookie.

| Interaction | Required registration | Configuration |
| --- | --- | --- |
| `OAuth` | `login.<id>.begin`, `.resume` | No fields or Cookie configuration |
| `Password` | `login.<id>.begin` | At least one field |
| `CredentialImport` | `login.<id>.begin` | At least one field |
| `Form` | `login.<id>.begin` | At least one field |
| `WebCookie` | `login.<id>.begin`, `.check` | Cookie configuration; no fields |

Fields require `id`, `type`, `label`; `placeholder` is optional and `required`
defaults to true. Label/placeholder use manifest text. Field types are `Text`,
`Username`, `Password`, `Otp`, `Secret`. These select Host controls; the plugin
still validates credentials against the website. The form Host accepts at most
32 submitted fields, each up to 16384 characters, with only declared field IDs.

Cookie configuration contains `startUrl` and `probes`. Each probe has `url` and
`cookies: [{ "name": "session", "required": true }]`. There must be 1..16
probes and 1..32 requirements per probe, without duplicate Cookie names within a
probe. Cookie start/probe URLs use an approved HTTPS origin, or the special
`$accountOrigin` prefix. That prefix is resolved in Cookie and Deep Link manifest
configuration; it is not a general string interpolation facility for plugin code.
See the [complete login recipes](authentication.md).

### Compose defaults

| Field | Shape and defaults |
| --- | --- |
| `text` | Optional `{ "maxLength": 500 }`; limit 1..1000000 |
| `media` | Optional object; required `maxCount` (0..100), `maxBytes` (1..1073741824) |
| `media.minCountForNew` | Default 0, between 0 and maxCount |
| `media.supportedMimeTypes` | Default `[]`; up to 128 MIME patterns, including `image/*` |
| `media.altTextMaxLength` | Default 1500, range 0..100000 |
| `media.canSensitive` | Default true |
| `visibility` | Optional `{ "allowed": ["public"], "default": "public" }`; default must be allowed |
| `contentWarning` | Default false |
| `poll` | Optional `{ "maxOptions": 4 }`, range 2..100 |
| `language` | Optional `{ "maxCount": 1 }`, range 1..32 |

Visibility values are `public`, `unlisted`, `followers`, `direct`. Describe what
the website actually supports. Login may return an instance config. When both sides define a section, the Host
uses the stricter limits, intersects visibility/MIME constraints, and rejects
empty intersections. When only one side defines an optional section, that section
is retained; omission is not a prohibition. Content-warning support requires
both sides when both configs exist. Implement every resulting enabled field in
`compose.publish`; see [compose](recipes.md#compose).

## Frequent invalid combinations

- Timeline capability without a static/default timeline.
- A profile tab without the profile timeline operation.
- Compose capability without compose defaults, or defaults without the capability.
- Only `search.posts` declared when the search Host interface requires all five operations.
- A login method ID that differs from the corresponding `login` object key.
- Localized manifest keys absent from the default catalog.
- Extra root properties such as `author`, `homepage`, `entry`, or a trust badge:
  API v1 defines no such manifest fields; put human-facing information in README.
