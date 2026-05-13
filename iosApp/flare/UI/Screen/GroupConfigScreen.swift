import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct GroupConfigScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var baseTimelineAppearance
    let item: GroupTimelineTabItemV2?
    @State private var name: String
    @State private var icon: IconType
    @State private var enabled: Bool
    @State private var mergePolicy: TimelineMergePolicy
    @State private var appearancePatch: AppearancePatch
    @State private var tabs: [TimelineTabItemV2]
    @State private var showAddTabSheet = false
    @State private var editItem: TimelineTabItemV2? = nil
    @StateObject private var presenter: KotlinPresenter<GroupConfigPresenterState>

    init(item: GroupTimelineTabItemV2? = nil) {
        self.item = item
        _name = State(initialValue: item?.title.text ?? "")
        _icon = State(initialValue: item?.icon ?? IconType.Material(icon: .rss))
        _enabled = State(initialValue: item?.enabled ?? true)
        _mergePolicy = State(initialValue: item?.mergePolicy ?? .timePerPage)
        _appearancePatch = State(
            initialValue: item?.appearancePatch ?? TimelinePresentationAppearancePatchHelper.shared.empty
        )
        _tabs = State(initialValue: Array((item?.children ?? []).reduce(into: [TimelineTabItemV2]()) { result, tab in
            if !result.contains(where: { $0.id == tab.id }) {
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
            TimelinePresentationEditor(
                text: $name,
                icon: $icon,
                availableIcons: presenter.state.availableIcons,
                withAvatar: false,
                canUseAvatar: false,
                onWithAvatarChange: { _ in },
                enabled: $enabled,
                showEnabled: true,
                showAppearanceOverrides: true,
                timelineAppearance: TimelinePresentationAppearancePatchHelper.shared.resolve(
                    base: baseTimelineAppearance,
                    patch: appearancePatch
                ),
                appearancePatch: $appearancePatch,
                behaviorContent: AnyView(
                    Section {
                        MergePolicySettingsItem(selected: $mergePolicy)
                    }
                ),
                titlePlaceholder: "tab_settings_group_name_placeholder"
            )
            
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
                    ForEach(tabs, id: \.id) { tab in
                        HStack {
                            Label {
                                TimelineTabTitle(title: tab.title)
                            } icon: {
                                TabIcon(tabItem: tab)
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
                        tabs.removeAll { $0.id == tab.id }
                    },
                    onAdd: { tab in
                        if !tabs.contains(where: { $0.id == tab.id }) {
                            tabs.append(tab)
                        }
                    }
                )
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
                        if let index = tabs.firstIndex(where: { $0.id == updated.id }) {
                            tabs[index] = updated
                        }
                    }, tabItem: item, titleAndIconOnly: true)
                }
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
                        appearancePatch: appearancePatch,
                        enabled: enabled,
                        tabs: tabs,
                        mergePolicy: mergePolicy,
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
    let availableIcons: [IconType]
    let onSelect: (IconType) -> Void
    
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                ForEach(availableIcons, id: \.description) { item in
                    TabIcon(icon: item, size: 48)
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
