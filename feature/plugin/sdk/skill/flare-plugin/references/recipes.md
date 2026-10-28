# Capability recipes

Use the [capability matrix](capabilities.md) for required operation sets and
[type declarations](../flare-plugin.d.ts) for full signatures. Every method also
receives `context`. Tables below describe all twelve services. Code fragments
use site-specific helpers such as `api`, `mapPost`, and `mapProfile`; implement
them from the website contract and [HTTP rules](runtime-http.md), then place
the service in the single `definePlugin` registration. These are adaptation
recipes, not seventeen separate installable plugins.

## Timeline

`timeline.page(TimelinePageRequestV1)` returns `PageV1<PostV1>`. Declare a static
default timeline and explicit supported directions. Read `timelineId`,
`parameters`, and the nested `page`, route to the corresponding website endpoint,
and map its continuation to olderCursor/newerCursor. The
[template](../assets/github-template/plugin.js) implements refresh/older paging,
empty pages, credential reads, typed errors, and browser-link posts.

For multiple timelines, use stable IDs such as home/local/bookmarks and a bounded
switch over those IDs. Unknown IDs should produce Unsupported, not accidentally
load the default timeline. Dynamic IDs come from [tab catalog](#tab-catalog).
Test refresh, a second page, an empty continued page, and genuine exhaustion.

## Search

All five methods receive `SearchRequestV1 { query, page }`:

| Method | Return |
| --- | --- |
| posts | PageV1<PostV1> |
| profiles | PageV1<ProfileV1> |
| discoverPosts | PageV1<PostV1> |
| discoverProfiles | PageV1<ProfileV1> |
| discoverHashtags | PageV1<HashtagV1> |

Declare and implement all five to expose the current search interface. Map the
website's different search/discovery endpoints rather than forwarding a search
result envelope. Encode queries as URL parameters, and keep paging cursors
specific to the query and result type. Discovery may use an empty query; do not
invent a hidden required query that the Host cannot provide. See Pixelfed's
search service for complete examples. Test non-ASCII queries and empty results.

## Profile

| Method | Input | Return |
| --- | --- | --- |
| byId | key + optional entityToken | ProfileV1 |
| byHandle | handle, host | ProfileV1 |
| timeline | profile key, optional tabId, parameters, page | PageV1<PostV1> |
| following / followers | key, optional entityToken, page | PageV1<ProfileV1> |

byId/byHandle are required together; the other operations are optional. Preserve
the website's canonical identity while translating handles to IDs. byHandle's
host is lookup data, not permission to request an arbitrary origin. A profile
tab requires both a manifest profileTabs entry and the timeline operation;
`display: "Grid"` changes presentation, not the return type. Map media-only
tabs through parameters as Pixelfed does. Test equivalent handles, a missing
profile, and stable keys between login and lookup.

## Post and semantic actions

| Method | Input | Return |
| --- | --- | --- |
| detail | EntityRequestV1 | PostV1 |
| context | EntityPageRequestV1 | PageV1<PostV1> |
| delete | EntityRequestV1 | deleted or noChange |
| mutate | MutationRequestV1 | updatedPost, deleted, invalidate, or noChange |

Only detail is required. Map context into the site's meaningful thread order.
Attach supported semantic actions to returned posts:

```js
const actions = [{
  action: remote.favourited ? "Unfavourite" : "Favourite",
  active: remote.favourited,
  enabled: true,
  count: remote.favourites_count,
  actionToken: remote.action_token,
}];
```

`mutate` receives `{ key, action, actionToken?, parameters }`. Map the semantic
action to a verified endpoint, including the inverse action; do not send the
enum name as a URL without a documented mapping. It is distinct from `delete`.

```js
const postServiceFragment = {
  async mutate(request, context) {
    const updated = await mutateWebsitePost(request, context);
    return { type: "updatedPost", post: mapPost(updated, context) };
  },
};
```

Return updatedPost when the server supplies authoritative new state. Return
`{ type: "invalidate", keys: [request.key] }` when affected content must be
reloaded; `deleted` only after deletion, and `noChange` for a successful outcome
that supplies no replacement state. A remote failure should throw, enabling the
Host's failure behavior. Do not return updatedProfile from post.mutate. Updated
post keys must match the requested entity; invalidation keys must be unique.
Test action/inverse, disabled active state, failure, and views of the same entity.

## Relation

Declare state and mutate, plus static `relationActions`, for example:

```json
{
  "operations": { "state": {}, "mutate": {} },
  "relationActions": ["follow", "block", "mute"]
}
```

`state(EntityRequestV1)` returns RelationV1; `mutate(MutationRequestV1)` returns
updatedRelation or noChange. Supported action pairs are Follow/Unfollow,
Block/Unblock, Mute/Unmute. Return the **complete authoritative relation** after
a mutation, including any server-side side effects (blocking may also unfollow)
and replacement actionTokens. Missing relation booleans default false, so a
partial updatedRelation can clear real state.

```js
const result = {
  type: "updatedRelation",
  relation: {
    profileKey: request.key,
    following: server.following,
    followedBy: server.followed_by,
    blocking: server.blocking,
    muting: server.muting,
    actionTokens: server.next_action_tokens,
  },
};
```

Validate/map `server.next_action_tokens` to known semantic action names. Test all
declared action pairs and account isolation; do not maintain durable relation
state in plugin globals.

## Compose

Declare publish and conservative composeDefaults. `compose.publish` receives
text, visibility, languages, assets, sensitive, optional spoilerText/replyTo/poll,
and returns `{ post: PostV1 }`.

1. Translate supported fields to the website's payload. Reply targets are keys;
   do not confuse them with URLs. Poll duration is seconds, not milliseconds.
2. Upload assets using the Host multipart handle API. Check each result and use
   the returned remote media IDs. Honor alt text, MIME, size, and count constraints.
3. Publish and map the authoritative returned post. Handle partial upload failure
   without reporting that the post was published. Use the website's idempotency
   facility when available; do not blindly retry an ambiguous write.

See [multipart](runtime-http.md#body-recipes) and Pixelfed's complete publish
implementation. Only enable controls the adapter handles. Login/manifest config
merging is described in [compose defaults](manifest.md#compose-defaults).
Test text-only where allowed, required-media constraints, an upload failure,
alt text, reply, visibility, and resulting post navigation.

## Notification

`notification.page({ filterId?, page })` returns `PageV1<NotificationV1>`;
`badge({})` returns `{ value: nonnegativeCount }`. Each may be declared separately,
but page requires a nonempty static notificationFilters list.

Filter IDs are `all`, `mention`, `comment`, `like`. Map them to the site's API.
Result kinds instead use `Mention`, `Reply`, `Favourite`, `Repost`, `Follow`,
`Other`. Preserve the notification's ID, actor, timestamp, custom message, and
post/repost presentation. Two events referencing one post still need distinct
notification IDs. Test those events together and filters with empty pages.

## List

The Host requires all list operations:

| Method | Input | Return |
| --- | --- | --- |
| page | PageRequestV1 directly | PageV1<SocialListV1> |
| detail | EntityRequestV1 | SocialListV1 |
| create / update | `{ id?, title?, entityToken? }` | SocialListV1 |
| delete | Same mutation request | deleted or noChange |
| timeline | EntityPageRequestV1 | PageV1<PostV1> |
| members / memberships | EntityPageRequestV1 | PageV1<ProfileV1> / PageV1<SocialListV1> |
| addMember | `{ listId, profileKey, entityToken? }` | ProfileV1 |
| removeMember | Same member request | deleted or noChange |

For memberships, the entity is the profile whose memberships are requested;
for list detail/timeline/members it identifies the list. Keep list IDs stable
across creation, editing, and paging. Preserve entityToken where the website
needs revision data. Validate title and operation-specific optional fields;
an optional TypeScript property does not mean every operation can omit it.
Test create → add member → members/memberships → remove member → delete.

## Direct message

All methods are required:

| Method | Input | Return |
| --- | --- | --- |
| rooms | PageRequestV1 directly | PageV1<DirectMessageRoomV1> |
| room | EntityRequestV1 | DirectMessageRoomV1 |
| messages | roomKey, page, optional roomEntityToken | PageV1<DirectMessageV1> |
| send | roomKey, text, optional roomEntityToken | DirectMessageV1 |
| delete | roomKey, messageKey, optional roomEntityToken/messageEntityToken | deleted or noChange |
| leave | EntityRequestV1 | deleted or noChange |
| create | EntityRequestV1 identifying the other profile | DirectMessageRoomV1 |
| badge | Empty object | `{ value: count }` |
| canSend | EntityRequestV1 identifying the other profile | `{ value: boolean }` |

Populate participants, sender, roomKey, and fromCurrentAccount accurately.
Messages and a room's lastMessage must belong to that room. `send` in v1 takes
text, not an invented media-attachment array.

For `messages`, support newer polling only when implemented. Return newerCursor
even for empty successful polls if the server advances it. Keep this separate
from roomEntityToken and history olderCursor. Sending a message is not a new
polling cursor. Test two rooms with different cursors, empty newer pages, failed
polls, history loading, send/delete, and a denied canSend result.

## Article

Declare detail and comments. Return a timeline PostV1 with
`detailType: "article"` and a key that article.detail understands.
`article.detail(EntityRequestV1)` returns ArticleV1 with key/title/content;
`comments(EntityPageRequestV1)` returns PageV1<PostV1>.

Map full text to sanitized HTML/plain rich text, keep images in meaningful
content order, and request mediaNetwork for remote covers/HTML images. A site
with no comments can return `{ items: [], endReached: true }` when that genuinely
represents its content. Test the preview click, long content, relative HTML
URLs, comments, and an unavailable article. A browser-only link feed can instead
use detailType link without declaring article capability.

## Gallery

Declare detail, comments, and recommendations; preview posts use
`detailType: "gallery"`.

| Method | Input | Return |
| --- | --- | --- |
| detail | EntityRequestV1 | GalleryV1 |
| comments / recommendations | EntityPageRequestV1 | PageV1<PostV1> |

Gallery detail needs key/title/createdAt/url and 1..100 image media entries.
Orientation is horizontal or vertical (default vertical). Preserve image order,
dimensions, descriptions, and the gallery's action state. Bookmark behavior
additionally requires post mutation capability, a matching semantic descriptor,
and an account; gallery capability alone does not implement mutations. Preserve
the same entity key across preview, gallery, and mutation responses. Test grid
navigation, empty/no-comment results, image permissions, and bookmark refresh.

## Tab catalog

`tabCatalog.page(PageRequestV1)` returns `PageV1<TimelineSectionV1>`. Each section
contains `id`, WireText `title`, and a nested PageV1 of TimelineDescriptorV1.
Each descriptor has id/title/icon and optional display/parameters:

```js
const catalog = {
  items: [{
    id: "topics", title: { value: "Topics" },
    timelines: {
      items: [{ id: "photography", title: { value: "Photography" }, icon: "Featured", parameters: { topic: "photo" } }],
      endReached: true,
    },
  }],
  endReached: true,
};
```

The descriptor's ID/parameters reach timeline.page when selected; implement that
routing. A catalog still requires timeline.page and at least one static default
timeline. Do not promise an independent nested-catalog paging callback: v1
exposes only tabCatalog.page. Test selecting a descriptor and its resulting page.

## Guest access

Declare `platform.guest: { "enabled": true }` only if the declared read paths
work without credentials. Branch on context.scope/credentialAvailable when an
operation supports both account and guest calls. Guest requests stay at the
selected origin and have no account credential API. The declaration makes the
guest datasource available; it does not make the plugin a global default guest
platform or create a new login method. Test supported guest navigation in Flare,
including capability requirements for internal article/gallery/post details.

## Localization

Manifest text accepts a string or `{ "key": "timeline.home", "fallback": "Home" }`.
Runtime WireText instead accepts `{ "value": "Home" }` or a localized object
with key/fallback and optional `args`. Exactly one of value/key is required;
localized WireText requires a fallback. Arguments use finite numbers, booleans,
or strings with valid names; omit null arguments.

`locales/en.json` contains a flat key-to-string map:

```json
{ "timeline.home": "Home", "error.limited": "Try again in {seconds} seconds" }
```

Ensure manifest keys exist in the default catalog, and include runtime keys as
well for translated errors/titles. Resolution tries exact BCP-47 tag,
language-script, language, default locale, then fallback. Only named `{argument}`
substitution is supported; there is no plural/select message syntax. Test an
unavailable locale and a missing translation falling back to the default.

## Deep Links

Deep Links are manifest-only structured rules, not a JavaScript resolver:

```json
{
  "origin": "$accountOrigin",
  "path": [{ "type": "literal", "value": "posts" }, { "type": "capture", "name": "id" }],
  "target": { "type": "Post", "value": "{id}" }
}
```

Place the rule in `platform.deepLinks`. Origin must be `$accountOrigin` or a
declared auth origin. Paths contain 1..32 literal/capture segments with unique
capture names. Targets are `Profile`, `Post`, `Timeline`, `Browser`. Entity target
values reference captured values; the current validator allows only capture
substitutions, not arbitrary literal prefixes. Timeline target value is a
declared static timeline ID; Browser takes no value. No wildcard/regex/callback
field is available. Target capability and account/guest availability still
matter. Test matching/nonmatching paths and URI encoding with the actual Host;
detailType link explicitly bypasses this internal matching path.
