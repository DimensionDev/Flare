# Build a plugin in Flare

Ask Flare's AI assistant to create or update a plugin from any conversation. The
**Settings → Plugins → + → Create with Flare AI** shortcut opens the ordinary assistant;
it does not create a plugin. Native macOS shows the add actions directly as buttons
at the top of the Plugins settings pane. The AI option is shown only when AI Agent
is enabled. The assistant uses the existing Agent provider/model settings and can
use plugin tools alongside the normal app tools.

Plugins are independent local resources. A creation tool call assigns immutable
plugin and platform IDs. Repeating the same creation request key returns the same
plugin, including after a restart. Different requests can create multiple plugins
with the same display name. Tools list and manage only editable local plugins;
imported and GitHub-installed plugins are outside their scope.

An external package cannot replace an editable local plugin with the same ID;
delete the local plugin first to install that package. Creation also rejects IDs
already used by imported plugins. Ownership is checked again when committing an
installation, so a pending review cannot bypass this boundary.

The assistant reads a plugin's complete source and revision before submitting a
replacement. Each accepted update atomically saves the complete file set and
increments its revision. Concurrent updates to the same plugin are serialized;
an outdated expected revision is rejected with the current revision. The caller
must reread and reassess its changes. There are no chat owners, edit leases or
session-wide locks, and no new limit on concurrent plugins.

Validation and application run in the plugin's application scope immediately
after submission. They do not wait for an AI reply to finish. The tool returns
the accepted revision and status; `list_plugins` reports subsequent status and
errors. Stopping, closing or deleting a conversation does not cancel accepted
updates. Opening a different conversation can manage the same plugin by ID.
Plugin names do not control chat titles.

Local AI plugins are enabled by default and automatically approved after source
validation, including permission and credential format changes. Login, upload and
writes already in progress still delay switching; Flare does not cancel or repeat
them. Pending updates can be cancelled from Settings without opening a conversation.
File and GitHub installations continue to require the normal installation review.
The plugin management UI does not expose enable/disable switches.

Invalid sources remain saved and editable while the last working installation
continues running. A failed update restores the working version. **Cancel update**
discards pending activation without affecting conversations or an applied version.
Restart restores the last applied version and saved source, without automatically
applying an unfinished update. Publishing availability follows the current plugin's
capabilities, including changes applied by updates or a new login.

Deleting a plugin immediately removes its source, installation, accounts,
credentials and account data. Shared drafts and attachments remain while another
account uses them. The deletion tool requires the user to select the host-provided
confirmation button for the exact plugin revision. The host controls its deletion
warning and destructive action type; a model-supplied confirmation flag or text
reply alone cannot authorize deletion. Approval is single-use and expires with
that execution attempt, including startup failure or cancellation. A source change
requires a new confirmation. Stop directory development before deleting.

Flare validates account ownership and records removal before deleting account
data. If that preparation fails, the working plugin and its accounts remain.
Once removal is recorded, an account-cleanup or final-index-write failure leaves
the plugin stopped, including after restart. Delete the same management entry
again to finish removal; installation and updates cannot reactivate it while
removal is incomplete. If only unused package-file cleanup fails after deletion
finishes, retry the management cleanup action.

Android uses Compose settings components, iOS and native macOS use SwiftUI, and
Compose Desktop uses Fluent components. All four use the same authoring references, validation,
platform JavaScript runtime, version checks and permission rules. No data migration or legacy
chat compatibility layer is included for the development-only previous workflow.

Provide public API documentation or redacted response examples where needed. Log
in through Flare's account UI; never paste credentials into a conversation or
source. Plugin tools provide only scoped text files, local authoring references and a bounded
HTTPS documentation reader, with no shell or general filesystem access. The
assistant writes classic JavaScript directly, without Node/npm/TypeScript on the
device. The host supplies a default icon. `read_plugin_documentation` also returns icons
from the website head, resolving relative URLs against the final page URL and its
HTML base. `write_plugin_files` accepts an optional public HTTPS `iconUrl`; omitted
URLs preserve the current icon without another download. PNG, JPEG, WebP, GIF and
ICO are stored unchanged (at most 2 MiB and 1024×1024); SVG is not supported.
Downloads and validation belong to the accepted source revision. A failure leaves
the previous running package untouched, and stale work cannot overwrite a newer
revision. Saved icon bytes survive restart and are never returned to the model.
There is no remote image loading on the UI path or fallback to a World icon.
Validation checks the package and runtime contract, not live website behavior; runtime errors do not trigger AI edits.

`read_plugin_sdk` lists the local reference files and reads one requested document,
with line pagination. Start with its `README.md`, the in-app tool workflow from
`agent/README.md`. The build includes an explicit set of shared SDK contracts,
types and the small token-login template. It extracts the registration/runtime
ABI and compatibility rules without the external installation/publication flow,
and adapts references to the available files. Documents are assembled only when
read; listing paths or supplying the default icon does not assemble the corpus.
No SDK download or remote cache is used. The complete external SDK and skill at
`sdk/skill/flare-plugin/`, including publishing tools and Pixelfed, remain intact.
