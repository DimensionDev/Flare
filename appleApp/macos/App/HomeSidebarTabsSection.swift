import FlareAppleCore
import FlareAppleUI
import Foundation
import KotlinSharedUI
import SwiftUI

struct HomeSidebarTabsSection: View {
    let title: String
    let icon: FontAwesomeIcon
    let liveTabs: [UiTimelineTabItem]
    @Binding var selectedTab: Route?
    @Binding var isExpanded: Bool
    let onEditTab: (UiTimelineTabItem, @escaping (UiTimelineTabItem) -> Void) -> Void

    @StateObject private var settingsPresenter = KotlinPresenter(presenter: HomeTabSettingsPresenter())
    @State private var isCustomizing = false
    @State private var editableTabs: [UiTimelineTabItem] = []
    @State private var addPopover: HomeSidebarAddPopover?

    var body: some View {
        Group {
            if !isCustomizing, liveTabs.count == 1, let tab = liveTabs.first {
                sidebarRow(for: tab, showsReorderHandle: false)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: .trailing) {
                        controls
                    }
            } else {
                groupedTabs
            }
        }
        .onAppear {
            editableTabs = liveTabs
            normalizeSelection(previousHomeTabIDs: nil)
        }
        .onChange(of: liveTabs.map(\.id)) { oldValue, _ in
            if !isCustomizing {
                editableTabs = liveTabs
            }
            normalizeSelection(previousHomeTabIDs: oldValue)
        }
    }

    private var groupedTabs: some View {
        DisclosureGroup(isExpanded: $isExpanded) {
            if isCustomizing {
                ForEach(editableTabs, id: \.id) { tab in
                    sidebarRow(for: tab, showsReorderHandle: true)
                }
                .onMove(perform: move)
            } else {
                ForEach(liveTabs, id: \.id) { tab in
                    sidebarRow(for: tab, showsReorderHandle: false)
                }
            }
        } label: {
            Label {
                Text(title)
            } icon: {
                Image(fontAwesome: icon)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .overlay(alignment: .trailing) {
                controls
            }
        }
    }

    @ViewBuilder
    private func sidebarRow(
        for tab: UiTimelineTabItem,
        showsReorderHandle: Bool
    ) -> some View {
        if let group = tab as? UiGroupTimelineTabItem, !tab.isSystemHomeMixedTimeline {
            HomeSidebarEditableGroupRow(
                group: group,
                showsReorderHandle: showsReorderHandle,
                onEdit: {
                    onEditTab(group) { updated in
                        update(tab, with: updated)
                    }
                },
                onDelete: {
                    remove(tab)
                }
            )
            .tag(Route.timeline(tab, isHome: true))
        } else {
            HomeSidebarEditableTabRow(
                tab: tab,
                showsReorderHandle: showsReorderHandle,
                onEdit: {
                    onEditTab(tab) { updated in
                        update(tab, with: updated)
                    }
                },
                onDelete: {
                    remove(tab)
                }
            )
            .tag(Route.timeline(tab, isHome: true))
        }
    }

    @ViewBuilder
    private var controls: some View {
        HStack(spacing: 6) {
            if isCustomizing {
                Menu {
                    Button {
                        addPopover = .tab
                    } label: {
                        Label {
                            Text("tab_settings_add_tab")
                        } icon: {
                            Image(fontAwesome: .plus)
                        }
                    }

                    Button {
                        addPopover = .group
                    } label: {
                        Label {
                            Text("tab_settings_add_group")
                        } icon: {
                            Image(fontAwesome: .tableList)
                        }
                    }
                } label: {
                    Image(fontAwesome: .plus)
                }
                .buttonStyle(.plain)
                .help(String(localized: "tab_settings_add_tab", bundle: .main))
                .popover(item: $addPopover, arrowEdge: .trailing) { popover in
                    switch popover {
                    case .tab:
                        HomeSidebarAddTabPopover(
                            selectedTabs: editableTabs,
                            onAddGroup: nil,
                            onAdd: add,
                            onDelete: remove
                        )
                        .frame(width: 360, height: 460)
                    case .group:
                        HomeSidebarCreateGroupPopover { group in
                            add(group)
                            addPopover = nil
                        }
                        .frame(width: 440, height: 560)
                    }
                }

                Button {
                    isCustomizing = false
                    addPopover = nil
                } label: {
                    Image(fontAwesome: .check)
                }
                .buttonStyle(.plain)
                .help(String(localized: "done", bundle: .main))
            } else {
                Button {
                    editableTabs = liveTabs
                    isCustomizing = true
                    isExpanded = true
                } label: {
                    Image(fontAwesome: .sliders)
                }
                .buttonStyle(.plain)
                .help(String(localized: "tab_settings_customize", bundle: .main))
            }
        }
    }

    private func add(_ tab: UiTimelineTabItem) {
        guard !editableTabs.contains(where: { $0.id == tab.id }) else {
            return
        }
        editableTabs.append(tab)
        persist()
    }

    private func remove(_ tab: UiTimelineTabItem) {
        if !isCustomizing {
            editableTabs = liveTabs
        }
        editableTabs.removeAll { $0.id == tab.id }
        if selectedTab == .timeline(tab, isHome: true) {
            selectedTab = editableTabs.first.map { .timeline($0, isHome: true) }
        }
        persist()
    }

    private func update(_ initialTab: UiTimelineTabItem, with updatedTab: UiTimelineTabItem) {
        if !isCustomizing {
            editableTabs = liveTabs
        }
        guard let index = editableTabs.firstIndex(where: { $0.id == initialTab.id }) else {
            return
        }
        editableTabs[index] = updatedTab
        if selectedTab == .timeline(initialTab, isHome: true) {
            selectedTab = .timeline(updatedTab, isHome: true)
        }
        persist()
    }

    private func move(from source: IndexSet, to destination: Int) {
        editableTabs.move(fromOffsets: source, toOffset: destination)
        persist()
    }

    private func persist() {
        settingsPresenter.state.replaceHomeTimelineTabs(tabs: editableTabs)
    }

    private func normalizeSelection(previousHomeTabIDs: [String]?) {
        guard let currentSelection = selectedTab else {
            selectedTab = liveTabs.first.map { .timeline($0, isHome: true) }
            return
        }

        guard case .timeline(let selectedTimeline, isHome: true) = currentSelection else {
            return
        }

        let selectedTimelineID = selectedTimeline.id
        let currentHomeTabIDs = Set(liveTabs.map(\.id))
        guard !currentHomeTabIDs.contains(selectedTimelineID),
              previousHomeTabIDs?.contains(selectedTimelineID) == true
        else {
            return
        }

        selectedTab = liveTabs.first.map { .timeline($0, isHome: true) }
    }
}

