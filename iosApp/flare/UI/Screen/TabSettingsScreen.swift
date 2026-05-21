import SwiftUI
import KotlinSharedUI
struct TabSettingsScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: HomeTabSettingsPresenter())
    @Environment(\.dismiss) private var dismiss
    @State private var enableMixedTimeline: Bool = false
    @State private var tabItems: [TimelineTabItemV2] = []
    @State private var loadedTabs = false
    @State private var showAddTabSheet = false
    @State private var editItem: TimelineTabItemV2? = nil
    @State private var editGroup: GroupTimelineTabItemV2? = nil
    @State private var showCreateGroup = false
    var body: some View {
        List {
            if tabItems.filter({ !isSystemHomeMixedTimeline($0) }).count > 1 {
                Section {
                    Toggle(isOn: $enableMixedTimeline) {
                        Text("tab_settings_enable_mixed_timeline_title")
                        Text("tab_settings_enable_mixed_timeline_desc")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .onChange(of: enableMixedTimeline) { _, value in
                        tabItems = withSystemHomeMixedTimelineEnabled(tabItems, enabled: value)
                    }
                    if enableMixedTimeline {
                        MergePolicySettingsItem(
                            selected: Binding(get: {
                                systemHomeMergePolicy
                            }, set: { value in
                                tabItems = withSystemHomeMixedTimelineEnabled(
                                    tabItems,
                                    enabled: true,
                                    mergePolicy: value
                                )
                            })
                        )
                    }
                }
            }
            Section {
                ForEach(tabItems, id: \.id) { item in
                    HStack(
                        spacing: 8
                    ) {
                        Label {
                            TimelineTabTitle(title: item.title)
                        } icon: {
                            TabIcon(tabItem: item)
                        }
                        Spacer()
                        Button {
                            if let group = item as? GroupTimelineTabItemV2, !isSystemHomeMixedTimeline(item) {
                                editGroup = group
                            } else {
                                editItem = item
                            }
                        } label: {
                            Image("fa-pen")
                        }
                        .buttonStyle(.plain)
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.secondary)
                    }
                    .swipeActions(edge: .leading) {
                        Button {
                            if let group = item as? GroupTimelineTabItemV2, !isSystemHomeMixedTimeline(item) {
                                editGroup = group
                            } else {
                                editItem = item
                            }
                        } label: {
                            Label {
                                Text("tab_settings_edit")
                            } icon: {
                                Image("fa-pen")
                            }
                        }
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            if let index = tabItems.firstIndex(where: { $0.id == item.id }) {
                                tabItems.remove(at: index)
                            }
                        } label: {
                            Label {
                                Text("tab_settings_delete")
                            } icon: {
                                Image("fa-trash")
                            }
                        }
                    }
                }
                .onMove(perform: move)
            }
        }
        .onChange(of: presenter.state.homeTimelineTabs) { oldValue, newValue in
            if !loadedTabs, case .success(let tabs) = onEnum(of: newValue) {
                tabItems = tabs.data.cast(TimelineTabItemV2.self)
                enableMixedTimeline = tabItems.contains { isSystemHomeMixedTimeline($0) }
                loadedTabs = true
            }
        }
        .navigationTitle("tab_settings_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image("fa-xmark")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Button {
                        showCreateGroup = true
                    } label: {
                        Text("tab_settings_add_group")
                    }
                    Button {
                        showAddTabSheet = true
                    } label: {
                        Text("tab_settings_add_tab")
                    }
                } label: {
                    Image("fa-plus")
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(
//                    role: .confirm
                ) {
                    presenter.state.replaceHomeTimelineTabs(tabs: tabItems)
                    dismiss()
                } label: {
                    Image("fa-check")
                }
            }
        }
        .sheet(isPresented: $showAddTabSheet) {
            NavigationStack {
                AddTabSheet(
                    selectedTabs: tabItems,
                    filterIsTimeline: true,
                    onDelete: { item in
                        if let index = tabItems.firstIndex(where: { $0.id == item.id }) {
                            tabItems.remove(at: index)
                        }
                    },
                    onAdd: { item in
                        if !tabItems.contains(where: { $0.id == item.id }) {
                            tabItems.append(item)
                        }
                    },
                )
            }
        }

        .sheet(isPresented: Binding(get: {
            editGroup != nil
        }, set: { value in
            if !value {
                editGroup = nil
            }
        })) {
            NavigationStack {
                if let item = editGroup {
                    GroupConfigScreen(item: item) { updated in
                        upsertGroup(initialItem: item, updatedItem: updated)
                    }
                }
            }
        }
        .sheet(isPresented: $showCreateGroup) {
            NavigationStack {
                GroupConfigScreen(item: nil) { updated in
                    upsertGroup(initialItem: nil, updatedItem: updated)
                }
            }
        }
        .sheet(isPresented: Binding(get: {
            editItem != nil
        }, set: { value in
            if !value {
                editItem = nil
            }
        })) {
            NavigationStack {
                if let item = editItem {
                    EditTabSheet(onConfirm: { updated in
                        if let index = tabItems.firstIndex(where: { $0.id == updated.id }) {
                            tabItems[index] = updated
                        }
                    }, tabItem: item)
                }
            }
        }
    }
    
    
    func move(from source: IndexSet, to destination: Int) {
        tabItems.move(fromOffsets: source, toOffset: destination)
    }

    private func isSystemHomeMixedTimeline(_ item: TimelineTabItemV2) -> Bool {
        item.isSystemHomeMixedTimeline
    }

    private func withSystemHomeMixedTimelineEnabled(_ tabs: [TimelineTabItemV2], enabled: Bool) -> [TimelineTabItemV2] {
        withSystemHomeMixedTimelineEnabled(tabs, enabled: enabled, mergePolicy: nil)
    }

    private func withSystemHomeMixedTimelineEnabled(
        _ tabs: [TimelineTabItemV2],
        enabled: Bool,
        mergePolicy: TimelineMergePolicy?
    ) -> [TimelineTabItemV2] {
        TimelineTabItemV2Helpers.shared
            .withSystemHomeMixedTimelineEnabled(
                tabs: tabs,
                enabled: enabled,
                mergePolicy: mergePolicy
            )
    }

    private var systemHomeMergePolicy: TimelineMergePolicy {
        tabItems
            .compactMap { $0 as? GroupTimelineTabItemV2 }
            .first(where: { isSystemHomeMixedTimeline($0) })?
            .mergePolicy ?? .timePerPage
    }

    private func upsertGroup(
        initialItem: GroupTimelineTabItemV2?,
        updatedItem: GroupTimelineTabItemV2?
    ) {
        let targetIndex = initialItem
            .flatMap { item in tabItems.firstIndex(where: { $0.id == item.id }) }
            ?? tabItems.count

        tabItems.removeAll { item in
            item.id == initialItem?.id || item.id == updatedItem?.id
        }

        if let updatedItem {
            tabItems.insert(updatedItem, at: min(targetIndex, tabItems.count))
        }
    }
}

