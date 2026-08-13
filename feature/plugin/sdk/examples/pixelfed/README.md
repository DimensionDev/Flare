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

Guest mode, lists, direct messages, notifications, custom UI, and plugin Deep
Links are intentionally omitted. Flare hides those entries because the
manifest does not declare their capabilities.

## Build

From the repository root:

```sh
node feature/plugin/sdk/pack.mjs \
  feature/plugin/sdk/examples/pixelfed \
  feature/plugin/build/dev.dimension.flare.sample.pixelfed-0.1.0.fpp
```

The output is deterministic: identical input bytes produce an identical `.fpp`.
Install it from Flare's Settings > Plugins page, then restart Flare before
logging in. Local packages are unverified and should be reviewed before use.

The bundled icon comes from Pixelfed's official
[`brand-assets`](https://github.com/pixelfed/brand-assets) repository.

## Optional real-instance smoke test

Set `PIXELFED_TEST_HOST` and `PIXELFED_TEST_TOKEN` when running the JVM tests.
The test remains read-only unless `PIXELFED_TEST_ALLOW_WRITE=1` is also set.
