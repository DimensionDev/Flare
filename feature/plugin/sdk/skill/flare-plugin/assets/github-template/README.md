# Example Feed plugin template

This is a GitHub repository template for a token-based Flare API v1 adapter.
The website API below is an **illustrative contract**, not an available service.
Adapt the paths, authentication, and mapping to your website before publishing.
No demonstration server is included. Tests in Flare exercise this template with
fixture HTTP responses through the real QuickJS runtime.

## 1. Adapt the plugin

If you created this project with `npx flare-plugin-sdk@next init my-plugin`, work
inside `my-plugin` and keep its generated plugin/platform IDs. If you copied the
template manually, first copy its contents to your repository root and replace
the example `manifest.id`, `platform.id`, and names. Keep IDs stable for updates.

The generated manifest's `$schema` is pinned to the SDK version that created it.
Open `manifest.json` in VS Code or another JSON Schema editor for completion,
field descriptions, enum choices and static errors. Flare treats the URL as
metadata and never downloads it; actual registration and behavior are checked in
Flare. See the [manifest editor guide](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/manifest.md#json-schema-and-editor-support)
for offline use and older Flare builds that reject `$schema`.

1. Update the description, login methods, and declared capabilities in
   `manifest.json`. Replace the one-pixel placeholder PNG with your own icon.
2. Adapt `plugin.js` using the
   [developer guide](https://github.com/DimensionDev/Flare/tree/feature/plugin/feature/plugin/sdk/skill/flare-plugin)
   and your website's API evidence.
   It currently verifies an imported bearer token with `/api/me`, then reads
   `/api/posts`. It registers no detector, so choose the platform explicitly.
3. Update this README with the actual website, token acquisition steps, supported
   features, verification results, and remaining limitations.

The template requests no media permission and emits text-only posts. Every post
uses `detailType: "link"`, opening its HTTPS website URL. There is no arbitrary
profile lookup, internal post detail, write capability, or guest access. The
authenticated profile returned at login supplies the saved account identity.

## 2. Develop and debug locally

The plain JavaScript starter needs Node 22 or newer for tooling, with no dependency
installation or build step. From this project directory, check syntax:

```sh
node --check plugin.js
node -e 'JSON.parse(require("node:fs").readFileSync("manifest.json", "utf8")); console.log("Manifest JSON parses")'
```

Flare supplies the `definePlugin` and `flare` globals and performs the actual
manifest, registration, and runtime checks. Use a Flare build with Plugins in Settings.

1. In Compose Desktop or native macOS, open **Settings → Plugins → More → Plugin
   development**. Choose this directory, click **Start development**, and confirm
   the access review.
2. Add an account, explicitly select this plugin platform, and enter your website's
   HTTPS API origin. Import a real access token. After adapting the endpoints,
   verify the account identity, Home refresh, older/empty/end pages, and link clicks.
3. Edit and save `plugin.js`, `manifest.json`, the icon, or translations. Flare
   automatically validates them; click **Reload plugin** to apply the validated
   version. Invalid edits leave the previous working version running.
4. Check validation errors and recent-call diagnostics in the development panel.
   Recent calls show method names and outcomes, without HTTP request/response
   payloads. Reproduce authentication, HTTP, response-mapping, and paging failures
   against your website's documented API.
5. Click **Stop development** before installing or updating this plugin. Stopping
   restores the version that was running before development; app restarts do not
   automatically mount this directory. Development uses real accounts and data,
   and remote writes are not rolled back.

For TypeScript, run your own compiler/watch process and select its output directory.
Emit one self-contained classic `plugin.js`; Flare provides its Host APIs rather
than Node/browser APIs. Commit that built script at the repository root for release.
See [development and testing details](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/testing.md#develop-locally-in-desktop)
for reload restrictions, credential changes, and platform acceptance checks.

## 3. Publish and install through GitHub (recommended)

Create a public GitHub repository with these ready-to-run files at its root:

```text
manifest.json
plugin.js
assets/icon.png
README.md
locales/en.json       # optional; other locale catalogs as needed
```

The icon can also be JPEG, WebP, GIF, or ICO; keep exactly one supported icon.
Development files may coexist in the repository, but keep credentials,
`node_modules`, build caches, and private fixtures out of Git. Flare reads the
committed plugin files directly, so build TypeScript before committing/tagging.

For a new project and an empty GitHub repository, replace `OWNER/REPO` and run:

```sh
git init
git add manifest.json plugin.js assets README.md
# If used, also stage translations with: git add locales
git commit -m "Add Flare plugin"
git branch -M main
git remote add origin https://github.com/OWNER/REPO.git
git push -u origin main
```

Use the existing Git setup if the project is already a repository. The generated
`manifest.version` is `0.1.0`. After pushing the matching commit, publish its stable tag:

```sh
git tag v0.1.0
git push origin refs/tags/v0.1.0
```

If you changed the manifest version, commit/push it and use that exact version
after the optional lowercase `v`. npm SDK versions and plugin versions are
independent. Flare ignores prerelease plugin tags; a branch alone is insufficient.
A GitHub Release and uploaded `.fpp` asset are unnecessary.

Stop development, then in **Settings → Plugins → Add → Install from GitHub** enter
`https://github.com/OWNER/REPO`. Load the latest stable version, review its source
and permissions, and confirm. Installation takes effect immediately. Select the
plugin platform, HTTPS API origin, and real access token to verify it on each target
device. For later updates, increment `manifest.version`, commit/push, and publish
a new matching stable tag while preserving IDs. Check for updates in Flare and
review/confirm the new version. See the
[publication and update rules](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/publishing.md).

## Alternative: package a local file

Use `.fpp` for offline transfer or local testing. Create a new, empty `dist`
directory and copy only `manifest.json`, `plugin.js`, one supported
`assets/icon.<extension>`, and any `locales/*.json` into it, keeping those paths.
Keep `.git`, `package.json`, `node_modules`, tests, and old archives outside `dist`.
From this project directory, run:

```sh
npx --package=flare-plugin-sdk@next flare-plugin-pack ./dist ../my-plugin.fpp
```

Stop development for this plugin, then choose file installation in **Settings →
Plugins → Add**, select the `.fpp`, review, and confirm. It becomes available
immediately. The packer checks archive structure; Flare still validates the
manifest, registration, permissions, and behavior. Local file installs have no
GitHub source/update tracking.

## Illustrative website API to replace

The selected origin is an absolute HTTPS origin such as `https://api.example.org`
with no path. Both requests use a structured bearer token. The imported token
is accepted only after the profile request succeeds.

`GET /api/me`:

```json
{ "id": "alice", "username": "alice", "name": "Alice" }
```

`GET /api/posts?limit=20` and subsequent `&cursor=<encoded opaque cursor>`:

```json
{
  "items": [{
    "id": "post-1",
    "author": { "id": "alice", "username": "alice", "name": "Alice" },
    "created_at": "2026-01-01T00:00:00Z",
    "text": "A post from the website",
    "url": "https://example.org/posts/post-1"
  }],
  "next_cursor": "page-2"
}
```

`next_cursor` is omitted or null at the end. Empty pages may still contain a next
cursor. IDs are strings and stable across requests; request cursors never become
entity IDs. A refresh starts without the old cursor. Adapt these assumptions to
the actual API, including token expiry and rate-limit responses. The helper maps
401/404/429 to typed errors, treats other HTTP failures as `Remote`, and parses
the response's string `body` explicitly.

Describe separately which checks passed with fixtures and which passed against
the actual website and Flare UI. Keep tokens and Cookies out of the repository.
