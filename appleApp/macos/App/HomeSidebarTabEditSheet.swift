import FlareAppleCore
import FlareAppleUI
import Foundation
import KotlinSharedUI
import SwiftUI

struct HomeSidebarTabEditSheet: View {
    @Environment(\.timelineAppearance) private var baseTimelineAppearance

    let tab: UiTimelineTabItem
    let onCancel: () -> Void
    let onSave: (UiTimelineTabItem) -> Void

    @StateObject private var presenter: KotlinPresenter<EditTabPresenterState>
    @StateObject private var groupConfigPresenter: KotlinPresenter<GroupConfigPresenterState>
    @State private var text: String
    @State private var enabled: Bool
    @State private var mergePolicy: TimelineMergePolicy
    @State private var groupChildren: [UiTimelineTabItem]
    @State private var filterConfig: TimelineFilterConfig
    @State private var appearancePatch: AppearancePatch
    @State private var showAddTabPopover = false

    init(
        tab: UiTimelineTabItem,
        onCancel: @escaping () -> Void,
        onSave: @escaping (UiTimelineTabItem) -> Void
    ) {
        let editableGroup = tab as? UiGroupTimelineTabItem

        self.tab = tab
        self.onCancel = onCancel
        self.onSave = onSave
        self._presenter = StateObject(wrappedValue: KotlinPresenter(presenter: EditTabPresenter(tabItem: tab)))
        self._groupConfigPresenter = StateObject(wrappedValue: KotlinPresenter(presenter: GroupConfigPresenter()))
        self._text = State(initialValue: tab.title.text)
        self._enabled = State(initialValue: tab.enabled)
        self._mergePolicy = State(initialValue: editableGroup?.mergePolicy ?? .timePerPage)
        self._groupChildren = State(initialValue: editableGroup.map { Array($0.children) } ?? [])
        self._filterConfig = State(initialValue: tab.filterConfig)
        self._appearancePatch = State(
            initialValue: tab.appearancePatch ?? TimelinePresentationAppearancePatchHelper.shared.empty
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 0) {
                titleAndIconPane

                Divider()

                settingsPane
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            Divider()

            HStack {
                Spacer()

                Button("cancel_button") {
                    onCancel()
                }

                Button("done_button") {
                    save()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(!canSave)
            }
            .padding(16)
        }
    }

    private var titleAndIconPane: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("tab_settings_edit_title_header")
                .font(.headline)

            TextField(titlePlaceholder, text: $text)
                .textFieldStyle(.roundedBorder)

            Divider()

            HStack(spacing: 10) {
                TabIcon(icon: presenter.state.icon, size: 36)
                    .frame(width: 40, height: 40)

                VStack(alignment: .leading, spacing: 14) {
                    Text("tab_settings_edit_icon_header")
                        .font(.headline)

                    if presenter.state.canUseAvatar {
                        Toggle(isOn: Binding(
                            get: { presenter.state.withAvatar },
                            set: { presenter.state.setWithAvatar(value: $0) }
                        )) {
                            Text("tab_settings_edit_use_avatar")
                        }
                    }
                }
            }

            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 40), spacing: 8)], spacing: 8) {
                    ForEach(presenter.state.availableIcons, id: \.description) { icon in
                        Button {
                            presenter.state.setIcon(value: icon)
                        } label: {
                            TabIcon(icon: icon, size: 24)
                                .frame(width: 34, height: 34)
                                .padding(3)
                                .background {
                                    if presenter.state.icon.description == icon.description {
                                        RoundedRectangle(cornerRadius: 6)
                                            .fill(Color.accentColor.opacity(0.16))
                                    }
                                }
                                .overlay {
                                    if presenter.state.icon.description == icon.description {
                                        RoundedRectangle(cornerRadius: 6)
                                            .stroke(Color.accentColor, lineWidth: 1)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .padding(18)
        .frame(width: 280)
        .frame(maxHeight: .infinity, alignment: .topLeading)
    }

    private var settingsPane: some View {
        Form {
            if !tab.isSystemHomeMixedTimeline || editableGroup != nil {
                Section {
                    if !tab.isSystemHomeMixedTimeline {
                        Toggle(isOn: $enabled) {
                            MacSidebarSettingLabel(
                                "tab_settings_enabled",
                                subtitle: "tab_settings_enabled_desc"
                            )
                        }
                    }

                    if editableGroup != nil {
                        MacSidebarMergePolicyPicker(selected: $mergePolicy)
                    }
                }
            }

            groupChildrenSection

            Section {
                MacSidebarFilterEditor(filterConfig: $filterConfig)
            }

            Section("appearance_title") {
                MacSidebarLayoutAppearanceOverrideGroup(
                    timelineAppearance: resolvedTimelineAppearance,
                    appearancePatch: $appearancePatch
                )
                MacSidebarDisplayAppearanceOverrideGroup(
                    timelineAppearance: resolvedTimelineAppearance,
                    appearancePatch: $appearancePatch
                )
                MacSidebarMediaAppearanceOverrideGroup(
                    timelineAppearance: resolvedTimelineAppearance,
                    appearancePatch: $appearancePatch
                )
                MacSidebarThemeAppearanceOverrideGroup(
                    timelineAppearance: resolvedTimelineAppearance,
                    appearancePatch: $appearancePatch
                )
            }
        }
        .formStyle(.grouped)
    }

    @ViewBuilder
    private var groupChildrenSection: some View {
        if editableGroup != nil {
            Section("tab_settings_group_tabs") {
                if groupChildren.isEmpty {
                    Text("tab_settings_group_empty")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(Array(groupChildren.enumerated()), id: \.element.id) { index, child in
                        HomeSidebarGroupChildEditorRow(
                            tab: child,
                            canMoveUp: index > 0,
                            canMoveDown: index < groupChildren.count - 1,
                            onMoveUp: {
                                moveChild(from: index, to: index - 1)
                            },
                            onMoveDown: {
                                moveChild(from: index, to: index + 1)
                            },
                            onDelete: {
                                removeChild(child)
                            }
                        )
                    }
                }

                Button {
                    showAddTabPopover.toggle()
                } label: {
                    Label {
                        Text("tab_settings_add_tab")
                    } icon: {
                        Image(fontAwesome: .plus)
                    }
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showAddTabPopover, arrowEdge: .trailing) {
                    HomeSidebarAddTabPopover(
                        selectedTabs: groupChildren,
                        onAddGroup: nil,
                        onAdd: addChild,
                        onDelete: removeChild
                    )
                    .frame(width: 360, height: 460)
                }
            }
        }
    }

    private var normalizedTitle: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var editableGroup: UiGroupTimelineTabItem? {
        guard !tab.isSystemHomeMixedTimeline else {
            return nil
        }
        return tab as? UiGroupTimelineTabItem
    }

    private var titlePlaceholder: LocalizedStringKey {
        editableGroup == nil ? "tab_settings_edit_title_placeholder" : "tab_settings_group_name_placeholder"
    }

    private var canSave: Bool {
        if editableGroup != nil {
            return !groupChildren.isEmpty
        }
        return !normalizedTitle.isEmpty
    }

    private var resolvedTimelineAppearance: TimelineAppearance {
        TimelinePresentationAppearancePatchHelper.shared.resolve(
            base: baseTimelineAppearance,
            patch: appearancePatch
        )
    }

    private func save() {
        if let editableGroup {
            if let updated = groupConfigPresenter.state.buildGroupItem(
                initialItem: editableGroup,
                name: normalizedTitle,
                icon: presenter.state.icon,
                appearancePatch: appearancePatch,
                enabled: enabled,
                tabs: groupChildren,
                mergePolicy: mergePolicy,
                filterConfig: filterConfig,
                defaultGroupName: String(localized: "tab_settings_group_default_name", bundle: .main)
            ) {
                onSave(updated)
            }
        } else {
            onSave(
                tab.withPresentationOverrides(
                    title: normalizedTitle,
                    icon: presenter.state.icon,
                    appearancePatch: appearancePatch,
                    enabled: enabled,
                    filterConfig: filterConfig
                )
            )
        }
    }

    private func addChild(_ tab: UiTimelineTabItem) {
        guard !groupChildren.contains(where: { $0.id == tab.id }) else {
            return
        }
        groupChildren.append(tab)
    }

    private func removeChild(_ tab: UiTimelineTabItem) {
        groupChildren.removeAll { $0.id == tab.id }
    }

    private func moveChild(from source: Int, to destination: Int) {
        guard groupChildren.indices.contains(source),
              groupChildren.indices.contains(destination)
        else {
            return
        }

        let child = groupChildren.remove(at: source)
        groupChildren.insert(child, at: destination)
    }
}

private struct HomeSidebarGroupChildEditorRow: View {
    let tab: UiTimelineTabItem
    let canMoveUp: Bool
    let canMoveDown: Bool
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Label {
                TimelineTabTitle(title: tab.title)
            } icon: {
                TabIcon(tabItem: tab)
            }

            Spacer()

            Button(action: onMoveUp) {
                Image(systemName: "chevron.up")
            }
            .buttonStyle(.borderless)
            .disabled(!canMoveUp)
            .help(Text("macos_action_move_up"))

            Button(action: onMoveDown) {
                Image(systemName: "chevron.down")
            }
            .buttonStyle(.borderless)
            .disabled(!canMoveDown)
            .help(Text("macos_action_move_down"))

            Button(role: .destructive, action: onDelete) {
                Image(fontAwesome: .trash)
                    .foregroundStyle(.red)
            }
            .buttonStyle(.borderless)
            .help(Text("delete_button"))
        }
    }
}

struct MacSidebarMergePolicyPicker: View {
    @Binding var selected: TimelineMergePolicy

    var body: some View {
        Picker(selection: $selected) {
            Text("tab_settings_merge_policy_time").tag(TimelineMergePolicy.time)
            Text("tab_settings_merge_policy_time_per_page").tag(TimelineMergePolicy.timePerPage)
            Text("tab_settings_merge_policy_staggered").tag(TimelineMergePolicy.staggered)
        } label: {
            MacSidebarSettingLabel(
                "tab_settings_merge_policy",
                subtitle: "tab_settings_merge_policy_desc"
            )
        }
    }
}

struct MacSidebarSettingLabel: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey?

    init(_ title: LocalizedStringKey, subtitle: LocalizedStringKey? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct MacSidebarFilterEditor: View {
    @Binding var filterConfig: TimelineFilterConfig

    private let kindOptions: [TimelinePostKind] = [.reply, .repost, .quote]
    private let contentOptions: [TimelinePostContent] = [.text, .image, .video]

    var body: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                Text("tab_settings_filter_kind_group")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                ForEach(kindOptions, id: \.self) { kind in
                    Toggle(isOn: includedKindBinding(kind)) {
                        Text(kind.titleKey)
                    }
                }

                Text("tab_settings_filter_content_group")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)

                ForEach(contentOptions, id: \.self) { content in
                    Toggle(isOn: includedContentBinding(content)) {
                        Text(content.titleKey)
                    }
                }
            }
            .padding(.top, 6)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text("tab_settings_filter_title")
                Text(filterSummary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var filterSummary: LocalizedStringKey {
        if filterConfig.excludedKinds.isEmpty && filterConfig.excludedContents.isEmpty {
            return "tab_settings_filter_desc"
        }
        return "tab_settings_filter_configured"
    }

    private func includedKindBinding(_ kind: TimelinePostKind) -> Binding<Bool> {
        Binding(
            get: { !filterConfig.excludedKinds.contains(kind) },
            set: { included in
                var excludedKinds = filterConfig.excludedKinds
                if included {
                    excludedKinds.removeAll { $0 == kind }
                } else if !excludedKinds.contains(kind) {
                    excludedKinds.append(kind)
                }
                filterConfig = TimelineFilterConfig(
                    excludedKinds: excludedKinds,
                    excludedContents: filterConfig.excludedContents
                )
            }
        )
    }

    private func includedContentBinding(_ content: TimelinePostContent) -> Binding<Bool> {
        Binding(
            get: { !filterConfig.excludedContents.contains(content) },
            set: { included in
                var excludedContents = filterConfig.excludedContents
                if included {
                    excludedContents.removeAll { $0 == content }
                } else if !excludedContents.contains(content) {
                    excludedContents.append(content)
                }
                filterConfig = TimelineFilterConfig(
                    excludedKinds: filterConfig.excludedKinds,
                    excludedContents: excludedContents
                )
            }
        )
    }
}

private extension TimelinePostKind {
    var titleKey: LocalizedStringKey {
        switch self {
        case .reply:
            return "tab_settings_filter_reply"
        case .repost:
            return "tab_settings_filter_repost"
        case .quote:
            return "tab_settings_filter_quote"
        default:
            return "tab_settings_filter_reply"
        }
    }
}

private extension TimelinePostContent {
    var titleKey: LocalizedStringKey {
        switch self {
        case .text:
            return "tab_settings_filter_text_only"
        case .image:
            return "tab_settings_filter_image"
        case .video:
            return "tab_settings_filter_video"
        default:
            return "tab_settings_filter_text_only"
        }
    }
}
