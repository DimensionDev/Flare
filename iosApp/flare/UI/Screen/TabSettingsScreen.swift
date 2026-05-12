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
                    GroupConfigScreen(item: item)
                }
            }
        }
        .sheet(isPresented: $showCreateGroup) {
            NavigationStack {
                GroupConfigScreen(item: nil)
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
//        tabs.withSystemHomeMixedTimelineEnabled(enabled: enabled)
        TimelineTabItemV2Helpers.shared
            .withSystemHomeMixedTimelineEnabled(tabs: tabs, enabled: enabled)
    }
}

struct EditTabSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onConfirm: (TimelineTabItemV2) -> Void
    let tabItem: TimelineTabItemV2
    @StateObject private var presenter: KotlinPresenter<EditTabPresenterState>
    @State private var text: String = ""
    @State private var showPicker = false
    
    init(onConfirm: @escaping (TimelineTabItemV2) -> Void, tabItem: TimelineTabItemV2) {
        self.onConfirm = onConfirm
        self.tabItem = tabItem
        self._presenter = .init(wrappedValue: .init(presenter: EditTabPresenter(tabItem: tabItem)))
    }
    
    var body: some View {
        Form {
            Section {
                TabIcon(icon: presenter.state.icon, accountType: nil, size: 64)
                    .onTapGesture {
                        showPicker = true
                    }
                    .popover(isPresented: $showPicker) {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                                ForEach(presenter.state.availableIcons, id: \.description) { item in
                                    TabIcon(icon: item, accountType: nil, size: 48)
                                        .padding(4)
                                        .onTapGesture {
                                            presenter.state.setIcon(value: item)
                                            showPicker = false
                                        }
                                }
                            }
                            .padding()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .center)
                
                Toggle(isOn: Binding(get: {
                    presenter.state.withAvatar
                }, set: { value in
                    presenter.state.setWithAvatar(value: value)
                })) {
                    Text("tab_settings_edit_use_avatar")
                }
                .disabled(!presenter.state.canUseAvatar)
            } header: {
                Text("tab_settings_edit_icon_header")
            }
            
            Section {
                TextField("tab_settings_edit_title_placeholder", text: $text)
            } header: {
                Text("tab_settings_edit_title_header")
            }
        }
        .onChange(of: presenter.state.initialText) { oldValue, newValue in
            if case .success(let success) = onEnum(of: newValue) {
                text = String(success.data)
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
                    onConfirm(tabItem.withPresentationOverrides(title: text, icon: presenter.state.icon))
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
