# Flare Plugin SDK

`flare-plugin-sdk` contains a project initializer, editor types, a local package command,
templates, examples, and a portable AI authoring skill for Social Plugin API v1.
Flare provides the plugin runtime. Supported targets are Android, JVM Desktop,
iOS, and native macOS, not Web. Use a Flare build that includes Plugins in Settings;
this SDK does not promise a minimum public app release.

## Create a plugin

With Node 22 or newer, run:

```sh
npx flare-plugin-sdk@next init my-plugin
cd my-plugin
npm install
```

This copies the GitHub template bundled with that SDK version into `my-plugin`:

```text
my-plugin/
  manifest.json
  plugin.js
  assets/icon.png
  src/plugin.ts
  package.json
  tsconfig.json
  build.mjs
  .gitignore
  README.md
```

The directory name becomes the plugin and platform display name. The initializer
generates unique plugin/platform IDs; keep them stable after publication. New and
empty directories are accepted, including `init .` in an empty current directory.
Non-empty directories, files, and symbolic links are rejected without overwriting
them. The command uses bundled files only and does not install dependencies,
initialize Git, contact your website, or publish anything. Run
`npx flare-plugin-sdk@next --help` for usage.

The generated `manifest.json` includes a `$schema` URL pinned to the SDK version
used to create it. Open it in VS Code or another JSON Schema editor for property
completion, descriptions, enum choices, and static validation. The complete schema
is bundled at `skill/flare-plugin/manifest.schema.json` inside the SDK.

The root already contains an installable, prebuilt `plugin.js`. Install development
dependencies to edit and rebuild the TypeScript source. The private project pins
the SDK to the version used by `init`; TypeScript and esbuild are development tools.

Edit `src/plugin.ts` to use your website's real authentication, endpoints, response
mapping, and pagination. The starter's `/api/me` and `/api/posts` are illustrative;
no demonstration server is included. Update the description, login methods and
capabilities in `manifest.json`, and replace the placeholder icon. Keep `plugin.js`
as generated output; edit the TS source and rebuild instead of editing both copies.