struct EditTabSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var baseTimelineAppearance
    let onConfirm: (TimelineTabItemV2) -> Void
    let tabItem: TimelineTabItemV2
    let titleAndIconOnly: Bool
    @StateObject private var presenter: KotlinPresenter<EditTabPresenterState>
    @State private var text: String = ""
    @State private var enabled: Bool
    @State private var filterConfig: TimelineFilterConfig
    @State private var appearancePatch: AppearancePatch
    @State private var showFilterSheet = false
    
    init(onConfirm: @escaping (TimelineTabItemV2) -> Void, tabItem: TimelineTabItemV2, titleAndIconOnly: Bool = false) {
        self.onConfirm = onConfirm
        self.tabItem = tabItem
        self.titleAndIconOnly = titleAndIconOnly
        self._presenter = .init(wrappedValue: .init(presenter: EditTabPresenter(tabItem: tabItem)))
        self._text = State(initialValue: tabItem.title.text)
        self._enabled = State(initialValue: tabItem.enabled)
        self._filterConfig = State(initialValue: tabItem.filterConfig)
        self._appearancePatch = State(
            initialValue: tabItem.appearancePatch ?? TimelinePresentationAppearancePatchHelper.shared.empty
        )
    }
    
    var body: some View {
        Form {
            TimelinePresentationEditor(
                text: $text,
                icon: Binding(get: {
                    presenter.state.icon
                }, set: { value in
                    presenter.state.setIcon(value: value)
                }),
                availableIcons: presenter.state.availableIcons,
                withAvatar: presenter.state.withAvatar,
                canUseAvatar: !titleAndIconOnly && presenter.state.canUseAvatar,
                onWithAvatarChange: { value in
                    presenter.state.setWithAvatar(value: value)
                },
                enabled: $enabled,
                filterConfig: $filterConfig,
                onEditFilter: {
                    showFilterSheet = true
                },
                showEnabled: !titleAndIconOnly && !tabItem.isSystemHomeMixedTimeline,
                showFilter: !titleAndIconOnly,
                showAppearanceOverrides: !titleAndIconOnly,
                timelineAppearance: TimelinePresentationAppearancePatchHelper.shared.resolve(
                    base: baseTimelineAppearance,
                    patch: appearancePatch
                ),
                appearancePatch: $appearancePatch,
                titlePlaceholder: "tab_settings_edit_title_placeholder"
            )
        }
        .sheet(isPresented: $showFilterSheet) {
            NavigationStack {
                TimelineFilterSheet(
                    initialFilterConfig: filterConfig,
                    onCancel: {
                        showFilterSheet = false
                    },
                    onConfirm: { updated in
                        filterConfig = updated
                        showFilterSheet = false
                    }
                )
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Close")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(
//                    role: .confirm
                ) {
                    onConfirm(
                        tabItem.withPresentationOverrides(
                            title: text,
                            icon: presenter.state.icon,
                            appearancePatch: appearancePatch,
                            enabled: enabled,
                            filterConfig: filterConfig
                        )
                    )
                    dismiss()
                } label: {
                    Label {
                        Text("Done")
                    } icon: {
                        Image("fa-check")
                    }
                }
            }
        }
    }
}

