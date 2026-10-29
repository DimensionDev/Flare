# Pixelfed sample plugin

This sample implements Pixelfed through Flare's Social Plugin API v1. It is a
self-contained classic script and has no npm or runtime dependencies.

Implemented features:

- exact Pixelfed detection;
- dynamic OAuth client registration and login;
- Home, Discover, Local, Federated, Bookmarks, and Favourites timelines;
- profile lookup, Posts/Gallery tabs, following/followers, and search;
- post detail, context, delete, favourite, repost, and bookmark actions;
- media upload and status publishing.

Guest mode, lists, direct messages, notifications, and plugin Deep Links are
intentionally omitted. The manifest exposes only the implemented capabilities
and configuration. Custom plugin UI is unavailable in API v1.

The manifest explicitly requests `mediaNetwork` so avatars and federated/CDN
images can load. Installation review discloses that media destinations may
receive this plugin's account data.

## Develop locally in Desktop

In Settings > Plugins > Plugin development, select this directory and start
development. File edits are validated automatically; use **Reload plugin** to
apply them. See [local development](../../references/testing.md#develop-locally-in-desktop)
for account access, reload boundaries, and restoring the installed version.

## Publish on GitHub and install

Copy the contents of this directory to the root of your own public GitHub
repository. Publish a stable tag `v0.1.0` matching `manifest.version`, then install
the repository URL from Flare's plugin management. It becomes available immediately. The script
is already built; a GitHub Release or uploaded `.fpp` asset is unnecessary.

For a separately maintained fork, choose your own plugin ID and a distinct
platform ID before release. The sample's `Pixelfed` platform ID cannot coexist
with another registered platform of the same name. Keep IDs stable after users
install the fork. See [GitHub publication and updates](../../references/publishing.md).

This example cannot be installed by passing the Flare monorepo URL or a GitHub
subdirectory URL: each installation repository must contain one root plugin.
Actual server behavior may differ by Pixelfed version; report the instance and
checks performed when claiming live compatibility.

## Optional local package

From the skill directory (the installed `flare-plugin` directory, or
`feature/plugin/sdk/skill/flare-plugin` in a Flare checkout):

```sh
node pack.mjs examples/pixelfed /tmp/pixelfed-0.1.0.fpp
```

The output is deterministic: identical input bytes produce an identical `.fpp`.
Install it from Flare's Settings > Plugins page, then continue with
logging in. Local packages are unverified and should be reviewed before use.

The bundled icon comes from Pixelfed's official
[`brand-assets`](https://github.com/pixelfed/brand-assets) repository.

## Read the implementation

- `manifest.json`: static capabilities, timeline/profile tabs, compose defaults,
  localized labels, and explicit media permission.
- `plugin.js`: detection, OAuth registration/resume, structured HTTP, entity
  mapping, cursor pagination, actions, and multipart publication.
- `locales/`: English and Simplified Chinese catalogs. The SDK guides are English;
  these catalogs demonstrate plugin localization.

Use the [capability recipes](../../references/recipes.md) for features this sample
does not implement and [testing](../../references/testing.md) for evidence levels.

## Optional real-instance smoke test

Set `PIXELFED_TEST_HOST` and `PIXELFED_TEST_TOKEN` when running the JVM tests.
The test remains read-only unless `PIXELFED_TEST_ALLOW_WRITE=1` is also set.
