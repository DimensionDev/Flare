# Build a plugin in Flare

In **Settings → Plugins → Create with AI**, describe the website and the features
it should provide. This uses the existing Agent provider/model settings. Include
an API documentation URL or redacted response examples when the service is not
well documented. Log in through Flare's account UI; do not paste credentials into
the conversation.

A local plugin is created immediately. Open it from the plugin list to continue
editing. Multiple plugins can generate independently, without a new concurrency
limit or application queue. Each plugin permits one generation at a time. Existing
runtime memory and package/index size limits still apply.

Source files are saved automatically. After the Agent finishes, Flare validates
and applies the latest complete revision. The first network permission, additional
permissions and credential format changes require approval in the plugin status
panel. Approval applies only to that exact revision. A login, upload or write
already in progress delays switching; Flare does not cancel or repeat it.

Invalid changes and failed switches keep the previous working version. **Stop**
discards pending activation and cancels generation; it does not undo an already
applied version. Disabling prevents later generation results from enabling the
plugin. Restart restores the last applied version, without resuming unfinished
Agent tasks. Deleting a plugin removes its local source and installation, while
keeping account records and conversation history.

Re-enabling restores the last working installation while saved source is validated,
without creating another generation task or increasing the source revision. Unchanged
source keeps the restored session; changed source follows the same approval and
in-flight operation checks.
Stop directory development before changing an AI plugin's enabled state or deleting it.

Android uses the existing Compose settings/chat components; iOS and native macOS
use SwiftUI; Compose Desktop uses the existing Fluent components. The same SDK,
validator, QuickJS runtime and permission rules serve all four interfaces.

The Agent receives only the current plugin's text files, the bundled SDK and a
bounded HTTPS documentation reader. It has no shell, general filesystem, account
or activation tools. It writes classic JavaScript directly, with no on-device
Node/npm/TypeScript toolchain. The host supplies the default icon. A validation
pass checks the package and runtime contract, not the live website's behavior;
ordinary runtime/network errors do not start another generation automatically.