struct AddTabSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<AllTabsPresenterState>
    let selectedTabs: [TimelineTabItemV2]
    let onDelete: (TimelineTabItemV2) -> Void
    let onAdd: (TimelineTabItemV2) -> Void
    @State private var showAddRssSource = false
    @State private var importOpmlUrl: URL? = nil
    @State private var expandedSections: Set<String> = ["rss"]
    var body: some View {
        List {
            Section {
                DisclosureGroup(isExpanded: binding(for: "rss")) {
                    ForEach(presenter.state.rssTabs, id: \.id) { tabItem in
                        AddTabRow(
                            tabItem: tabItem,
                            isSelected: selectedTabs.contains(where: { $0.id == tabItem.id }),
                            onDelete: onDelete,
                            onAdd: onAdd
                        )
                    }
                    Button {
                        showAddRssSource = true
                    } label: {
                        Label {
                            Text("rss_add_source")
                        } icon: {
                            Image("fa-plus")
                        }
                    }
                    .buttonStyle(.plain)
                } label: {
                    Label {
                        Text("rss_title")
                    } icon: {
                        Image("fa-square-rss")
                    }
                }
            }
            StateView(state: presenter.state.accountTabs) { accountTabs in
                let tabs = accountTabs.cast(AllTabsPresenterStateAccountTabs.self)
                Section {
                    ForEach(0..<tabs.count, id: \.self) { index in
                        let item = tabs[index]
                        DisclosureGroup(isExpanded: binding(for: "account-\(item.profile.key)")) {
                            AccountTabListView(
                                accountTabs: item,
                                selectedTabs: selectedTabs,
                                expandedSections: $expandedSections,
                                onDelete: onDelete,
                                onAdd: onAdd
                            )
                        } label: {
                            Label {
                                Text(item.profile.handle.canonical)
                            } icon: {
                                AvatarView(data: item.profile.avatar)
                                    .frame(width: 20, height: 20)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("tab_settings_add_tab")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Close")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
        }
        .sheet(isPresented: $showAddRssSource) {
            NavigationStack {
                EditRssSheet(id: nil, onImportOPML: { url in
                    importOpmlUrl = url
                })
            }
        }
        .sheet(item: $importOpmlUrl) { url in
            NavigationStack {
                ImportOPMLScreen(url: url)
            }
        }
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

struct AccountTabListView: View {
    let accountTabs: AllTabsPresenterStateAccountTabs
    let selectedTabs: [TimelineTabItemV2]
    @Binding var expandedSections: Set<String>
    let onDelete: (TimelineTabItemV2) -> Void
    let onAdd: (TimelineTabItemV2) -> Void
    var body: some View {
        ForEach(0..<accountTabs.tabs.count, id: \.self) { index in
            let tabItem = accountTabs.tabs[index]
            DisclosureGroup(isExpanded: binding(for: "account-\(accountTabs.profile.key)-extra-\(index)")) {
                PagingView(data: tabItem.data) { item in
                    HStack {
                        Label {
                            TimelineTabTitle(title: item.title)
                        } icon: {
                            TabIcon(tabItem: item)
                        }
                        Spacer()
                        if selectedTabs.contains(where: { $0.id == item.id }) {
                            Button {
                                onDelete(item)
                            } label: {
                                Image("fa-minus")
                                    .foregroundColor(.red)
                            }
                            .buttonStyle(.plain)
                        } else {
                            Button {
                                onAdd(item)
                            } label: {
                                Image("fa-plus")
                                    .foregroundColor(.accentColor)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                } loadingContent: {
                    UiListPlaceholder()
                }
            } label: {
                Text(title(at: index))
            }
        }
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
    
    private func title(at index: Int) -> LocalizedStringKey {
        LocalizedStringKey(accountTabs.tabs[index].title.text)
    }
}

private struct AddTabRow: View {
    let tabItem: TimelineTabItemV2
    let isSelected: Bool
    let onDelete: (TimelineTabItemV2) -> Void
    let onAdd: (TimelineTabItemV2) -> Void
    
    var body: some View {
        HStack {
            Label {
                TimelineTabTitle(title: tabItem.title)
            } icon: {
                TabIcon(tabItem: tabItem)
            }
            Spacer()
            if isSelected {
                Button {
                    onDelete(tabItem)
                } label: {
                    Image("fa-minus")
                        .foregroundColor(.red)
                }
                .buttonStyle(.plain)
            } else {
                Button {
                    onAdd(tabItem)
                } label: {
                    Image("fa-plus")
                        .foregroundColor(.accentColor)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

extension AddTabSheet {
    init(
        selectedTabs: [TimelineTabItemV2],
        filterIsTimeline: Bool,
        onDelete: @escaping (TimelineTabItemV2) -> Void,
        onAdd: @escaping (TimelineTabItemV2) -> Void,
    ) {
        self.selectedTabs = selectedTabs
        self.onDelete = onDelete
        self.onAdd = onAdd
        self._presenter = .init(wrappedValue: .init(presenter: AllTabsPresenter()))
    }
}
