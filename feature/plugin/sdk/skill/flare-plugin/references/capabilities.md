# Capability and UI contract

Each ID below has prefix `flare.datasource.` and suffix `/v1`; use the full ID
in the manifest and negotiated login result. JavaScript methods live under
`defineFlareSocialPlugin({ capabilities: { <service>: ... } })`.

| ID middle | Service | Required operations | Additional operations | Host surface / dependency |
| --- | --- | --- | --- | --- |
| `timeline` | `timeline` | `page` | None | Static timelines, including at least one default |
| `search` | `search` | `posts`, `profiles`, `discoverPosts`, `discoverProfiles`, `discoverHashtags` | None | Search/discovery sections |
| `profile` | `profile` | `byId`, `byHandle` | `timeline`, `following`, `followers` | Profile lookup; tabs additionally require `timeline` and profileTabs |
| `post` | `post` | `detail` | `context`, `delete`, `mutate` | Internal post detail, thread context, and supported actions |
| `relation` | `relation` | `state`, `mutate` | None | Follow/block/mute controls; nonempty static relationActions |
| `compose` | `compose` | `publish` | None | Composer; requires composeDefaults and an account |
| `notification` | `notification` | At least one of `page`, `badge` | Both may be declared | `page` supplies the list and requires notificationFilters; badge alone is not a displayable login capability |
| `list` | `list` | `page`, `detail`, `create`, `update`, `delete`, `timeline`, `members`, `memberships`, `addMember`, `removeMember` | None | List browsing and management |
| `direct-message` | `directMessage` | `rooms`, `room`, `messages`, `send`, `delete`, `leave`, `create`, `badge`, `canSend` | None | DM rooms, conversation, and related account controls |
| `article` | `article` | `detail`, `comments` | None | Article detail when a post selects article navigation |
| `gallery` | `gallery` | `detail`, `comments`, `recommendations` | None | Gallery detail when a post selects gallery navigation |
| `tab-catalog` | `tabCatalog` | `page` | None | Dynamic timeline catalog; also needs timeline.page and a static/default timeline |

These sets come from the Host's `PluginAbiV1` contract, not from TypeScript
`Partial<Service>` declarations. Known capability operations must be implemented
and declared together. A website without a required operation may need a smaller
feature set; do not advertise a successful mutation that does nothing. A genuine
empty comments collection may return an empty ended page; an unsupported remote
operation should be explained and omitted where the Host allows omission.

## Declaration, registration, negotiation

For the same timeline, these three shapes are intentionally different:

```json
{ "flare.datasource.timeline/v1": { "operations": { "page": { "directions": ["refresh", "older"] } } } }
```

```js
// Registration fragment: include inside the single defineFlareSocialPlugin call.
const timelineService = {
  async page(request, context) {
    return loadTimeline(request.timelineId, request.page, context);
  },
};
```

```json
{ "flare.datasource.timeline/v1": ["page"] }
```

The first is `platform.capabilities`, the second is `capabilities.timeline`
(with a website-specific `loadTimeline` implementation), and the third is the
login success's `capabilities`. On login, negotiate only declared, complete sets
that this account/instance supports. At least one displayable capability is
required: compose/relation alone and a notification badge alone do not qualify.
Guest access uses the enabled guest declaration and manifest capabilities instead
of account login negotiation. See [upgrade negotiation](publishing.md#updates-and-compatibility)
for how an existing account adopts newly declared operations after an update.

## Entry points and actions

The Host builds UI from capabilities and static configuration. Implementing an
undeclared function does not add a UI entry. A static timeline ID is passed as
`request.timelineId`; profile tabs pass `request.tabId`. Names and parameters are
not network URLs unless your implementation explicitly interprets them that way.

Post actions additionally depend on their `ActionDescriptorV1`, enabled state,
account, and mutation capability. Set accurate `active`/`count` values even when
an action is disabled. The Host supplies layout and controls; plugins cannot
install arbitrary UI or arbitrary action kinds.

| `PostV1.detailType` | Click behavior | Required target |
| --- | --- | --- |
| Omitted or `post` | Internal post detail | post.detail |
| `article` | Internal article detail | article.detail + comments |
| `gallery` | Internal gallery detail | gallery.detail + comments + recommendations |
| `link` | Browser according to user settings | Absolute HTTPS `url`, no internal-detail capability needed |

Missing internal target capability makes that click unavailable; it does not
fall back to another detail type. `link` bypasses internal Deep Link matching.
Grid covers use the same article/gallery/link navigation; author/actions and
reposted targets retain their own behavior.

The login profile remains the current account's saved identity if profile lookup
is absent. It does not enable arbitrary profile pages, profile timelines, or
lookup methods. Use this for a minimal read-only feed such as the template.

See [recipes](recipes.md) for every service's inputs, result shape, and adaptation
points, and [the type declarations](../flare-plugin.d.ts) for full signatures.
