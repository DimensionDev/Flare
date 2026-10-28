# Validation and acceptance

Record checks against the actual plugin revision. Use the smallest checks that
exercise the edited behavior. Distinguish syntax, mocked behavior, real QuickJS,
installation, live website behavior, and platform UI; none proves all the others.
No demonstration backend is required for authoring this SDK.

## Checks in a generated repository

From the generated repository root, using Node:

```sh
node --check plugin.js
node -e 'JSON.parse(require("node:fs").readFileSync("manifest.json", "utf8")); console.log("Manifest JSON parses")'
```

These are syntax checks only: Node accepts APIs that the QuickJS Host does not
provide, and parsing JSON does not validate the manifest. Also inspect:

- Required root files and valid PNG; stable IDs and version/tag consistency.
- Every declared known operation has its required registration and full method
  set. Login negotiates supported subsets with a displayable capability.
- Top-level initialization has no Host calls; bundled output has no module or
  runtime platform dependency.
- Each remote response is mapped explicitly. Fixtures cover missing fields,
  invalid JSON, HTTP errors, pagination continuation, and stable identities.
- Auth/cursor/action data is not accidentally inserted into public content or
  media URLs. No real secrets enter checked-in fixtures.

Use the shipped `.d.ts` as editor/reference input, outside the release package.
For a TypeScript authoring project, reference that file and configure the actual
bundler to emit one classic script. Keep manifest/Host validation even when the
type checker passes: TypeScript optional service properties do not express all
capability requirements, and optionality does not make every null value valid.

## Develop locally in Desktop

In Settings > Plugins, open the overflow menu and choose **Plugin development**.
Choose the directory containing `manifest.json`, `plugin.js`, and the plugin assets.
Click **Start development** and review access to your existing accounts. Both Compose Desktop and native macOS support this flow.

Edit `plugin.js`, `manifest.json`, `assets/icon.png`, or `locales/*.json`. Flare
automatically validates the files; click **Reload plugin** to apply the validated
snapshot to this plugin's accounts and open pages. An invalid edit keeps the last
working version running. For TypeScript projects, select your build output directory
and run your own compiler/watch command; Flare does not execute build scripts.

Only one development directory can be active. Reload is refused while publishing,
uploading, deleting, or sending is in progress; retry after the operation finishes.
Reload clears this plugin's derived account caches and restarts its login flows;
drafts, credentials, routes, and other plugins are retained. Changing the plugin ID
or platform ID requires stopping first. An override must retain the installed
plugin's platform ID. A credential schema change may require logging in again.

**Stop development** restores the version that was running before development.
Restarting the app loads its installed plugins normally; the last directory is
remembered but never mounted automatically. Development uses real accounts and
data: changes made by the plugin are not rolled back. Stop development before
installing, updating, disabling, or uninstalling the same plugin. Recent-call
diagnostics show method names and outcomes only, never request/response payloads.

## Checks with a Flare source checkout

Run from the Flare repository root:

```sh
./gradlew :feature:plugin:jvmTest \
  --tests '*GitHubTemplateTest' \
  --tests '*PixelfedSampleTest' \
  --tests '*PluginGitHubInstallTest'
```

`GitHubTemplateTest` constructs a GitHub-style source archive from the **actual
template files**, passes it through the real importer/registration checks,
activates it after reopening the store, and invokes real QuickJS. It verifies
token/profile identity, link DTOs, refresh/older/empty/end paging, typed HTTP
errors, and malformed website responses using an injected fixture transport.
It does not download a live GitHub repo or contact a website.

`PixelfedSampleTest` packages and exercises the complete Pixelfed sample with
fixtures; `PluginGitHubInstallTest` covers source archive/version/identity rules.
For a new adapter, replace/adapt the fixtures to its actual API evidence and
add checks at this seam. Read the generated test XML/results for executed counts,
failures, and skips; a cached/no-source task is not evidence of new execution.

For changes to manifests or wire contracts, relevant existing suites include
PluginManifestV1Test and WireV1Test. Runtime/HTTP/login suites cover their own Host
contracts. Run the affected suites rather than requiring a full app build for a
documentation edit. The opt-in Pixelfed live test requires PIXELFED_TEST_HOST and
PIXELFED_TEST_TOKEN; it is read-only unless PIXELFED_TEST_ALLOW_WRITE=1. Do not
enable live writes without the user's current authorization.

## Website and UI acceptance

After an adapter has real endpoints and authorized credentials:

1. Install the selected GitHub stable tag and restart. Confirm source/version and
   permission review, then the expected platform/login/timeline entries.
2. Verify each supported login method and account identity; exercise cancelled,
   rejected, or expired authentication where practical.
3. Refresh, paginate, open internal/link details, and verify remote media where
   declared. Exercise only the advertised read/write capabilities, using safe
   test accounts/content authorized for those actions.
4. For updates, test a new version and restart, including credential compatibility
   and removed permissions/capabilities that affect cached content.
5. Record Android, JVM Desktop, iOS, and native macOS evidence separately.
   Simulator/compilation results do not establish physical-device UI behavior.

The generic template's API is illustrative. Passing its fixture tests does not
turn it into a working adapter for an arbitrary website; replace and validate
the API contract before claiming it is ready for end users.

## Skill portability and behavioral checks

Copy `feature/plugin/sdk/skill/flare-plugin` alone into a temporary directory.
Check that its directory name matches the frontmatter `name` and that all relative document/type/template/example references
stay within that copy. Exercise the pack command from the copy if local packaging
is relevant; no parent Flare path should be needed for authoring.

When testing the skill with an agent, use a concrete request with redacted API
fixtures: create a token feed, add article navigation, or diagnose a failed
capability declaration. Check the generated artifacts and behavior, not whether
the answer repeats the skill's wording. Ensure the agent reads only relevant
references, reports missing API evidence, and does not publish without authority.

## Delivery evidence template

```text
Plugin revision / API version:
Implemented features and capability mapping:
Syntax / manifest / registration checks:
Fixture tests and executed counts:
Real QuickJS / GitHub importer results:
Live website and platform UI checks:
Unverified assumptions / steps still requiring an account or device:
GitHub repository and stable tag, if publication was authorized and completed:
```

## Coverage map

| Documentation requirement | Resource / example |
| --- | --- |
| Quick start and AI workflow | README.md, SKILL.md, GitHub template |
| Manifest fields, defaults, validation | manifest.md; template and Pixelfed manifests |
| Capability/UI matrix and all twelve services | capabilities.md, recipes.md |
| QuickJS, HTTP, credentials, permissions | runtime-http.md; template requestJson helper |
| Five login methods and detection | authentication.md; template token login and Pixelfed OAuth |
| Mapping, identity, paging, tokens | wire-data.md; template and Pixelfed |
| Advanced features, guest, localization, Deep Links | recipes.md |
| Failure diagnosis, limits, recovery | troubleshooting.md |
| Verification and evidence boundaries | this document; Host SDK tests |
| GitHub distribution and schema upgrades | publishing.md; ABI.md |
