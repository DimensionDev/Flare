---
name: flare-plugin
description: Create, extend, debug, and prepare GitHub distribution for Flare Social Plugin API v1 plugins. Use when implementing a website adapter, editing a Flare plugin manifest or classic JavaScript entrypoint, adding login or datasource capabilities, or diagnosing Flare plugin installation and runtime errors.
---

# Flare Plugin

Produce a website adapter that matches Flare's API v1 contract and can be installed
from a public GitHub repository. Use the user's chosen website and requested
features. This skill includes the SDK; resolve links relative to this file, not
to the user's plugin repository.

## Establish the contract

1. Inspect an existing plugin before changing it. For a new plugin, collect the
   target HTTPS origin, requested features, login method, API documentation, and
   representative redacted responses. Public API documentation may supply missing
   information. Identify unresolved endpoint, authentication, identity, or paging
   assumptions before implementing the affected feature.
2. Read [manifest](references/manifest.md), [capabilities](references/capabilities.md),
   and [runtime/HTTP](references/runtime-http.md). Map requested features to
   capability IDs, their required operations, service method paths, and UI entry
   points. Declare only supported behavior, including any required companion
   operations. A function's TypeScript optionality does not relax manifest rules.
3. Use [flare-plugin.d.ts](flare-plugin.d.ts) for exact signatures and [ABI.md](ABI.md)
   for the boundary contract. Preserve API v1 behavior. If the target Flare build
   has a different contract, establish its version before generating code; there
   is no implied released-app minimum version in this skill.

## Implement the requested adapter

Start a new repository from [the GitHub template](assets/github-template/README.md).
Copy the **contents** of `assets/github-template/` to the new repository root.
Its `/api/me` and `/api/posts` contract is illustrative, not a live service. Adapt
it from verified website evidence before presenting it as an integration.
Use [Pixelfed](examples/pixelfed/README.md) as a complete real-service example,
not as a universal API specification.

Read the references needed for the task:

| Task | Read |
| --- | --- |
| Detection, OAuth, password, token import, form, Cookie, credential refresh | [Authentication](references/authentication.md) |
| Map remote entities, HTML, media, identity, cursor pagination, opaque tokens | [Wire data](references/wire-data.md) |
| Implement any of the twelve capabilities, guest access, actions, Deep Links, localization | [Recipes](references/recipes.md) and the relevant [capability row](references/capabilities.md) |
| Diagnose a rejected package, missing UI, failed request, or retired runtime | [Troubleshooting and limits](references/troubleshooting.md) |
| Check the generated files and exercise behavior | [Testing](references/testing.md) |
| Prepare a GitHub repository, version tag, update, or schema change | [Publishing](references/publishing.md) |

Keep `plugin.js` a self-contained classic script and call `definePlugin` once.
Use `flare.http.request` with structured authorization/Cookies; retain credentials
through the Host account credential API. Map remote data explicitly into Wire
DTOs. Do not add fields to the ABI or assume browser/Node globals exist. Perform
Host calls inside registered methods, not during script initialization.

The manifest, registered methods, and login's negotiated capabilities must agree.
Keep account IDs stable, use the selected origin for authenticated identity, and
treat globals as disposable cache. Use typed `flare.error` results for expected
remote failures. Request media permission only when the adapter returns remote
media. Keep actual secrets out of source, examples, logs, and repository history.

## Validate and deliver

Follow [testing](references/testing.md): check syntax, manifest/method agreement,
representative mapping and paging behavior, and available real Host validation.
For an existing plugin, recheck the behavior changed by the edit. State what ran
and what still needs the target website or Flare UI; syntax checks and mocked
responses do not prove live integration.

Prefer GitHub distribution: one plugin at repository root, an already-built
`plugin.js`, and a stable version tag matching `manifest.version`. Prepare files
and release instructions; create/push repositories, commits, or tags only within
the user's current authorization. See [publishing](references/publishing.md) for
immediate activation, uninstall cleanup, and the optional local `.fpp` path.

Deliver the complete repository files, feature-to-capability mapping, setup and
installation instructions, checks performed, and remaining assumptions. A new
adapter is ready for use only when its documented website contract and required
runtime paths have been exercised; otherwise identify the unfinished integration
steps precisely.
