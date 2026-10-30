# Wire data and pagination

All boundary values are JSON. The exact signatures are in
[flare-plugin.d.ts](../flare-plugin.d.ts). Build DTOs explicitly: returning an
entire website response introduces unknown fields, and the Host decoder is
strict. Non-finite numbers, cycles, BigInt, functions, and undefined return values
are not valid results. Optional nullable fields may be omitted; defaulted
non-null fields must not be explicitly null. A missing field with no default is
an error. Preserve string IDs; parsing a large numeric ID into a JavaScript
Number can already lose precision before mapping.

## Identity and a minimal post

`EntityKeyV1` is `{ id, host }`: a stable website identifier and an HTTPS authority
without a scheme/path, with a non-default port when applicable. Use the selected
instance/API authority for IDs in its namespace. Do not substitute a display
name, page position, timestamp, random UUID, or cursor as an entity key.
Federated content may have a public URL on a different website while its ID is
still local to the selected API. Profile identity at login must match that origin.
The Host canonicalizes a profile's host to the selected origin when they differ
only by case. A notification without a post uses its mapped actor's profile
target, so tapping its row or avatar uses the same account key. Profile navigation
remains unavailable when the session does not expose that capability.

Example website response (illustrative):

```json
{
  "id": "post-17",
  "author": { "id": "alice", "username": "alice", "name": "Alice" },
  "created_at": "2026-01-01T00:00:00Z",
  "text": "Hello",
  "url": "https://example.org/posts/post-17"
}
```

Explicit mapping, assuming the fields have been validated:

```js
const host = context.origin.slice("https://".length);
const post = {
  key: { id: remote.id, host },
  author: {
    key: { id: remote.author.id, host },
    handle: remote.author.username,
    displayName: remote.author.name,
  },
  createdAt: remote.created_at,
  content: { format: "plain", value: remote.text },
  detailType: "link",
  url: remote.url,
};
```

Use ISO-8601 instants with a timezone, not epoch numbers or locale-formatted dates.
`ProfileV1` requires key/handle/displayName; `PostV1` requires key/createdAt/content.
Post author and visibility default to null and may be omitted or explicitly null.
When an author is provided, it must be a complete valid ProfileV1. The Host does
not synthesize an author or infer public visibility. Most collections default
empty and booleans default false. Counts are nonnegative. Do not include unknown
optional website fields by spreading objects.
See [navigation](capabilities.md#entry-points-and-actions) before choosing detailType.

A minimal post without author or visibility is:

```json
{
  "key": { "id": "post-17", "host": "example.org" },
  "createdAt": "2026-01-01T00:00:00Z",
  "content": { "value": "Hello" }
}
```

The same defaults apply inside `repost`, notifications, and `updatedPost` results.
An updated post is a complete snapshot: omitting author or visibility clears that
value rather than retaining it from the cached post.

## Text and media

Rich text is `{ format: "plain" | "html", value }`; omitted format means plain.
Flare sanitizes HTML. Relative HTML links/resources resolve against the selected
instance; URLs must resolve to HTTPS without credentials. Scripts and local/
data/content resources are not a way to embed a custom UI. Normal DTO URL fields
should be absolute HTTPS. `detailType: "link"` always requires such a URL.

Media requires `{ id, type, url }`, with type `image`, `video`, `gif`, or `audio`.
Optional fields are previewUrl, description (alt text), width, height, and
durationMillis. Preserve accurate dimensions/duration and accessibility text.
Remote media needs the manifest's mediaNetwork permission, including profile
avatars, article covers, and images inside HTML. Returning text successfully does
not prove media permission is correct.

`repost` holds the nested original PostV1 with its own key and detailType; do not
overwrite it with the wrapper ID. `replyTo` is an EntityKey, not a full post.
Spoiler text, sensitive flag, visibility, and action state should reflect the
website. Post nesting is bounded; see [limits](troubleshooting.md#limits).

## Pages

Most operations receive `request.page`; some, such as list.page and tabCatalog.page,
receive PageRequestV1 directly. Consult the signature before destructuring.

```json
{ "direction": "older", "limit": 20, "cursor": "opaque-page-2", "parameters": {} }
```

Return a PageV1, not the website's pagination envelope:

```json
{ "items": [], "olderCursor": "opaque-page-3", "newerCursor": "latest-marker", "endReached": false }
```

| Direction | Expected behavior |
| --- | --- |
| refresh | Fetch the initial/current page; the Host supplies no old cursor |
| older | Continue the older/history side using the supplied cursor |
| newer | Fetch newer items using the newer-side cursor, if declared/supported |

The Host maps olderCursor to append and newerCursor to prepend. `endReached`
suppresses the older cursor; it does not erase newerCursor. Return stable ordering
consistent with the selected operation and site API. If an API returns newest
first, keep that convention across pages. Respect the requested limit and map
website continuation tokens/Link headers/offsets explicitly. A cursor may encode
an offset or multiple server values, but it is bounded and opaque to the Host.

An empty page need not be the end: filtering or long polling may return no items
and still advance a cursor. Report actual server exhaustion, not merely
`items.length === 0`. Do not restart at page one when an older cursor is present.
Only declare directions you implement; an empty manifest direction set currently
allows all directions. Keep parameter semantics stable across refresh and paging.

## Three different tokens

| Value | Purpose | Lifecycle |
| --- | --- | --- |
| `olderCursor` / `newerCursor` | Continue a page stream | Returned page → later paging request |
| `entityToken` | Opaque context for an entity lookup/operation | Returned entity → entity request; room and message tokens have distinct fields |
| `actionToken` | Opaque website data needed for one semantic mutation | Action descriptor → MutationRequestV1; relation uses actionTokens keyed by action |

Never use an entity token as a DM polling cursor. Room `messages` polling uses
the page's newerCursor and can advance after an empty successful page. Opaque
tokens are serialized/cacheable operation data, not a secret store. Keep account
credentials in `flare.credential`, and do not encode URLs that bypass origin checks.

## Other entities

| DTO | Required fields / notable semantics |
| --- | --- |
| HashtagV1 | name; optional HTTPS url |
| NotificationV1 | id, createdAt, kind; optional actor/post/message. Identity belongs to the notification, not the referenced post |
| RelationV1 | profileKey; following/followedBy/blocking/muting default false; actionTokens map semantic actions to opaque strings |
| SocialListV1 | id, title; optional memberCount/entityToken |
| DirectMessageRoomV1 | key, title, participants; lastMessage must belong to this room |
| DirectMessageV1 | key, roomKey, sender, createdAt, content; fromCurrentAccount defaults false |
| ArticleV1 | key, title, content; optional author/createdAt/url/coverUrl |
| GalleryV1 | key, title, createdAt, url, nonempty images; all gallery media must be images; orientation defaults vertical |
| TimelineDescriptorV1 | id, title as WireText, icon; display defaults List; optional string parameters |
| TimelineSectionV1 | id, title as WireText, timelines as PageV1 of descriptors |

Notification kinds are `Mention`, `Reply`, `Favourite`, `Repost`, `Follow`, `Other`.
Gallery orientations are `horizontal`/`vertical`. These enum spellings intentionally
differ from manifest notification filter IDs and List/Grid display values.
See [recipes](recipes.md) for operation-specific result requirements and mutations.