private enum HomeSidebarAddPopover: String, Identifiable {
    case tab
    case group

    var id: String { rawValue }
}

private struct HomeSidebarEditableGroupRow: View {
    let group: UiGroupTimelineTabItem
    let showsReorderHandle: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var isHovering = false

    var body: some View {
        rowContent
            .contentShape(Rectangle())
            .onHover { isHovering = $0 }
            .contextMenu {
                contextMenu
            }
    }

    private var rowContent: some View {
        HStack(spacing: 8) {
            Label {
                TimelineTabTitle(title: group.title)
            } icon: {
                TabIcon(tabItem: group)
            }

            Spacer()

            editButton

            deleteButton

            if showsReorderHandle {
                Image(systemName: "line.3.horizontal")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
    }

    @ViewBuilder
    private var editButton: some View {
        if showsReorderHandle && isHovering {
            Button(action: onEdit) {
                Image(fontAwesome: .pen)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "edit", bundle: .main))
        } else {
            Image(fontAwesome: .pen)
                .hidden()
        }
    }

    @ViewBuilder
    private var deleteButton: some View {
        if showsReorderHandle && isHovering {
            Button(role: .destructive, action: onDelete) {
                Image(fontAwesome: .trash)
                    .foregroundStyle(.red)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "delete", bundle: .main))
        } else {
            Image(fontAwesome: .trash)
                .hidden()
        }
    }

    @ViewBuilder
    private var contextMenu: some View {
        Button(action: onEdit) {
            Label {
                Text("edit")
            } icon: {
                Image(fontAwesome: .pen)
            }
        }

        Button(role: .destructive, action: onDelete) {
            Label {
                Text("delete")
            } icon: {
                Image(fontAwesome: .trash)
            }
        }
    }
}

