import SwiftUI

struct TabSettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var store: TabSettingsStore
    @State private var showingAddSheet = false
    @State private var newTabTitle = ""
    
    var body: some View {
        NavigationView {
            List {
                ForEach(store.tabs) { tab in
                    HStack {
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.gray)
                        
                        Text(tab.title)
                            .foregroundColor(tab.isEnabled ? .primary : .gray)
                        
                        Spacer()
                        
                        Toggle("", isOn: Binding(
                            get: { tab.isEnabled },
                            set: { _ in store.toggleTab(tab.id) }
                        ))
                    }
                }
                .onMove { source, destination in
                    store.moveTab(from: source, to: destination)
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
        }
        .sheet(isPresented: $showingAddSheet) {
            NavigationView {
                Form {
                    TextField("标签名称", text: $newTabTitle)
                }
                .navigationTitle("添加新标签")
                .navigationBarItems(
                    leading: Button("取消") {
                        showingAddSheet = false
                        newTabTitle = ""
                    },
                    trailing: Button("添加") {
                        if !newTabTitle.isEmpty {
                            store.addNewTab(title: newTabTitle)
                            showingAddSheet = false
                            newTabTitle = ""
                        }
                    }
                )
            }
        }
        .environment(\.editMode, .constant(.active))
    }
}
