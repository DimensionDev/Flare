# Flare Plugin SDK and skill

Use this directory as the `flare-plugin` skill to build a website adapter with an
AI coding agent, or read the same references directly. All material describes
Social Plugin API v1 in the accompanying Flare source. Use a Flare build that
includes this subsystem; this directory does not promise a minimum public app
release. The module targets Android, JVM Desktop, iOS, and native macOS, not Web.

## Install the skill

This skill installs into your AI coding agent. The website adapter it helps you
create is installed separately into Flare; see [Start a plugin repository](#start-a-plugin-repository).
The source package is `feature/plugin/sdk/skill/flare-plugin/`. Copy that **entire
directory**, including references, types, assets, and examples. Copying only
`SKILL.md` loses the SDK. The parent `skill/` directory is just a container.

The package follows the [Agent Skills format](https://agentskills.io/specification):
the directory and frontmatter name are both `flare-plugin`, the entrypoint is
`SKILL.md`, and supporting files use relative links. It has no agent-specific
runtime dependency. Optional UI metadata such as `agents/openai.yaml` is not
needed to use it. Your agent still needs file access and the tools required by
your plugin project; Node is needed for the documented syntax and package checks.

### Install from GitHub with Codex

The [development source](https://github.com/DimensionDev/Flare/tree/feature/plugin/feature/plugin/sdk/skill/flare-plugin)
is on the `feature/plugin` branch of `DimensionDev/Flare`. It is a development
branch, not a released SDK version. For reproducible installation, select the
full commit SHA containing the skill in GitHub and use it in place of the branch.

Ask Codex's built-in installer:

```text
$skill-installer Install the flare-plugin skill from repository DimensionDev/Flare,
ref feature/plugin, path feature/plugin/sdk/skill/flare-plugin.
Copy the complete skill directory, including its supporting files.
```

Give the repository, ref, and path separately: this ref contains a slash. If you
use the installer's Python CLI directly, the equivalent arguments are
`--repo DimensionDev/Flare --ref feature/plugin --path feature/plugin/sdk/skill/flare-plugin`.
Record the resolved commit SHA when you need to reproduce an installation. If a
skill already exists, follow [Update or remove](#update-or-remove) first.

### Install from a local checkout

Check out a revision that contains this directory. For example, clone the
development branch into a new directory, then enter its repository root:

```sh
git clone --branch feature/plugin --single-branch https://github.com/DimensionDev/Flare.git
cd Flare
```

For an existing checkout, use your normal Git workflow to select the intended
revision before copying. No Flare build is needed to install the skill.

From the Flare repository root, choose **one** of the following first-install
commands. The existence check prevents copying into an already-installed skill.

Codex, for all local projects:

```sh
mkdir -p "$HOME/.agents/skills"
if [ ! -e "$HOME/.agents/skills/flare-plugin" ] && [ ! -L "$HOME/.agents/skills/flare-plugin" ]; then
  cp -R feature/plugin/sdk/skill/flare-plugin "$HOME/.agents/skills/flare-plugin"
else
  echo "flare-plugin already exists; follow Update or remove before replacing it."
fi
```

Claude Code, for all local projects:

```sh
mkdir -p "$HOME/.claude/skills"
if [ ! -e "$HOME/.claude/skills/flare-plugin" ] && [ ! -L "$HOME/.claude/skills/flare-plugin" ]; then
  cp -R feature/plugin/sdk/skill/flare-plugin "$HOME/.claude/skills/flare-plugin"
else
  echo "flare-plugin already exists; follow Update or remove before replacing it."
fi
```

For a project-only installation, copy the same `flare-plugin/` folder into the
**plugin project's** `.agents/skills/` (Codex) or `.claude/skills/` (Claude Code).
Use the plugin project's path, even when your source checkout is elsewhere.
Choose one scope per agent to avoid loading duplicate copies. The expected
layout is `<skills-directory>/flare-plugin/SKILL.md`, with resources beside it.

### Verify discovery and invoke

1. Check the installed folder contains `SKILL.md`, `flare-plugin.d.ts`,
   `references/manifest.md`, `assets/github-template/plugin.js`, and
   `examples/pixelfed/plugin.js`.
2. In Codex CLI or its IDE extension, use `/skills` or type `$` and select
   `flare-plugin`. If it is missing, restart Codex and check the installation
   location. In Claude Code, type `/flare-plugin`; if a newly created skills
   directory is not discovered, run `/reload-skills` or start a new session.
3. Run a read-only check before creating a plugin:

   ```text
   Use the flare-plugin skill. Read its bundled references and template.
   Explain the required files for a GitHub plugin and the available login methods,
   and identify the template's illustrative endpoints. Do not create or publish files.
   ```

   Confirm the agent reads resources from the installed directory and identifies
   `/api/me` and `/api/posts` as illustrative. This checks discovery and access,
   not the correctness of a future website integration.

For actual work, prefix your request with `$flare-plugin` in Codex CLI/IDE or
`/flare-plugin` in Claude Code. Natural-language requests can also trigger the
skill when they match its description. See the current official
[Codex instructions](https://learn.chatgpt.com/docs/build-skills) and
[Claude Code instructions](https://code.claude.com/docs/en/skills) for discovery
locations, invocation, and client-specific behavior. These local installations
do not automatically install the skill in a remote or cloud agent environment.

### Update or remove

To update, select the desired source revision and review its changes. Back up the
existing installed `flare-plugin` folder **outside all skill discovery directories**,
preserving any local edits, then replace it with the complete new folder. With
Codex's installer, move the old folder out before reinstalling; it does not
overwrite an existing installation. Avoid merging copies: removed reference
files would otherwise remain. Repeat the discovery check after replacement.

To remove, delete or move out only the installed `flare-plugin` folder from the
location you chose. Restart the agent if its menu still shows it. Updating or
removing this skill does not change generated plugin repositories or plugins
already installed in Flare.

### Use without installing

An agent with access to this checkout can follow an explicit request to read
`feature/plugin/sdk/skill/flare-plugin/SKILL.md` and its referenced files. This
works for a single task; it does not register a discoverable skill. Keep the
entire folder accessible rather than pasting only the entrypoint into chat.

## Use with an AI coding agent

Start with a request such as:

> Use the flare-plugin skill to create a plugin for [website / HTTPS API origin].
> Implement [features] using [login method]. Here are the API documentation and
> redacted example responses: [sources]. First map these features to Flare
> capabilities and list any missing API information. Then create a GitHub-ready
> repository, validate the available paths, and explain installation and any
> remaining integration work.

For an existing plugin:

> Use the flare-plugin skill to add [feature] to this repository. Preserve its
> plugin and platform IDs and existing features. Inspect the current manifest,
> implementation, and website API before editing. Update the capability mapping,
> documentation, and relevant checks, and explain the version/schema implications.

Provide API docs, authenticated-profile and content responses, paging examples,
and authentication details relevant to the requested feature. Remove real tokens,
passwords, Cookies, and private user content. Tell the agent which errors or edge
cases matter. It should identify gaps rather than invent website endpoints.

## Start a plugin repository

1. Copy the contents of [assets/github-template](assets/github-template/README.md)
   to a new repository root. Its example website contract needs adaptation; no
   demonstration server is required or provided.
2. Choose stable plugin/platform IDs, replace the placeholder icon, and adapt
   authentication and data mapping to your website. See [manifest](references/manifest.md)
   and [authentication](references/authentication.md).
3. Implement the required capability methods and map the responses into
   [Wire data](references/wire-data.md). The starter uses token import and a
   read-only timeline whose posts open their website URLs.
4. Use [Desktop plugin development](references/testing.md#develop-locally-in-desktop)
   to validate and reload a local directory. Follow the other testing checks, then
   commit the ready-to-run `plugin.js` and publish a public GitHub repository with a stable version tag.
5. In Flare's plugin management, enter `https://github.com/OWNER/REPO` and click
   **Install**. Flare downloads the latest stable version and shows its source and
   permissions in the same dialog. Confirm installation, then restart Flare.
   Follow the plugin's README to select the platform, origin, and login method.

See [publishing](references/publishing.md) for exact tag rules and updates. Flare
reads the tagged repository root; a subdirectory in the Flare repository itself
is not a plugin installation URL. A GitHub Release or `.fpp` upload is unnecessary.

## Reference and example index

| Need | Reference |
| --- | --- |
| Agent workflow | [SKILL.md](SKILL.md) |
| Boundary contract and registration | [ABI.md](ABI.md) |
| Exact JavaScript signatures and field comments | [flare-plugin.d.ts](flare-plugin.d.ts) |
| Manifest fields, defaults, enums, and validation | [Manifest](references/manifest.md) |
| All twelve capabilities, minimum methods, and UI requirements | [Capabilities](references/capabilities.md) |
| Platform engines, async completion, HTTP, permissions, credentials, and state | [Runtime and HTTP](references/runtime-http.md) |
| Detection and all five login methods | [Authentication](references/authentication.md) |
| DTO mapping, pagination, media, and opaque tokens | [Wire data](references/wire-data.md) |
| Capability implementations, guest, localization, and Deep Links | [Recipes](references/recipes.md) |
| Errors, resource limits, and recovery | [Troubleshooting](references/troubleshooting.md) |
| Author checks, Host tests, and acceptance evidence | [Testing](references/testing.md) |
| GitHub publication, upgrades, and optional local installation | [Publishing](references/publishing.md) |
| Complete website adapter | [Pixelfed](examples/pixelfed/README.md) |

Rules have one canonical reference; the skill links to it when needed. Recipe
fragments explain adaptation points and are not separate installable plugins.