private struct HomeSidebarEditableTabRow: View {
    let tab: UiTimelineTabItem
    let showsReorderHandle: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    @State private var isHovering = false

    var body: some View {
        HStack(spacing: 8) {
            Label {
                TimelineTabTitle(title: tab.title)
            } icon: {
                TabIcon(tabItem: tab)
            }

            Spacer()

            editButton

            deleteButton

            if showsReorderHandle {
                Image(systemName: "line.3.horizontal")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .contentShape(Rectangle())
        .onHover { isHovering = $0 }
        .contextMenu {
            Button(action: onEdit) {
                Label {
                    Text("edit")
                } icon: {
                    Image(fontAwesome: .pen)
                }
            }

            Button(role: .destructive, action: onDelete) {
                Label {
                    Text("delete")
                } icon: {
                    Image(fontAwesome: .trash)
                }
            }
        }
    }

    @ViewBuilder
    private var editButton: some View {
        if showsReorderHandle && isHovering {
            Button(action: onEdit) {
                Image(fontAwesome: .pen)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "edit", bundle: .main))
        } else {
            Image(fontAwesome: .pen)
                .hidden()
        }
    }

    @ViewBuilder
    private var deleteButton: some View {
        if showsReorderHandle && isHovering {
            Button(role: .destructive, action: onDelete) {
                Image(fontAwesome: .trash)
                    .foregroundStyle(.red)
            }
            .buttonStyle(.borderless)
            .help(String(localized: "delete", bundle: .main))
        } else {
            Image(fontAwesome: .trash)
                .hidden()
        }
    }
}

struct HomeSidebarAddTabPopover: View {
    @Environment(\.openWindow) private var openWindow
    @StateObject private var presenter = KotlinPresenter(presenter: AllTabsPresenter())
    @State private var expandedSections: Set<String> = ["rss"]
    @State private var showCreateGroupPopover = false

    let selectedTabs: [UiTimelineTabItem]
    let onAddGroup: ((UiGroupTimelineTabItem) -> Void)?
    let onAdd: (UiTimelineTabItem) -> Void
    let onDelete: (UiTimelineTabItem) -> Void

    var body: some View {
        List {
            if let onAddGroup {
                Section {
                    Button {
                        showCreateGroupPopover.toggle()
                    } label: {
                        Label {
                            Text("tab_settings_add_group")
                        } icon: {
                            Image(fontAwesome: .tableList)
                        }
                    }
                    .buttonStyle(.plain)
                    .popover(isPresented: $showCreateGroupPopover, arrowEdge: .trailing) {
                        HomeSidebarCreateGroupPopover { group in
                            onAddGroup(group)
                            showCreateGroupPopover = false
                        }
                        .frame(width: 440, height: 560)
                    }
                }
            }

            Section {
                DisclosureGroup(isExpanded: binding(for: "rss")) {
                    ForEach(presenter.state.rssTabs, id: \.id) { tab in
                        HomeSidebarAddTabRow(
                            tab: tab,
                            isSelected: isSelected(tab),
                            onAdd: onAdd,
                            onDelete: onDelete
                        )
                    }

                    Button {
                        openWindow(id: MacWindowID.rssManagement)
                    } label: {
                        Label {
                            Text("settings_rss_management_title")
                        } icon: {
                            Image(fontAwesome: .squareRss)
                        }
                    }
                    .buttonStyle(.plain)
                } label: {
                    Label {
                        Text("rss_title")
                    } icon: {
                        Image(fontAwesome: .squareRss)
                    }
                }
            }

            StateView(state: presenter.state.flattenedAccountTabs) { accountTabs in
                let accounts: [AllTabsPresenterStateFlattenedAccountTabs] = accountTabs
                    .cast(AllTabsPresenterStateFlattenedAccountTabs.self)
                Section {
                    ForEach(accounts, id: \.profile.key) { account in
                        DisclosureGroup(isExpanded: binding(for: "account-\(account.profile.key)")) {
                            ForEach(Array(account.sections.enumerated()), id: \.offset) { index, section in
                                DisclosureGroup(isExpanded: binding(for: "account-\(account.profile.key)-section-\(index)")) {
                                    ForEach(section.data, id: \.id) { tab in
                                        HomeSidebarAddTabRow(
                                            tab: tab,
                                            isSelected: isSelected(tab),
                                            onAdd: onAdd,
                                            onDelete: onDelete
                                        )
                                    }
                                } label: {
                                    TimelineTabTitle(title: section.title)
                                }
                            }
                        } label: {
                            Label {
                                Text(account.profile.handle.canonical)
                            } icon: {
                                AvatarView(
                                    data: account.profile.avatar?.url,
                                    customHeader: account.profile.avatar?.customHeaders
                                )
                                .frame(width: 20, height: 20)
                            }
                        }
                    }
                }
            } loadingContent: {
                ProgressView()
                    .frame(maxWidth: .infinity)
            }
        }
        .listStyle(.sidebar)
    }

