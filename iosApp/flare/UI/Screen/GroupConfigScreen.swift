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
    @State private var editItem: TimelineTabItem? = nil
    @StateObject private var presenter: KotlinPresenter<GroupConfigPresenterState>

    init(item: MixedTimelineTabItem? = nil) {
        self.item = item
        _name = State(initialValue: item?.metaData.title.text ?? "")
        _icon = State(initialValue: item?.metaData.icon ?? IconType.Material(icon: .rss))
        _tabs = State(initialValue: Array((item?.subTimelineTabItem ?? []).reduce(into: [TimelineTabItem]()) { result, tab in
            if !result.contains(where: { $0.key == tab.key }) {
                result.append(tab)
            }
        }))
        _presenter = StateObject(
            wrappedValue: KotlinPresenter(
                presenter: GroupConfigPresenter()
            )
        )
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
                            Button {
                                editItem = tab
                            } label: {
                                Image("fa-pen")
                            }
                            .buttonStyle(.plain)
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
                        tabs.removeAll { $0.key == tab.key }
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
                IconPicker(
                    selectedIcon: icon,
                    onSelect: { icon = $0 }
                )
            }
        }
        .sheet(item: $editItem) { item in
            NavigationStack {
                EditTabSheet(onConfirm: { updated in
                    if let index = tabs.firstIndex(where: { $0.key == updated.key }), let updated = updated as? TimelineTabItem {
                        tabs[index] = updated
                    }
                }, tabItem: item)
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
                    presenter.state.commit(
                        initialItem: item,
                        name: name,
                        icon: icon,
                        tabs: tabs,
                        defaultGroupName: NSLocalizedString("tab_settings_group_default_name", comment: "")
                    )
                    dismiss()
                } label: {
                    Image(.faCheck)
                }
                .disabled(tabs.isEmpty && item == nil)
            }
        }
    }
    
    func move(from source: IndexSet, to destination: Int) {
        tabs.move(fromOffsets: source, toOffset: destination)
    }
    
    func delete(at offsets: IndexSet) {
        tabs.remove(atOffsets: offsets)
    }
}

struct IconPicker: View {
    @Environment(\.dismiss) private var dismiss
    let selectedIcon: IconType
    let onSelect: (IconType) -> Void
    
    let availableIcons: [IconType] = UiIcon.allCases.map { IconType.Material(icon: $0) }
    
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                ForEach(availableIcons, id: \.description) { item in
                    TabIcon(icon: item, accountType: AccountType.Guest.shared, size: 48)
                        .padding(4)
                        .onTapGesture {
                            onSelect(item)
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