For manual template copies, replace the example IDs and names before use. For
`npx` projects, keep the generated IDs. Read the
[complete developer guide](https://github.com/DimensionDev/Flare/tree/feature/plugin/feature/plugin/sdk/skill/flare-plugin)
for authentication, Host APIs, capabilities, and Wire data mapping.

## Develop and debug in Flare

1. From `my-plugin`, run:

   ```sh
   npm run check
   npm run build
   node -e 'JSON.parse(require("node:fs").readFileSync("manifest.json", "utf8")); console.log("Manifest JSON parses")'
   ```

   `check` uses the SDK's ambient types with strict TypeScript checking. `build`
   checks types and bundles local TS modules into one ES2020 IIFE at root.
   Actual execution and manifest/registration validation happen in Flare, which
   provides `defineFlareSocialPlugin` and `flare`.
2. In Compose Desktop or native macOS, open **Settings → Plugins → More → Plugin
   development**. Choose the `my-plugin` directory, click **Start development**,
   and confirm the access review.
3. Add an account in Flare. Select your plugin platform explicitly, enter the
   website's HTTPS API origin, and import a real access token using the starter's
   login form. Adapt this step if you change the login method. Test login,
   timeline refresh, older pages, end-of-list behavior, and website link clicks.
4. Run `npm run dev` in a terminal, then edit `src/plugin.ts` or its local modules.
   Watch mode rebuilds `plugin.js`; syntax/build errors preserve the previous
   script. It transpiles without type checking, so use editor diagnostics or run
   `npm run check` separately. Flare automatically validates runtime-file changes;
   click **Reload plugin** to apply them. Invalid edits keep the last working
   version running. The development panel shows validation errors and recent-call
   diagnostics; recent calls contain method names and outcomes, without HTTP
   request/response payloads.
5. Click **Stop development** before installing or updating the same plugin.
   Development temporarily overrides the running version; stopping restores the
   previous version. App restarts do not automatically mount the directory.
   Development uses real accounts and data, and remote writes are not rolled back.

Finish active writes before reloading; credential-schema changes may require
logging in again. Keep Flare pointed at the project root, where the build writes
`plugin.js`. Flare loads that classic script and does not execute TS or build tools.
See [development and testing details](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/testing.md#develop-locally-in-desktop).

## Publish and install through GitHub (recommended)

1. Create a public GitHub repository. Its tagged root must contain `manifest.json`,
   the ready-to-run `plugin.js`, exactly one supported `assets/icon.<extension>`,
   and optional `locales/*.json`. Update the project README with the actual website,
   login instructions, supported features, and verification results. Flare reads
   these files directly; commit any compiled output at the root before tagging.
2. Run `npm run build`, then commit both source and the resulting root `plugin.js`.
   The generated `.gitignore` excludes dependencies, temporary files and `.fpp`
   archives, while retaining `plugin.js`. Commit the `package-lock.json` created
   by `npm install`; use `npm ci` on later clean checkouts. For a new project and an empty GitHub
   repository, replace `OWNER/REPO` and run from `my-plugin`:

   ```sh
   git init
   git add manifest.json plugin.js assets src package.json package-lock.json tsconfig.json build.mjs .gitignore README.md
   # If used, also stage translations with: git add locales
   git commit -m "Add Flare plugin"
   git branch -M main
   git remote add origin https://github.com/OWNER/REPO.git
   git push -u origin main
   ```

   Use your existing Git setup if the project is already a repository. Keep real
   credentials, private fixtures, `node_modules`, and build caches out of Git.
3. Set a stable `manifest.version` and push a matching tag. The generated version
   is `0.1.0`, so the first release can use:

   ```sh
   git tag v0.1.0
   git push origin refs/tags/v0.1.0
   ```

   Commit and push any version edit before tagging. npm SDK versions are separate
   from your plugin's version. Flare ignores prerelease plugin tags; pushing only
   a branch does not create an installable version. A GitHub Release and `.fpp`
   upload are unnecessary.
4. Stop development, then open **Settings → Plugins → Add → Install from GitHub**
   on any supported Flare platform. Enter `https://github.com/OWNER/REPO`, load the
   latest stable version, and review its source and permissions before confirming.
   Installation takes effect immediately. Add/log in to the plugin account and
   verify its behavior on that device.
5. For updates, increment `manifest.version`, commit and push, then publish a new
   matching stable tag. Keep plugin/platform IDs unchanged. In Flare, check for
   updates and review/confirm the offered version; app restart is unnecessary.

See [GitHub publication and update rules](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/publishing.md).

## Optional: package and install a local file

Use `.fpp` for offline transfer or local testing when GitHub distribution is not
needed. From the generated project root:

```sh
npm run package
```

This checks types, rebuilds `plugin.js`, stages only manifest/script/icon/locale
files in a temporary directory, reuses the SDK packer, and writes `plugin.fpp` in
the project root. Temporary staging is cleaned up. Source and development files
are excluded. The standalone `flare-plugin-pack` command still requires a clean
runtime directory; passing the entire TS project is not supported.

Stop development for this plugin. In **Settings → Plugins → Add**, choose file
installation, select the `.fpp`, review, and confirm. Installation is immediate.
Packaging checks archive structure; Flare still validates the manifest,
registration, permissions, and behavior. File installs have no GitHub update tracking.

## SDK types and editor support

The generated `tsconfig.json` already sets `types: ["flare-plugin-sdk"]`, strict
checking, and an ES2020 library without browser or Node globals.
`defineFlareSocialPlugin` contextually types its handlers; `TimelineItemV1`, `PostV1`, `ProfileV1`,
and other SDK types are available directly. The SDK is a development dependency,
not a runtime import.
Local TS imports are bundled into `plugin.js`; imported code must also work
without Node/browser APIs. `build` and `package` stop on type errors.

For JSON Schema use without network access, install the project's dependencies and set the
manifest's `$schema` to `./node_modules/flare-plugin-sdk/skill/flare-plugin/manifest.schema.json`.
Keep these development files outside the optional `.fpp` staging directory.
Flare treats `$schema` as editor metadata and never downloads it. With an older
Flare build that rejects this field, remove it and use a VS Code `json.schemas`
association in workspace settings instead. See the
[manifest editor guide](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/references/manifest.md#json-schema-and-editor-support)
and [VS Code schema settings](https://code.visualstudio.com/docs/languages/json#_json-schemas-and-settings).

For an existing project, install editor types:

```sh
npm install --save-dev flare-plugin-sdk@next
```

Add the package to your TypeScript project's compiler options:

```json
{
  "compilerOptions": {
    "types": ["flare-plugin-sdk"]
  }
}
```

Append it to any existing `types` list. For JavaScript, enable `checkJs` as well.
The declarations provide ambient `defineFlareSocialPlugin`, `flare`, and wire
types. Do not import the SDK at runtime: Flare supplies those globals, and plugin
scripts must bundle their own runtime dependencies without Node or browser APIs.

## Use the AI authoring skill

The self-contained skill is in `skill/flare-plugin/` in both the source and npm
package. Copy that **entire directory** into your agent's skill directory; copying
only `SKILL.md` loses its types, references, templates, and examples. Follow the
[skill installation guide](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/README.md#install-the-skill)
for Codex or Claude Code, including project scope, verification, and updates.
All documentation is in English. The complete Pixelfed adapter is bundled at
`skill/flare-plugin/examples/pixelfed/`. The offline
[mixed timeline showcase](skill/flare-plugin/examples/timeline-showcase/README.md)
covers Post, Feed, User, UserList and Message rows, notifications, voting,
reactions and follow requests using Flare's existing UI.

Timeline-producing methods now return `PageV1<TimelineItemV1>`; post detail returns
`{ post, presentation? }`. This preview intentionally removes the old bare-post
and notification wire shapes. Update plugin sources and use a matching Host/SDK
build; see the [wire contract](skill/flare-plugin/references/wire-data.md#timeline-items-and-interactions).

## Validate this SDK

From `feature/plugin/sdk`, run `npm ci` and `npm test` with Node 22 or newer.
The tests build an npm tarball, check its complete resource set and license,
install it offline in an empty project, invoke the actual npx initializer,
type-check a consumer, and install/build/package the generated TS project. The
SDK tarball is installed offline; development tools use cached packages where
available and fetch missing npm metadata or packages. Checks compare the built and
prebuilt script, exercise local-module bundling, watch errors/recovery and missing
Node APIs, and verify the package contains only runtime files.
Initializer tests cover valid unique IDs, empty/nested/Unicode paths, invalid
arguments, and preserving existing directories, files, and symlinks. They do not
contact a website or publish to npm. Flare's Host and platform acceptance checks are
documented in `skill/flare-plugin/references/testing.md`.
The draft-07 schema is compiled with Ajv in strict mode. Shared manifest cases
exercise both Schema and Host validation; Host tests also compare every field,
required property, enum, and known capability operation against Kotlin descriptors.

To try the SDK from a checkout before npm publication, run
`npm pack --pack-destination /tmp` after those checks, then install the resulting `.tgz` in
a separate authoring project with
`npm install --save-dev /path/to/flare-plugin-sdk-VERSION.tgz`.

## Publish this SDK (maintainers)

For the first publication, use the npm account that will own this unscoped package.
Subsequent publication and Trusted Publisher setup require package maintainer access;
GitHub repository permissions do not grant npm access. The package includes the
repository's AGPL license; its types, templates, and skill remain the canonical source resources.

For the first publication, an authorized maintainer can use an npm account with
2FA, run `npm ci` and `npm test` in this directory, then explicitly run:

```sh
npm login
npm whoami
npm publish --access public --tag next
```

Only run that last command when publication is intended. The GitHub workflow does
not bootstrap a new npm package or configure npm account permissions.

After the package exists, configure [npm Trusted Publishing](https://docs.npmjs.com/trusted-publishers/)
in its npm Settings with GitHub owner `DimensionDev`, repository `Flare`, workflow
filename `plugin-sdk.yml`, no environment, and permission to publish directly with
`npm publish`. Use a GitHub-hosted runner. The workflow uses Node 24 and npm's OIDC
authentication; no long-lived npm token or `NPM_TOKEN` secret is required. npm
requires CLI 11.5.1 or newer and Node 22.14 or newer. Complete the configuration's
first successful CI publication within two days, or recreate the expired connection.
Public OIDC publications from this public repository receive provenance automatically.

For subsequent releases:

1. Update the SDK version and lockfile together, for example with
   `npm version prerelease --preid beta --no-git-tag-version`, and commit the tested changes.
2. Tag that commit `plugin-sdk-v<version>` using the new version and push only that tag when ready to publish.
   The workflow rejects a tag that does not exactly match `package.json`.
3. The workflow validates the tarball first, then publishes prereleases to `next`
   and stable versions to `latest`. Existing app workflows exclude SDK tags.

Run the **Plugin SDK** workflow manually to validate a branch or tag; this does not
publish, even when a tag is selected. Publishing requires a push of a matching
`plugin-sdk-v*` tag. Branch pushes and pull requests do not start this workflow.
