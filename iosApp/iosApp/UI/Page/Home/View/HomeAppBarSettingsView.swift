import SwiftUI
import shared

struct HomeAppBarSettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var store: TabSettingsStore
    @State private var showingAddSheet = false
    @State private var showingDeleteAlert = false
    @State private var tabToDelete: FLTabItem? = nil
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("")) {
                    ForEach(store.allTabs, id: \.key) { tab in
                        TabItemRow(tab: tab, store: store) {
                            tabToDelete = tab
                            showingDeleteAlert = true
                        }
                    }
                    .onMove { source, destination in
                        store.moveTab(from: source, to: destination)
                    }
                }
            }
            .navigationTitle("标签设置")
            .navigationBarItems(
                leading: Button("返回") {
                    dismiss()
                },
                trailing: Button(action: {
                    showingAddSheet = true
                }) {
                    Image(systemName: "plus")
                }
            )
            .alert(isPresented: $showingDeleteAlert) {
                Alert(
                    title: Text("删除标签"),
                    message: Text("确定要删除这个标签吗？"),
                    primaryButton: .destructive(Text("删除")) {
                        if let tab = tabToDelete {
                            store.removeTab(tab.key)
                        }
                    },
                    secondaryButton: .cancel(Text("取消"))
                )
            }
        }
        .sheet(isPresented: $showingAddSheet) {
            AddTabView(store: store, isPresented: $showingAddSheet)
        }
        .environment(\.editMode, .constant(.active))
    }
}

struct TabItemRow: View {
    let tab: FLTabItem
    let store: TabSettingsStore
    let onDelete: () -> Void
    
    var body: some View {
        HStack {
            Image(systemName: "line.3.horizontal")
                .foregroundColor(.gray)
            
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
            
            Toggle("", isOn: Binding(
                get: { true },
                set: { _ in store.toggleTab(tab.key) }
            ))
            
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
        }
    }
}

struct AddTabView: View {
    @ObservedObject var store: TabSettingsStore
    @Binding var isPresented: Bool
    
    var body: some View {
        NavigationView {
            List {
                ForEach(store.availableTabs, id: \.key) { tab in
                    HStack {
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
                        
                        Button(action: {
                            store.addTab(tab)
                            isPresented = false
                        }) {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(.blue)
                        }
                    }
                }
            }
            .navigationTitle("添加次要标签")
            .navigationBarItems(
                leading: Button("取消") {
                    isPresented = false
                }
            )
        }
    }
}