    private func isSelected(_ tab: UiTimelineTabItem) -> Bool {
        selectedTabs.contains { $0.id == tab.id }
    }

    private func binding(for key: String) -> Binding<Bool> {
        Binding(
            get: { expandedSections.contains(key) },
            set: { isExpanded in
                if isExpanded {
                    expandedSections.insert(key)
                } else {
                    expandedSections.remove(key)
                }
            }
        )
    }
}

private struct HomeSidebarCreateGroupPopover: View {
    @StateObject private var presenter = KotlinPresenter(presenter: GroupConfigPresenter())
    @State private var name = ""
    @State private var icon: IconType = IconType.Material(icon: .rss)
    @State private var tabs: [UiTimelineTabItem] = []

    let onCreate: (UiGroupTimelineTabItem) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("tab_settings_add_group")
                .font(.headline)

            TextField("tab_settings_group_name_placeholder", text: $name)
                .textFieldStyle(.roundedBorder)

            HStack(alignment: .top, spacing: 10) {
                TabIcon(icon: icon, size: 32)
                    .frame(width: 36, height: 36)

                VStack(alignment: .leading, spacing: 6) {
                    Text("tab_settings_edit_icon_header")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    ScrollView(.horizontal) {
                        LazyHGrid(rows: [GridItem(.fixed(38), spacing: 8)], spacing: 8) {
                            ForEach(presenter.state.availableIcons, id: \.description) { item in
                                Button {
                                    icon = item
                                } label: {
                                    TabIcon(icon: item, size: 22)
                                        .frame(width: 32, height: 32)
                                        .padding(3)
                                        .background {
                                            if icon.description == item.description {
                                                RoundedRectangle(cornerRadius: 6)
                                                    .fill(Color.accentColor.opacity(0.16))
                                            }
                                        }
                                        .overlay {
                                            if icon.description == item.description {
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
                    .frame(height: 46)
                }
            }

            Divider()

            HomeSidebarAddTabPopover(
                selectedTabs: tabs,
                onAddGroup: nil,
                onAdd: add,
                onDelete: remove
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()

            HStack {
                if tabs.isEmpty {
                    Text("tab_settings_group_empty")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Text(verbatim: "\(tabs.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button("done") {
                    create()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(tabs.isEmpty)
            }
        }
        .padding(16)
    }

    private func add(_ tab: UiTimelineTabItem) {
        guard !tabs.contains(where: { $0.id == tab.id }) else {
            return
        }
        tabs.append(tab)
    }

    private func remove(_ tab: UiTimelineTabItem) {
        tabs.removeAll { $0.id == tab.id }
    }

    private func create() {
        if let group = presenter.state.buildGroupItem(
            initialItem: nil,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            icon: icon,
            appearancePatch: TimelinePresentationAppearancePatchHelper.shared.empty,
            enabled: true,
            tabs: tabs,
            mergePolicy: .timePerPage,
            filterConfig: TimelineFilterConfig(),
            defaultGroupName: String(localized: "tab_settings_group_default_name", bundle: .main)
        ) {
            onCreate(group)
        }
    }
}

private struct HomeSidebarAddTabRow: View {
    let tab: UiTimelineTabItem
    let isSelected: Bool
    let onAdd: (UiTimelineTabItem) -> Void
    let onDelete: (UiTimelineTabItem) -> Void

    var body: some View {
        Button {
            isSelected ? onDelete(tab) : onAdd(tab)
        } label: {
            HStack(spacing: 8) {
                Label {
                    TimelineTabTitle(title: tab.title)
                } icon: {
                    TabIcon(tabItem: tab)
                }

                Spacer()

                Image(fontAwesome: isSelected ? .check : .plus)
                    .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
