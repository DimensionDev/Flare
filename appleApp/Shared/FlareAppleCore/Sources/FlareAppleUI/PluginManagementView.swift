import FlareAppleCore
@preconcurrency import KotlinSharedUI
import Foundation
import SwiftUI
import UniformTypeIdentifiers

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public struct PluginManagementView: View {
    @State private var facade: PluginAppleFacadeV1
    @State private var presenter: KotlinPresenter<PluginManagementStateV1>
    @State private var importsPlugin = false
    @State private var createdPluginConversation: String?
    @State private var showsGitHubInstall = false
    @State private var gitHubURL = ""
    @State private var selectedGitHubTag = ""
    @State private var fileError: String?
    @State private var pendingUninstallId: String?
    @State private var pendingUninstallName: String?
    #if os(macOS)
    @State private var showsDevelopment = false
    #endif

    public init() {
        let facade = PluginAppleFacadeV1()
        _facade = State(initialValue: facade)
        _presenter = State(initialValue: KotlinPresenter(presenter: facade))
    }

    public var body: some View {
        List {
            Button("plugin_ai_create") {
                Task {
                    do { createdPluginConversation = try await facade.createPlugin() }
                    catch { fileError = error.localizedDescription }
                }
            }
            ForEach(presenter.state.authoredPlugins, id: \.id) { plugin in
                NavigationLink {
                    AuthoredPluginChatView(conversationId: plugin.conversationId)
                } label: {
                    VStack(alignment: .leading) {
                        Text(plugin.name.isEmpty ? String(localized: "plugin_ai_untitled") : plugin.name)
                        Text(pluginAuthoringLabel(plugin.phase)).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .contextMenu {
                    Button("plugin_uninstall", role: .destructive) {
                        pendingUninstallId = plugin.id
                        pendingUninstallName = plugin.name
                    }
                }
            }
            #if os(macOS)
            if showsDevelopment || presenter.state.development.directory != nil {
                developmentSection
            }
            #endif
            if presenter.state.requiresRestart {
                Section {
                    Label {
                        Text("plugin_restart_required")
                    } icon: {
                        Image(systemName: "arrow.clockwise.circle")
                    }
                    .foregroundStyle(.tint)
                }
            }

            if presenter.state.error != nil || fileError != nil {
                Section {
                    Label("plugin_operation_failed", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                    if let error = presenter.state.error {
                        Text(String(error.prefix(512))).font(.caption)
                    }
                }
            }

            if presenter.state.updatesChecked && presenter.state.plugins.allSatisfy({ $0.availableUpdate == nil }) {
                Label("plugin_up_to_date", systemImage: "checkmark.circle")
            }

            if !presenter.state.issues.isEmpty || !presenter.state.runtimeIssues.isEmpty {
                Section("plugin_issues") {
                    ForEach(Array(presenter.state.issues.enumerated()), id: \.offset) { _, issue in
                        Text(issueText(issue.code))
                            .foregroundStyle(.red)
                    }
                    ForEach(presenter.state.runtimeIssues, id: \.pluginId) { issue in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(issue.pluginId)
                                Text(runtimeIssueText(issue.code))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button("plugin_retry") {
                                Task { try? await facade.retryRuntime(pluginId: issue.pluginId) }
                            }
                            .disabled(presenter.state.busy)
                        }
                    }
                }
            }

            if presenter.state.plugins.isEmpty && presenter.state.authoredPlugins.isEmpty {
                ContentUnavailableView(
                    "plugin_empty",
                    systemImage: "puzzlepiece.extension",
                    description: Text("settings_plugins_subtitle")
                )
                .listRowBackground(Color.clear)
            } else {
                Section {
                    ForEach(presenter.state.plugins.filter { installed in !presenter.state.authoredPlugins.contains { $0.id == installed.pluginId } }, id: \.pluginId) { plugin in
                        pluginRow(plugin)
                    }
                }
            }
        }
        .navigationTitle("settings_plugins_title")
        .navigationDestination(item: $createdPluginConversation) { conversationId in
            AuthoredPluginChatView(conversationId: conversationId)
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    importsPlugin = true
                } label: {
                    Label("plugin_install", systemImage: "plus")
                }
                .disabled(presenter.state.busy)
            }
            ToolbarItem(placement: .automatic) {
                Menu {
                    #if os(macOS)
                    Button("plugin_development") {
                        facade.prepareDevelopment()
                        showsDevelopment = true
                    }
                    #endif
                    Button("plugin_install_github") {
                        gitHubURL = ""
                        selectedGitHubTag = ""
                        facade.clearGitHubRepository()
                        showsGitHubInstall = true
                    }
                    Button("plugin_check_updates") {
                        Task { try? await facade.checkUpdates(forceRefresh: true) }
                    }
                    .disabled(!presenter.state.plugins.contains { $0.sourceUrl != nil })
                    if presenter.state.canRebuildIndex {
                        Button("plugin_rebuild_index") {
                            Task { try? await facade.rebuildIndex() }
                        }
                    }
                    Button("plugin_cleanup") {
                        Task { try? await facade.cleanup() }
                    }
                    .disabled(presenter.state.canRebuildIndex)
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .disabled(presenter.state.busy)
            }
        }
        .overlay {
            if presenter.state.busy {
                ProgressView()
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
            }
        }
        .fileImporter(
            isPresented: $importsPlugin,
            allowedContentTypes: [UTType(filenameExtension: "fpp") ?? .data],
            allowsMultipleSelection: false,
            onCompletion: importPlugin
        )
        .task { try? await facade.checkUpdates(forceRefresh: false) }
        .sheet(isPresented: $showsGitHubInstall) {
            gitHubInstallSheet
        }
        .alert(
            presenter.state.pendingInstall.map { "\($0.name) \($0.version)" } ?? "",
            isPresented: Binding(
                get: { presenter.state.pendingInstall != nil },
                set: { _ in }
            )
        ) {
            Button("Cancel", role: .cancel) {
                Task { try? await facade.cancelInstall() }
            }
            .disabled(presenter.state.busy)
            Button("plugin_confirm_install") {
                Task { try? await facade.confirmInstall() }
            }
            .disabled(presenter.state.busy)
        } message: {
            if let review = presenter.state.pendingInstall {
                Text(installReviewText(review))
            }
        }
        .alert(
            "plugin_uninstall_confirm_title",
            isPresented: Binding(
                get: { pendingUninstallId != nil },
                set: { visible in
                    if !visible {
                        pendingUninstallId = nil
                        pendingUninstallName = nil
                    }
                }
            )
        ) {
            Button("Cancel", role: .cancel) {
                pendingUninstallId = nil
                pendingUninstallName = nil
            }
            .disabled(presenter.state.busy)
            Button("plugin_uninstall", role: .destructive) {
                guard let pluginId = pendingUninstallId else { return }
                pendingUninstallId = nil
                pendingUninstallName = nil
                Task { try? await facade.uninstall(pluginId: pluginId) }
            }
            .disabled(presenter.state.busy)
        } message: {
            Text(
                String(
                    format: presenter.state.authoredPlugins.contains { $0.id == pendingUninstallId } ? String(localized: "plugin_ai_delete_message") : String(localized: "plugin_uninstall_confirm_message"),
                    pendingUninstallName ?? ""
                )
            )
        }
    }

    @ViewBuilder
    private func pluginRow(_ plugin: PluginManagementItemV1) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                pluginIcon(path: plugin.iconPath)
                    .frame(width: 40, height: 40)
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(plugin.nameText.text)
                        .font(.headline)
                    Text("\(plugin.platformId) · \(plugin.version)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(plugin.running ? "plugin_running" : "plugin_inactive")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Toggle(
                    "",
                    isOn: Binding(
                        get: { plugin.enabled },
                        set: { enabled in
                            Task {
                                try? await facade.setEnabled(pluginId: plugin.pluginId, enabled: enabled)
                            }
                        }
                    )
                )
                .labelsHidden()
                .disabled(presenter.state.busy || isDeveloping(plugin.pluginId))
            }

            if plugin.pendingRestart {
                Text("plugin_restart_required")
                    .font(.caption)
                    .foregroundStyle(.tint)
            }

            if let source = plugin.sourceUrl {
                Text(source).font(.caption).foregroundStyle(.secondary)
            }
            if let update = plugin.availableUpdate {
                Button {
                    Task { try? await facade.inspectGitHubUpdate(pluginId: plugin.pluginId) }
                } label: {
                    Text("plugin_update") + Text(" " + update.tag)
                }
                .disabled(presenter.state.busy || isDeveloping(plugin.pluginId))
            }

            Button("plugin_uninstall", role: .destructive) {
                pendingUninstallId = plugin.pluginId
                pendingUninstallName = plugin.nameText.text
            }
            .disabled(presenter.state.busy || isDeveloping(plugin.pluginId))
        }
        .padding(.vertical, 4)
    }

    private func isDeveloping(_ pluginId: String) -> Bool {
        presenter.state.development.active && presenter.state.development.pluginId == pluginId
    }

    #if os(macOS)
    private var developmentSection: some View {
        let state = presenter.state.development
        return Section("plugin_development") {
            Text("plugin_development_description").foregroundStyle(.secondary)
            if let directory = state.directory ?? state.recentDirectory {
                Text(directory).font(.caption).textSelection(.enabled)
            }
            if state.active {
                Text("\(state.pluginId ?? "") · #\(state.generation)")
            } else if let pluginId = state.pluginId {
                Text("\(state.name ?? pluginId) (\(pluginId))")
            }
            if state.validating {
                ProgressView("plugin_development_validating")
            } else if state.candidateHash != nil {
                Text(state.active && state.candidateHash == state.runningHash
                     ? "plugin_development_current" : "plugin_development_ready")
            }
            if let error = state.error {
                Text(error).font(.caption).foregroundStyle(.red).textSelection(.enabled)
            }
            HStack {
                Button("plugin_development_open", action: openDevelopmentDirectory)
                    .disabled(state.busy || state.active)
                Button(state.active ? "plugin_development_reload" : "plugin_development_start") {
                    Task { try? await facade.reloadDevelopment(confirmed: false) }
                }
                .disabled(state.busy || state.validating || state.candidateHash == nil)
                if state.directory != nil {
                    Button("plugin_development_stop") {
                        Task {
                            do {
                                try await facade.stopDevelopment()
                                PluginDevelopmentDirectoryAccess.release()
                            } catch { /* The shared state displays the failure. */ }
                        }
                    }
                    .disabled(state.busy)
                }
                if state.busy { ProgressView().controlSize(.small) }
            }
            if !state.messages.isEmpty {
                DisclosureGroup("plugin_development_diagnostics") {
                    Text(state.messages.joined(separator: "\n"))
                        .font(.caption.monospaced()).textSelection(.enabled)
                }
            }
        }
        .alert("plugin_development", isPresented: Binding(
            get: { presenter.state.development.reviewHash != nil },
            set: { _ in }
        )) {
            Button("Cancel", role: .cancel) { facade.cancelDevelopmentReview() }
            Button(state.active ? "plugin_development_reload" : "plugin_development_start") {
                Task { try? await facade.reloadDevelopment(confirmed: true) }
            }
            .disabled(state.busy)
        } message: {
            Text(([String(localized: "plugin_development_review")]
                  + state.permissions.map { "• " + pluginPermissionText($0) }
                  + (state.credentialSchemaChanged ? [String(localized: "plugin_development_schema")] : []))
                .joined(separator: "\n"))
        }
    }

    private func openDevelopmentDirectory() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = true
        panel.canChooseFiles = false
        panel.allowsMultipleSelection = false
        if let recent = presenter.state.development.recentDirectory {
            panel.directoryURL = URL(fileURLWithPath: recent, isDirectory: true)
        }
        guard panel.runModal() == .OK, let url = panel.url else { return }
        PluginDevelopmentDirectoryAccess.retain(url)
        Task { try? await facade.openDevelopmentDirectory(path: url.path(percentEncoded: false)) }
    }
    #endif

    private var selectedGitHubVersion: GitHubPluginVersionV1? {
        guard let versions = presenter.state.githubCatalog?.versions else { return nil }
        return versions.first { $0.tag == selectedGitHubTag } ?? versions.first
    }

    private var gitHubInstallSheet: some View {
        NavigationStack {
            Form {
                TextField("plugin_github_repository", text: $gitHubURL, prompt: Text("https://github.com/owner/repository"))
                    .autocorrectionDisabled()
                    .onChange(of: gitHubURL) { _, _ in
                        selectedGitHubTag = ""
                        facade.clearGitHubRepository()
                    }
                Button("plugin_load_versions") {
                    Task { try? await facade.loadGitHubRepository(url: gitHubURL) }
                }
                .disabled(gitHubURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                if let catalog = presenter.state.githubCatalog {
                    Picker("plugin_version", selection: Binding(
                        get: { selectedGitHubVersion?.tag ?? "" },
                        set: { selectedGitHubTag = $0 }
                    )) {
                        ForEach(catalog.versions, id: \.tag) { version in
                            Text(version.tag).tag(version.tag)
                        }
                    }
                }
                if let error = presenter.state.error {
                    Text(String(error.prefix(512))).foregroundStyle(.red)
                }
            }
            .disabled(presenter.state.busy)
            .navigationTitle("plugin_install_github")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showsGitHubInstall = false }
                        .disabled(presenter.state.busy)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("plugin_confirm_install") {
                        guard let version = selectedGitHubVersion else { return }
                        showsGitHubInstall = false
                        Task { try? await facade.inspectGitHub(tag: version.tag) }
                    }
                    .disabled(presenter.state.busy || selectedGitHubVersion == nil)
                }
            }
            .overlay { if presenter.state.busy { ProgressView() } }
        }
        .interactiveDismissDisabled(presenter.state.busy)
        #if os(macOS)
        .frame(minWidth: 440, minHeight: 280)
        #endif
    }

    private func importPlugin(_ result: Result<[URL], Error>) {
        switch result {
        case .failure:
            fileError = String(localized: "plugin_operation_failed")
        case .success(let urls):
            guard let url = urls.first else { return }
            fileError = nil
            Task { @MainActor in
                let accessed = url.startAccessingSecurityScopedResource()
                defer {
                    if accessed {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                do {
                    _ = try await facade.inspect(path: url.path(percentEncoded: false))
                } catch {
                    fileError = String(localized: "plugin_operation_failed")
                }
            }
        }
    }

    @ViewBuilder
    private func pluginIcon(path: String) -> some View {
        #if os(iOS)
        if let image = UIImage(contentsOfFile: path) {
            Image(uiImage: image)
                .resizable()
                .scaledToFit()
        } else {
            Image(systemName: "puzzlepiece.extension")
        }
        #elseif os(macOS)
        if let image = NSImage(contentsOfFile: path) {
            Image(nsImage: image)
                .resizable()
                .scaledToFit()
        } else {
            Image(systemName: "puzzlepiece.extension")
        }
        #else
        Image(systemName: "puzzlepiece.extension")
        #endif
    }

    private func issueText(_ code: String) -> String {
        let key: String
        switch code {
        case "index.corrupt":
            key = "plugin_issue_index_corrupt"
        case "package.missing-or-changed", "icon.missing-or-changed":
            key = "plugin_issue_files_missing"
        case "platform.conflict":
            key = "plugin_issue_platform_conflict"
        case "platform.invalid":
            key = "plugin_issue_platform_invalid"
        default:
            key = "plugin_operation_failed"
        }
        return String(localized: String.LocalizationValue(key))
    }

    private func runtimeIssueText(_ code: String) -> String {
        let key: String.LocalizationValue =
            code == "runtime.paused" ? "plugin_runtime_paused" : "plugin_runtime_fatal"
        return String(localized: key)
    }

    private func installReviewText(_ review: PluginInstallReviewV1) -> String {
        var lines = [review.sourceUrl == nil ? String(localized: "plugin_unverified_warning") : String(localized: "plugin_unverified_github_warning")]
        if let source = review.sourceUrl { lines.append(source) }
        if !review.capabilities.isEmpty {
            lines.append("")
            lines.append(String(localized: "plugin_capabilities"))
            lines.append(contentsOf: review.capabilities.map { "• \($0)" })
        }
        if !review.permissions.isEmpty {
            lines.append("")
            lines.append(String(localized: "plugin_permissions"))
            lines.append(contentsOf: review.permissions.map { "• " + pluginPermissionText($0) })
        }
        lines.append(contentsOf: review.warnings.compactMap(warningText))
        return lines.joined(separator: "\n")
    }

    private func warningText(_ warning: PluginInstallWarningV1) -> String? {
        let key: String
        switch warning.type {
        case .unverifiedLocal:
            return nil
        case .addedPermission:
            key = "plugin_warning_added_permission"
        case .downgrade:
            key = "plugin_warning_downgrade"
        case .sameVersionDifferentHash:
            key = "plugin_warning_changed_package"
        case .compatibility:
            key = "plugin_warning_compatibility"
        case .sourceChanged:
            key = "plugin_warning_source_changed"
        case .tagMoved:
            key = "plugin_warning_tag_moved"
        }
        let message = String(localized: String.LocalizationValue(key))
        let detail = warning.detail?.replacingOccurrences(
            of: "$accountOrigin",
            with: String(localized: "plugin_permission_account_origin_label")
        )
        return "• " + (detail.map { "\(message): \($0)" } ?? message)
    }
}

#if os(macOS)
// The directory watcher outlives this view; retain access until development stops.
@MainActor
private enum PluginDevelopmentDirectoryAccess {
    private static var url: URL?
    static func retain(_ selected: URL) {
        release()
        if selected.startAccessingSecurityScopedResource() { url = selected }
    }
    static func release() {
        url?.stopAccessingSecurityScopedResource()
        url = nil
    }
}
#endif

private struct AuthoredPluginChatView: View {
    @State private var presenter: KotlinPresenter<GenericChatPresenterState>

    init(conversationId: String) {
        _presenter = State(initialValue: KotlinPresenter(presenter: GenericChatPresenter(conversationId: conversationId, initialMessage: nil)))
    }

    var body: some View {
        AgentChatView(
            messages: presenter.state.messages,
            isRunning: presenter.state.room.isRunning,
            canSend: presenter.state.canSend,
            errorMessage: presenter.state.room.errorMessage,
            runningTrace: String(localized: "agent_chat_thinking"),
            inputPlaceholder: String(localized: "agent_chat_input_placeholder"),
            onInputChange: presenter.state.setInput,
            onSend: presenter.state.sendMessage,
            onInputRequestOptionSelected: presenter.state.selectInputRequestOption,
            onPostClick: { _ in },
            onUserClick: { _ in },
            pluginState: presenter.state
        )
        .navigationTitle(presenter.state.plugin?.name.isEmpty == false ? presenter.state.plugin!.name : String(localized: "plugin_ai_untitled"))
    }
}

func pluginPermissionText(_ permission: PluginSensitivePermissionV1) -> String {
    if permission.type == .mediaNetwork {
        return String(localized: "plugin_permission_media_network")
    }
    let accountToken = "$accountOrigin"
    let accountRelativePath =
        permission.origin == accountToken || permission.origin.hasPrefix(accountToken + "/")
            ? String(permission.origin.dropFirst(accountToken.count))
            : nil
    let origin = accountRelativePath.map {
        String(localized: "plugin_permission_account_origin_label") + $0
    } ?? permission.origin
    guard let cookieName = permission.cookieName else {
        return accountRelativePath == nil
            ? origin
            : String(localized: "plugin_permission_account_origin")
    }
    return String(
        format: String(localized: "plugin_permission_cookie"),
        origin,
        cookieName
    )
}
