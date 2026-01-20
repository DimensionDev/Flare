import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct GroupConfigScreen: View {
    @Environment(\.dismiss) private var dismiss
    let item: MixedTimelineTabItem?
    
    @State private var name: String
    @State private var icon: IconType
    @State private var tabs: [TimelineTabItem]
    @State private var showAddTabSheet = false
    @State private var showIconPicker = false
    
    init(item: MixedTimelineTabItem? = nil) {
        self.item = item
        _name = State(initialValue: item?.metaData.title.text ?? "")
        _icon = State(initialValue: item?.metaData.icon ?? IconType.Material(icon: .rss))
        _tabs = State(initialValue: item?.subTimelineTabItem ?? [])
    }
    
    var body: some View {
        List {
            Section {
                HStack {
                    TabIcon(icon: icon, accountType: AccountType.Guest.shared, size: 36)
                        .onTapGesture {
                            showIconPicker = true
                        }
                    TextField("tab_settings_group_name_placeholder", text: $name)
                }
            }
            
            if tabs.isEmpty {
                Section {
                    VStack(alignment: .center, spacing: 8) {
                        Image("fa-table-list")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 64, height: 64)
                            .foregroundColor(.secondary)
                        Text("tab_settings_group_empty")
                            .foregroundColor(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                }
            } else {
                Section {
                    ForEach(tabs, id: \.key) { tab in
                        HStack {
                            Label {
                                TabTitle(title: tab.metaData.title)
                            } icon: {
                                TabIcon(icon: tab.metaData.icon, accountType: tab.account)
                            }
                            Spacer()
                        }
                    }
                    .onMove(perform: move)
                    .onDelete(perform: delete)
                }
            }
        }
        .navigationTitle(item == nil ? "tab_settings_add_group" : "tab_settings_edit_group")
        .sheet(isPresented: $showAddTabSheet) {
            NavigationStack {
                AddTabSheet(
                    selectedTabs: tabs,
                    filterIsTimeline: true,
                    onDelete: { tab in
                        if let index = tabs.firstIndex(where: { $0.key == tab.key }) {
                            tabs.remove(at: index)
                        }
                    },
                    onAdd: { tab in
                        if let timelineTab = tab as? TimelineTabItem {
                            if !tabs.contains(where: { $0.key == timelineTab.key }) {
                                tabs.append(timelineTab)
                            }
                        }
                    }
                )
            }
        }
        .sheet(isPresented: $showIconPicker) {
            NavigationStack {
                IconPicker(selectedIcon: $icon)
            }
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image(.faXmark)
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddTabSheet = true
                } label: {
                    Image(.faPlus)
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    save()
                    dismiss()
                } label: {
                    Image(.faCheck)
                }
                .disabled(tabs.isEmpty && item == nil) // Disable save if new group is empty
            }
        }
    }
    
    func move(from source: IndexSet, to destination: Int) {
        tabs.move(fromOffsets: source, toOffset: destination)
    }
    
    func delete(at offsets: IndexSet) {
        tabs.remove(atOffsets: offsets)
    }
    
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())

    func save() {
        let text = name
        let currentTabs = tabs
        let currentIcon = icon
        let isNew = item == nil
        let itemKey = item?.key
        
        presenter.state.updateTabSettings { current in
            var mainTabs = current.mainTabs
            
            if isNew {
                 if currentTabs.isEmpty { return current }
                 let newGroup = MixedTimelineTabItem(
                     subTimelineTabItem: currentTabs,
                     metaData: TabMetaData(
                         title: TitleType.Text(content: text.isEmpty ? NSLocalizedString("tab_settings_group_default_name", comment: "") : text),
                         icon: currentIcon
                     )
                 )
                 mainTabs.append(newGroup)
            } else {
                if currentTabs.isEmpty {
                    // Delete group if empty
                     if let index = mainTabs.firstIndex(where: { $0.key == itemKey }) {
                         mainTabs.remove(at: index)
                     }
                } else {
                    // Update group
                     if let index = mainTabs.firstIndex(where: { $0.key == itemKey }) {
                         let updatedGroup = MixedTimelineTabItem(
                             subTimelineTabItem: currentTabs,
                             metaData: TabMetaData(
                                 title: TitleType.Text(content: text.isEmpty ? NSLocalizedString("tab_settings_group_default_name", comment: "") : text),
                                 icon: currentIcon
                             )
                         )
                         mainTabs[index] = updatedGroup
                     }
                }
            }
            
            return current.doCopy(
                secondaryItems: current.secondaryItems,
                enableMixedTimeline: current.enableMixedTimeline,
                mainTabs: mainTabs
            )
        }
    }
}

struct IconPicker: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedIcon: IconType
    
    let availableIcons: [IconType] = IconType.MaterialMaterialIcon.allCases.map { IconType.Material(icon: $0) }
    
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                ForEach(availableIcons, id: \.description) { item in
                    TabIcon(icon: item, accountType: AccountType.Guest.shared, size: 48)
                        .padding(4)
                        .onTapGesture {
                            selectedIcon = item
                            dismiss()
                        }
                }
            }
            .padding()
        }
        .navigationTitle("Select Icon")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") {
                    dismiss()
                }
            }
        }
    }
}
