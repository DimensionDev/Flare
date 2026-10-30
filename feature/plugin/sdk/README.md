# Flare Plugin SDK

`@dimensiondev/flare-plugin-sdk` contains editor types, a local package command,
templates, examples, and a portable AI authoring skill for Social Plugin API v1.
Flare provides the plugin runtime. Supported targets are Android, JVM Desktop,
iOS, and native macOS, not Web. Use a Flare build that includes Plugins in Settings;
this SDK does not promise a minimum public app release.

## Install for development

After the first npm preview release:

```sh
npm install --save-dev @dimensiondev/flare-plugin-sdk@next
```

Before publication, build a local tarball from a Flare checkout instead:

```sh
cd feature/plugin/sdk
npm ci
npm test
npm pack --pack-destination /tmp
```

Install the resulting `.tgz` in your plugin project with
`npm install --save-dev /path/to/dimensiondev-flare-plugin-sdk-VERSION.tgz`.
These local checks and packaging commands do not publish anything.

## Create and develop a plugin

1. Copy the contents of `node_modules/@dimensiondev/flare-plugin-sdk/skill/flare-plugin/assets/github-template/`
   into your plugin repository. Replace its IDs, icon, and illustrative website
   endpoints using evidence from the real API.
2. Read the [complete developer guide](https://github.com/DimensionDev/Flare/tree/feature/plugin/feature/plugin/sdk/skill/flare-plugin).
   The same guide is bundled at `skill/flare-plugin/README.md` inside the npm package,
   with all authentication, capability, runtime, data, testing, and publication references.
3. Keep your ready-to-run `manifest.json`, classic-script `plugin.js`, one supported
   `assets/icon.<extension>`, and optional `locales/*.json` in a plugin output directory.
   For a TypeScript project, configure your bundler to produce that classic script.
4. In Desktop or native macOS, select the output directory in Settings → Plugins →
   Plugin development. Start development, then reload after edits or rebuilds.
5. For local file installation, package that directory from your plugin project:

   ```sh
   npx flare-plugin-pack ./dist ./plugin.fpp
   ```

The packer accepts plugin files only, so keep source, `.git`, `package.json`,
`node_modules`, and the generated `.fpp` outside `dist`. It checks archive structure;
Flare still validates the manifest, registration, permissions, and runtime behavior.
For GitHub installation, commit the ready-to-run plugin files at the repository
root and publish a stable tag matching `manifest.version`. npm SDK versions and
individual plugin versions are independent; GitHub plugin installation ignores
prerelease tags even though the npm SDK can use them.

## Editor types

Add the package to your TypeScript project's compiler options:

```json
{
  "compilerOptions": {
    "types": ["@dimensiondev/flare-plugin-sdk"]
  }
}
```

Append it to any existing `types` list. For JavaScript, enable `checkJs` as well.
The declarations provide ambient `definePlugin`, `flare`, and wire types. Do not
import the SDK at runtime: Flare supplies those globals, and plugin scripts must
bundle their own runtime dependencies without Node or browser APIs.

## Use the AI authoring skill

The self-contained skill is in `skill/flare-plugin/` in both the source and npm
package. Copy that **entire directory** into your agent's skill directory; copying
only `SKILL.md` loses its types, references, templates, and examples. Follow the
[skill installation guide](https://github.com/DimensionDev/Flare/blob/feature/plugin/feature/plugin/sdk/skill/flare-plugin/README.md#install-the-skill)
for Codex or Claude Code, including project scope, verification, and updates.
All documentation is in English. The complete Pixelfed adapter is bundled at
`skill/flare-plugin/examples/pixelfed/`.

## Validate this SDK

From `feature/plugin/sdk`, run `npm ci` and `npm test` with Node 22 or newer.
The tests build an npm tarball, check its complete resource set and license,
install it offline in an empty project, type-check an actual consumer, and run
the installed package command against the bundled template. They do not contact
a website or publish to npm. Flare's Host and platform acceptance checks are
documented in `skill/flare-plugin/references/testing.md`.

## Publish this SDK (maintainers)

Publishing requires control of the `@dimensiondev` npm scope. This repository's
GitHub organization does not grant npm access. The package includes the repository's
AGPL license; its types, templates, and skill remain the canonical source resources.

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
   `npm version 0.1.0-beta.2 --no-git-tag-version`, and commit the tested changes.
2. Tag that commit `plugin-sdk-v0.1.0-beta.2` and push only that tag when ready to publish.
   The workflow rejects a tag that does not exactly match `package.json`.
3. The workflow validates the tarball first, then publishes prereleases to `next`
   and stable versions to `latest`. Existing app workflows exclude SDK tags.

The **Plugin SDK** workflow runs validation on relevant pull requests. Its manual
**Run workflow** action also validates only, even when a tag is selected; publishing
requires a push of a matching `plugin-sdk-v*` tag. Updating a PR never publishes npm.
