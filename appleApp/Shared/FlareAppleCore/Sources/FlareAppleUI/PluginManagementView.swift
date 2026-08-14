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
    @StateObject private var presenter: KotlinPresenter<PluginManagementStateV1>
    @State private var importsPlugin = false
    @State private var fileError: String?
    @State private var pendingUninstallId: String?
    @State private var pendingUninstallName: String?

    private var facade: PluginAppleFacadeV1 {
        presenter.presenter as! PluginAppleFacadeV1
    }

    public init() {
        let facade = PluginAppleFacadeV1()
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: facade))
    }

    public var body: some View {
        List {
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
                }
            }

            if !presenter.state.issues.isEmpty || !presenter.state.runtimeIssues.isEmpty {
                Section("plugin_issues") {
                    ForEach(presenter.state.issues, id: \.code) { issue in
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

            if presenter.state.plugins.isEmpty {
                ContentUnavailableView(
                    "plugin_empty",
                    systemImage: "puzzlepiece.extension",
                    description: Text("settings_plugins_subtitle")
                )
                .listRowBackground(Color.clear)
            } else {
                Section {
                    ForEach(presenter.state.plugins, id: \.pluginId) { plugin in
                        pluginRow(plugin)
                    }
                }
            }
        }
        .navigationTitle("settings_plugins_title")
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
            Button("plugin_confirm_install") {
                Task { try? await facade.confirmInstall() }
            }
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
            Button("plugin_uninstall", role: .destructive) {
                guard let pluginId = pendingUninstallId else { return }
                pendingUninstallId = nil
                pendingUninstallName = nil
                Task { try? await facade.uninstall(pluginId: pluginId) }
            }
        } message: {
            Text(
                String(
                    format: String(localized: "plugin_uninstall_confirm_message"),
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
                .disabled(presenter.state.busy)
            }

            if plugin.pendingRestart {
                Text("plugin_restart_required")
                    .font(.caption)
                    .foregroundStyle(.tint)
            }

            Button("plugin_uninstall", role: .destructive) {
                pendingUninstallId = plugin.pluginId
                pendingUninstallName = plugin.nameText.text
            }
            .disabled(presenter.state.busy)
        }
        .padding(.vertical, 4)
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
        var lines = [String(localized: "plugin_unverified_warning")]
        if !review.capabilities.isEmpty {
            lines.append("")
            lines.append(String(localized: "plugin_capabilities"))
            lines.append(contentsOf: review.capabilities.map { "• \($0)" })
        }
        if !review.permissions.isEmpty {
            lines.append("")
            lines.append(String(localized: "plugin_permissions"))
            lines.append(contentsOf: review.permissions.map { permission in
                let cookie = permission.cookieName.map { "/\($0)" } ?? ""
                return "• \(permission.origin)\(cookie)"
            })
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
        }
        let message = String(localized: String.LocalizationValue(key))
        return "• " + (warning.detail.map { "\(message): \($0)" } ?? message)
    }
}
