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
continues running. A failed switch restores the working version. **Cancel update**
discards pending activation without affecting conversations or an applied version.
Disabling prevents source updates from enabling the plugin. Re-enabling restores
the last working installation and validates the saved source again. Restart
restores the last applied version and saved source, without automatically applying
an unfinished update. Deleting a plugin removes its source and installation while
retaining account records. Stop directory development before changing its enabled
state or deleting it.

Android uses Compose settings components, iOS and native macOS use SwiftUI, and
Compose Desktop uses Fluent components. All four use the same authoring references, validation,
QuickJS runtime, version checks and permission rules. No data migration or legacy
chat compatibility layer is included for the development-only previous workflow.

Provide public API documentation or redacted response examples where needed. Log
in through Flare's account UI; never paste credentials into a conversation or
source. Plugin tools provide only scoped text files, local authoring references and a bounded
HTTPS documentation reader, with no shell or general filesystem access. The
assistant writes classic JavaScript directly, without Node/npm/TypeScript on the
device. The host supplies a default icon. Validation checks the package and runtime
contract, not live website behavior; runtime errors do not trigger AI edits.

`read_plugin_sdk` lists the local reference files and reads one requested document,
with line pagination. Start with its `README.md`, the in-app tool workflow from
`agent/README.md`. The build includes an explicit set of shared SDK contracts,
types and the small token-login template. It extracts the registration/runtime
ABI and compatibility rules without the external installation/publication flow,
and adapts references to the available files. Documents are assembled only when
read; listing paths or supplying the default icon does not assemble the corpus.
No SDK download or remote cache is used. The complete external SDK and skill at
`sdk/skill/flare-plugin/`, including publishing tools and Pixelfed, remain intact.
