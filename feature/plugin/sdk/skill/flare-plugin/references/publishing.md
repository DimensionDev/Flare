# GitHub publication and upgrades

GitHub is the recommended distribution path. Flare installs from a public
`https://github.com/OWNER/REPOSITORY` URL (optional `.git` suffix), with one plugin
at the repository root:

```text
manifest.json
plugin.js
assets/icon.png
locales/en.json       # optional; other BCP-47 catalogs as needed
README.md            # author/user instructions, ignored by the importer
```

Development files may coexist in the repository. Flare selects the plugin files;
it does not run a package manager, build script, or client-side bundler. Commit
the already-built classic script, including all runtime dependencies. A Flare
monorepo URL pointing to an example subdirectory is not a valid installation URL:
copy that example's contents into a repository you own first.

## Prepare and publish

1. Adapt the website endpoints and verify the advertised features. Replace the
   template IDs, name, description, and placeholder icon before publication.
   Keep real tokens, Cookies, passwords, and private fixtures out of Git history.
2. Put the required files at root and check that a clean checkout contains them.
   Include README instructions for supported website/instances, token acquisition
   if relevant, features, permissions, and tests actually performed.
3. Set a stable SemVer manifest version, for example `0.1.0`. Publish a tag
   `0.1.0` or `v0.1.0` on the commit with those files. Removing the optional
   **lowercase** `v` must exactly match manifest.version. Follow the same rule for
   any permitted build metadata; plain major.minor.patch is the simplest choice.
4. Share the repository URL. A GitHub Release page and uploaded `.fpp` asset are
   not required. Creating/pushing the repository or tag follows the user's
   authorization; a generated project alone is not permission to publish it.
5. The user selects the version in Flare plugin management, reviews source and
   permissions, confirms installation, then restarts Flare. Verify the actual
   selected tag/version and running behavior after restart.

Private repositories, arbitrary branch tracking, prerelease tags, automatic
installation, and build-on-device are unsupported. A public repo without a stable
version tag has no installable version, even if its default branch has valid files.

Flare resolves the selected tag to a commit SHA and downloads that source ZIP.
Archive checks also inspect ignored development files for unsafe paths/symlinks,
duplicates, multiple roots, and size limits. Do not commit node_modules, build
caches, or unrelated large assets. See [limits](troubleshooting.md#limits).

## Updates and compatibility

Increment manifest.version and publish a new stable tag. Preserve plugin ID,
platform ID, and the GitHub repository identity for an in-place update. Flare
records repository ID, owner/name, tag, and SHA. A moved tag/source change or new
permission is surfaced in review; prefer a new tag over rewriting a published
one. Failure/cancellation leaves the installed package intact.

Opening management checks for updates using a five-minute metadata cache;
manual checking bypasses it. Discovery only offers an update; download/review/
confirmation are separate steps. GitHub metadata uses global proxy routing and
rate-limit handling, not plugin account credentials. Excessive tag lists produce
an error rather than an incomplete “latest version” answer.

Install, update, enable, disable, and uninstall alter Desired State and take
effect after restart. There are no plugin lifecycle/migration callbacks. Media
grants follow the running package; cached media retains plugin ownership and
rechecks that package's permission. Missing plugins cannot restore their media.

| Change | Author consequence |
| --- | --- |
| Credential payload format becomes incompatible | Increment credentialSchemaVersion; existing accounts require relogin, with no plugin migration hook |
| Timeline IDs/parameters change incompatibly | Consider timelineSchemaVersion; it changes timeline identities and retires old specs. Explain that existing selections may need reconfiguration |
| Capabilities removed | Running capabilities are filtered by the new manifest |
| Operations newly declared compared with the account's saved manifest | Current Host admits those new operations without re-negotiating login; do not assume a new login will gate them |
| Previously declared operation was not negotiated for the account | It remains unavailable unless account negotiation changes |
| Package hash changes | Account adapter invalidates cached content when refreshing its persisted package snapshot |

Treat website/credential compatibility deliberately. A version bump alone does
not convert stored credentials. Document new permissions and user-visible changes.
The Host's own cache-database migration is separate from a plugin's versioning;
plugin authors do not control it.

## Optional local installation

For offline development or local transfer, the skill includes Node's standard-
library-only packer. From this skill directory:

```sh
node pack.mjs assets/github-template /tmp/example-feed-0.1.0.fpp
```

For an adapted plugin repository, create a staging directory containing only
manifest.json, plugin.js, assets/icon.png, and optional locales/*.json, then pass
that directory to pack.mjs. The packer ignores only a root README.md; unlike
GitHub import it rejects other development files, including .git, tests, and type
declarations. Keep the output outside the input directory. Packaging success is
not full manifest/registration/behavior validation. Local packages are unverified
and still require review and restart; no GitHub source/update tracking is recorded.
