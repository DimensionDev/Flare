import SwiftUI
import shared

struct HomeAppBarSettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var store: TabSettingsStore
    
    var body: some View {
        NavigationView {
            List {
                // 主要标签（只显示第一个）
                if let primaryTab = store.primaryItems.first {
                    Section(header: Text("main tab")) {
                        TabItemRow(tab: primaryTab, store: store, isPrimary: true)
                    }
                }
                
                // 次要标签（可更改状态）
                Section(header: Text("used tabs")) {
                    ForEach(store.storeItems, id: \.key) { tab in
                        TabItemRow(tab: tab, store: store, isPrimary: false)
                    }
                    .onMove { source, destination in
                        store.moveTab(from: source, to: destination)
                    }
                }
                
                // 未启用的标签
                let unusedTabs = store.secondaryItems.filter { tab in
                    !store.storeItems.contains { $0.key == tab.key }
                }
                if !unusedTabs.isEmpty {
                    Section(header: Text("unused tabs")) {
                        ForEach(unusedTabs, id: \.key) { tab in
                            TabItemRow(tab: tab, store: store, isPrimary: false)
                        }
                    }
                }
            }
            .navigationTitle("tab_settings_title")
            .navigationBarItems(
                leading: Button(" ") {
                    dismiss()
                }
            )
        }
        .environment(\.editMode, .constant(.active))
    }
}

struct TabItemRow: View {
    let tab: FLTabItem
    let store: TabSettingsStore
    let isPrimary: Bool
    
    var body: some View {
        HStack {
            if !isPrimary {
                Image(systemName: "line.3.horizontal")
                    .foregroundColor(.gray)
            }
            
            // 显示图标
            switch tab.metaData.icon {
            case .material(let iconName):
                if let materialIcon = FLMaterialIcon(rawValue: iconName) {
                    materialIcon.icon
                        .foregroundColor(.blue)
                }
            case .mixed(let icons):
                if let firstIcon = icons.first,
                   let materialIcon = FLMaterialIcon(rawValue: firstIcon) {
                    materialIcon.icon
                        .foregroundColor(.blue)
                }
            case .avatar:
                Image(systemName: "person.circle")
                    .foregroundColor(.blue)
            }
            
            // 显示标题
            switch tab.metaData.title {
            case .text(let title):
                Text(title)
            case .localized(let key):
                Text(NSLocalizedString(key, comment: ""))
            }
            
            Spacer()
            
            // 次要标签显示开关
            if !isPrimary {
                Toggle("", isOn: Binding(
                    get: { store.storeItems.contains(where: { $0.key == tab.key }) },
                    set: { _ in store.toggleTab(tab.key) }
                ))
            }
        }
    }
}
