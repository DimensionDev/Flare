import SwiftUI
import KotlinSharedUI

struct TabSettingsScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.dismiss) private var dismiss
    @State private var enableMixedTimeline: Bool = false
    @State private var tabItems: [TimelineTabItem] = []
    @State private var showAddTabSheet = false
    @State private var editItem: TabItem? = nil
    var body: some View {
        List {
            Section {
                Toggle(isOn: $enableMixedTimeline) {
                    Text("tab_settings_enable_mixed_timeline_title")
                    Text("tab_settings_enable_mixed_timeline_desc")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Section {
                ForEach(tabItems, id: \.key) { item in
                    HStack(
                        spacing: 8
                    ) {
                        Label {
                            TabTitle(title: item.metaData.title)
                        } icon: {
                            TabIcon(icon: item.metaData.icon, accountType: item.account)
                        }
                        Spacer()
                        Button {
                            editItem = item
                        } label: {
                            Image("fa-pen")
                        }
                        .buttonStyle(.plain)
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.secondary)
                    }
                    .swipeActions(edge: .leading) {
                        Button {
                            editItem = item
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
                            if let index = tabItems.firstIndex(where: { $0.key == item.key }) {
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
        .onChange(of: presenter.state.tabSettings) { oldValue, newValue in
            if case .success(let tabSettings) = onEnum(of: newValue) {
                enableMixedTimeline = tabSettings.data.enableMixedTimeline
                tabItems = tabSettings.data.mainTabs
            }
        }
        .navigationTitle("tab_settings_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddTabSheet = true
                } label: {
                    Label {
                        Text("Add")
                    } icon: {
                        Image("fa-plus")
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(
                    role: .confirm
                ) {
                    presenter.state.updateTabSettings { current in
                        current.doCopy(secondaryItems: current.secondaryItems, enableMixedTimeline: enableMixedTimeline, mainTabs: tabItems)
                    }
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
        .sheet(isPresented: $showAddTabSheet) {
            NavigationStack {
                AddTabSheet(
                    selectedTabs: tabItems,
                    filterIsTimeline: true,
                    onDelete: { item in
                        if let index = tabItems.firstIndex(where: { $0.key == item.key }) {
                            tabItems.remove(at: index)
                        }
                    },
                    onAdd: { item in
                        if !tabItems.contains(where: { $0.key == item.key }), let tabItem = item as? TimelineTabItem {
                            tabItems.append(tabItem)
                        }
                    },
                    onAddRssSource: {
                        
                    }
                )
            }
        }
        .sheet(item: $editItem) { item in
            NavigationStack {
                EditTabSheet(onConfirm: { updated in
                    if let index = tabItems.firstIndex(where: { $0.key == updated.key }), let updated = updated as? TimelineTabItem {
                        tabItems[index] = updated
                    }
                }, tabItem: item)
            }
        }
    }
    
    
    func move(from source: IndexSet, to destination: Int) {
        tabItems.move(fromOffsets: source, toOffset: destination)
    }
}

extension TabItem: Identifiable {
    
}

struct EditTabSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onConfirm: (TabItem) -> Void
    let tabItem: TabItem
    @StateObject private var presenter: KotlinPresenter<EditTabPresenterState>
    @State private var text: String = ""
    @State private var showPicker = false
    
    init(onConfirm: @escaping (TabItem) -> Void, tabItem: TabItem) {
        self.onConfirm = onConfirm
        self.tabItem = tabItem
        self._presenter = .init(wrappedValue: .init(presenter: EditTabPresenter(tabItem: tabItem)))
    }
    
    var body: some View {
        Form {
            Section {
                TabIcon(icon: presenter.state.icon, accountType: tabItem.account, size: 64)
                    .onTapGesture {
                        showPicker = true
                    }
                    .popover(isPresented: $showPicker) {
                        ScrollView {
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                                ForEach(presenter.state.availableIcons, id: \.description) { item in
                                    TabIcon(icon: item, accountType: tabItem.account, size: 48)
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
                    role: .confirm
                ) {
                    onConfirm(tabItem.update(metaData: .init(title: .Text(content: text), icon: presenter.state.icon)))
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
    let selectedTabs: [TabItem]
    let onDelete: (TabItem) -> Void
    let onAdd: (TabItem) -> Void
    let onAddRssSource: () -> Void
    @State private var selectedIndex = 0
    var body: some View {
        VStack {
            ScrollView(.horizontal) {
                HStack {
                    Label {
                        Text("rss_title")
                    } icon: {
                        Image("fa-square-rss")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 20, height: 20)
                    }
                    .onTapGesture {
                        withAnimation {
                            selectedIndex = 0
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .foregroundStyle(selectedIndex == 0 ? Color.white : .primary)
                    .glassEffect(selectedIndex == 0 ? .regular.tint(.accentColor) : .regular, in: .capsule)
                    
                    StateView(state: presenter.state.accountTabs) { accountTabs in
                        let tabs = accountTabs.cast(AllTabsPresenterStateAccountTabs.self)
                        ForEach(0..<tabs.count, id: \.self) { index in
                            let item = tabs[index]
                            Label {
                                Text(item.profile.handle)
                            } icon: {
                                AvatarView(data: item.profile.avatar)
                                    .frame(width: 20, height: 20)
                            }
                            .onTapGesture {
                                withAnimation {
                                    selectedIndex = (index + 1)
                                }
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                            .foregroundStyle(selectedIndex == (index + 1) ? Color.white : .primary)
                            .glassEffect(selectedIndex == (index + 1) ? .regular.tint(.accentColor) : .regular, in: .capsule)
                        }
                    }
                }
                .padding(.horizontal)
            }
            .scrollClipDisabled()
            .scrollIndicators(.hidden)
            List {
                if selectedIndex == 0 {
                    if !presenter.state.rssTabs.isEmpty {
                        // rss
                        PagingView(data: presenter.state.rssTabs) { item in
                            HStack {
                                UiRssView(data: item)
                                Spacer()
                                let tabItem = RssTimelineTabItem(data: item)
                                if selectedTabs.contains(where: { $0.key == tabItem.key }) {
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
                        } loadingContent: {
                            UiListPlaceholder()
                        }
                    }
                    Button {
                        onAddRssSource()
                    } label: {
                        Label {
                            Text("rss_add_source")
                        } icon: {
                            Image("fa-plus")
                        }
                    }
                    .buttonStyle(.plain)
                } else {
                    let profileIndex = selectedIndex - 1
                    StateView(state: presenter.state.accountTabs) { accountTabs in
                        let tabs = accountTabs.cast(AllTabsPresenterStateAccountTabs.self)
                        if profileIndex < tabs.count {
                            let tabItem = tabs[profileIndex]
                            AccountTabListView(
                                accountTabs: tabItem,
                                selectedTabs: selectedTabs,
                                onDelete: onDelete,
                                onAdd: onAdd
                            )
                            .id(tabItem.profile.key)
                        }
                    }
                }
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
        }
    }
}

struct AccountTabListView: View {
    let accountTabs: AllTabsPresenterStateAccountTabs
    @State private var selectedIndex: Int = 0
    let selectedTabs: [TabItem]
    let onDelete: (TabItem) -> Void
    let onAdd: (TabItem) -> Void
    var body: some View {
        if !accountTabs.extraTabs.isEmpty {
            Picker(selection: $selectedIndex) {
                Text("tab_settings_section_main_tabs").tag(0)
                ForEach(0..<accountTabs.extraTabs.count, id: \.self) { index in
                    let tabItem = accountTabs.extraTabs[index]
                    switch onEnum(of: tabItem) {
                    case .antenna: Text("antenna_title")
                            .tag(index + 1)
                    case .feed: Text("bluesky_feeds_title")
                            .tag(index + 1)
                    case .list: Text("all_lists_title")
                            .tag(index + 1)
                    }
                }
            } label: {
                
            }
            .pickerStyle(.segmented)
        }
        if selectedIndex == 0 {
            ForEach(accountTabs.tabs, id: \.key) { tab in
                HStack {
                    Label {
                        TabTitle(title: tab.metaData.title)
                    } icon: {
                        TabIcon(icon: tab.metaData.icon, accountType: tab.account)
                    }
                    Spacer()
                    if selectedTabs.contains(where: { $0.key == tab.key }) {
                        Button {
                            onDelete(tab)
                        } label: {
                            Image("fa-minus")
                                .foregroundColor(.red)
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            onAdd(tab)
                        } label: {
                            Image("fa-plus")
                                .foregroundColor(.accentColor)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        } else {
            let index = selectedIndex - 1
            if !accountTabs.extraTabs.isEmpty, index < accountTabs.extraTabs.count {
                let tabItem = accountTabs.extraTabs[index]
                PagingView(data: tabItem.data) { tab in
                    HStack {
                        UiListView(data: tab)
                        Spacer()
                        let item = tab.toTabItem(accountKey: accountTabs.profile.key)
                        if selectedTabs.contains(where: { $0.key == item.key }) {
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
            }
        }
    }
}

extension AddTabSheet {
    init(
        selectedTabs: [TabItem],
        filterIsTimeline: Bool,
        onDelete: @escaping (TabItem) -> Void,
        onAdd: @escaping (TabItem) -> Void,
        onAddRssSource: @escaping () -> Void
    ) {
        self.selectedTabs = selectedTabs
        self.onDelete = onDelete
        self.onAdd = onAdd
        self.onAddRssSource = onAddRssSource
        self._presenter = .init(wrappedValue: .init(presenter: AllTabsPresenter(filterIsTimeline: filterIsTimeline)))
    }
}
