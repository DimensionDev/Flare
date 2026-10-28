# Build a plugin in Flare

Ask Flare's AI assistant to create or update a plugin from any conversation. The
**Settings → Plugins → Create with AI** shortcut opens the ordinary assistant;
it does not create a plugin. The assistant uses the existing Agent provider/model
settings and can use plugin tools alongside the normal app tools.

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

In **Settings → Plugins**, review and approve the first network permission,
additional permissions, and credential format changes. Approval applies only to
the displayed revision and cannot be granted by AI tools. A newer source update
invalidates an older approval. Login, upload and writes already in progress delay
switching; Flare does not cancel or repeat them. Approval and pending updates can
be managed without reopening any conversation.

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
Compose Desktop uses Fluent components. All four use the same SDK, validation,
QuickJS runtime, version checks and permission rules. No data migration or legacy
chat compatibility layer is included for the development-only previous workflow.

Provide public API documentation or redacted response examples where needed. Log
in through Flare's account UI; never paste credentials into a conversation or
source. Plugin tools provide only scoped text files, the bundled SDK and a bounded
HTTPS documentation reader, with no shell or general filesystem access. The
assistant writes classic JavaScript directly, without Node/npm/TypeScript on the
device. The host supplies a default icon. Validation checks the package and runtime
contract, not live website behavior; runtime errors do not trigger AI edits.
