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

The same defaults apply inside presentation posts, notifications, and `updatedPost` results.
An updated post is a complete snapshot: omitting author or visibility clears that
value rather than retaining it from the cached post.

## Text and media

Rich text is `{ format: "plain" | "html", value }`; omitted format means plain.
Flare sanitizes HTML. Relative HTML links/resources resolve against the selected
instance; URLs must resolve to HTTPS without credentials. Scripts and local/
data/content resources are not a way to embed a custom UI. Normal DTO URL fields
should be absolute HTTPS. `detailType: "link"` always requires such a URL.

Media requires `{ id, type, url }`, with type `image`, `video`, `gif`, `audio`, or `ugoira`.
Optional fields are previewUrl, description (alt text), width, height, and
durationMillis. Preserve accurate dimensions/duration and accessibility text.
Remote media needs the manifest's mediaNetwork permission, including profile
avatars, article covers, and images inside HTML. Returning text successfully does
not prove media permission is correct.

`presentation.repost` holds the original PostV1 with its own key and detailType; do not
overwrite it with the wrapper ID. `replyTo` is an EntityKey, not a full post.
Spoiler text, sensitive flag, visibility, and action state should reflect the
website. Presentation nesting is bounded to four levels; see [limits](troubleshooting.md#limits).

## Timeline items and interactions

Every timeline-producing operation returns `PageV1<TimelineItemV1>`, including
search posts, profile/list timelines, context, comments, recommendations and
notifications. Each item has a required `type`: `post`, `feed`, `user`, `userList`,
or `message`. Bare PostV1 and the old notification envelope are rejected. This
preview intentionally has no old-protocol compatibility.

```js
return { items: [{ type: "post", post }], endReached: true };
```

`post.detail` returns `{ post, presentation? }`, the same post body without the
list discriminator. Publish and updatedPost still return PostV1 entities. Put
reposts, quotes, inlineParents and notification messages in PostPresentationV1.
Inline parents are `{ post, presentation? }` bodies, without `type`. Do not put
`repost` inside PostV1. At most 100 parents and 100 quotes per presentation, four
nested parent levels, and 200 top-level items are accepted.
The host displays nested parents as one ancestor-first chain, keeping each post once.

| Item | Data |
| --- | --- |
| post | PostV1 plus optional presentation: message, inlineParents, quotes, repost, notificationKey |
| feed | HTTPS url, createdAt, source; optional title, rich description, image, sourceLanguages, target |
| user | Stable row key, createdAt, profile; optional message and buttons |
| userList | Stable row key, createdAt, 1..100 profiles; optional message and post |
| message | Stable key, createdAt, optional actor/icon/target; exactly one of text, kind, unknownType |

The Host namespaces user/userList/message row keys by plugin and type. These row
keys are independent of the profile/post entity keys passed to mutations.
Notifications about the same post use distinct presentation.notificationKey
values; their content stays shared. Feed identity uses its URL, so repeated feed
URLs represent the same feed entry. Gap rows, account identity, media proxies,
translation state, render hashes and callbacks remain Host-owned.

Navigation targets are `{ type: "post" | "profile" | "article" | "gallery", key }`
or `{ type: "link", url }`. Internal targets require the negotiated capability;
unavailable targets become inert. Feed defaults to opening its URL in the browser.
Message defaults to its actor's profile. There is no arbitrary deeplink/callback
field. Labels on user buttons are required and should use context.locale.

Posts additionally accept card, sourceLanguages, sourceChannel, replyToHandle,
references, poll, emojiReactions, and channel visibility. Counts are nonnegative;
media in cards, feeds and image reactions uses the same mediaNetwork checks as
post images. Unicode reactions need no network permission. Media may specify a
video downloadUrl. URLs must be credential-free HTTPS, including nested content.

Polls have 2..100 options, zero-based ownVotes, multiple, optional expiresAt,
enabled and actionToken. The Host calculates percentages and renders its normal
poll control. Voting calls post.mutate with action `Vote`; parameters.options is
a JSON array string, e.g. `"[0,2]"`. Validate selections against the website's poll.
Return updatedPost with authoritative counts/ownVotes. Guests, expired/voted polls
and sessions without post.mutate cannot vote. Failed votes restore the cached
snapshot through the existing Host event handler.

Emoji reactions contain name, count, isUnicode, me, enabled, optional image URL
and actionToken. Tapping calls post.mutate with `React` or `Unreact` and
parameters.reaction. Return updatedPost to replace both state and action tokens.

User buttons support Follow/Unfollow/Block/Unblock/Mute/Unmute and
AcceptFollowRequest/RejectFollowRequest. They call relation.mutate with the actual
profile key and the button's actionToken. Return updatedProfile with replacement
ProfileV1.actions (including labels) to refresh both profile and buttons. Include
the optional complete relation snapshot to update profile relation controls too;
its profileKey must match the updated profile. Return deleted to remove the acted-on row after accepting/rejecting a request. An
updatedRelation refreshes the relation cache and disables the old row buttons
until reload, since that result supplies no replacement labels/tokens. Normal
profile relation controls still accept only updatedRelation/noChange. A failure
must throw and must not be represented as a successful noChange.

Ugoira requires an authenticated account, mediaNetwork, gallery capability and
its optional `ugoiraMetadata` operation. Return media with type ugoira, ugoiraKey,
HTTPS url and previewUrl, and optional originalFrameUrl. The Host calls
`gallery.ugoiraMetadata({ key })` for `{ zipUrl, frames: [{ file, delayMillis }] }`
and uses the existing playback/export UI. Frame names must be simple image
filenames, unique within the ZIP; 1..10,000 frames, 1..60,000 ms per frame and at
most one hour total are accepted. This does not add guest Ugoira playback to the
existing account-based player.

The [timeline showcase](../examples/timeline-showcase/README.md) demonstrates
mixed items, notifications, poll/reaction mutations and follow-request buttons.

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
| RelationV1 | profileKey; following/followedBy/blocking/muting default false; actionTokens map semantic actions to opaque strings |
| SocialListV1 | id, title; optional memberCount/entityToken |
| DirectMessageRoomV1 | key, title, participants; lastMessage must belong to this room |
| DirectMessageV1 | key, roomKey, sender, createdAt, content; fromCurrentAccount defaults false |
| ArticleV1 | key, title, content; optional author/createdAt/url/coverUrl |
| GalleryV1 | key, title, createdAt, url, nonempty images; all MediaV1 types are supported; orientation defaults vertical |
| TimelineDescriptorV1 | id, title as WireText, icon; display defaults List; optional string parameters |
| TimelineSectionV1 | id, title as WireText, timelines as PageV1 of descriptors |

Gallery orientations are `horizontal`/`vertical`. These enum spellings intentionally
differ from manifest notification filter IDs and List/Grid display values.
See [recipes](recipes.md) for operation-specific result requirements and mutations.
