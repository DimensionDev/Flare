# Mixed timeline showcase

This offline plugin exercises the existing Post, Feed, User, UserList and Message
UI, including quotes/parents, two notifications about one post, a poll, a Unicode
reaction and follow-request buttons. No media/network permission is required.

Build with `flare-plugin build ./examples/timeline-showcase -o ./timeline.fpp`
from the SDK skill directory, import the archive in Flare, select an HTTPS origin
(e.g. `https://example.org`), and choose **Open demo account**. No remote account
or token is needed. This example makes no HTTP requests; the feed/card links open
only when tapped. State lives in one account's JavaScript runtime and resets on
runtime eviction; it is deliberately not a website backend or durable storage.

- Vote or toggle the reaction: `post.mutate` validates the action token and returns
  an authoritative `updatedPost`, including the replacement token.
- Follow the suggested user: `relation.mutate` returns `updatedProfile` with
  replacement labeled buttons. Normal profile controls use `updatedRelation`.
- Accept/reject the request: `deleted` removes that row, not the profile.
- Notifications have distinct `notificationKey` values but share post content.
- A stale post token fails; refresh to obtain the current snapshot. The Host
  restores failed optimistic votes and shows the error through its existing UI.

The [wire reference](../../references/wire-data.md#timeline-items-and-interactions)
includes image reactions, Ugoira and all validation limits. Those require real
remote media and are intentionally absent from this offline sample.
