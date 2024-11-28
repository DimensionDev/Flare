import SwiftUI

struct TabSettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var store: TabSettingsStore
    
    var body: some View {
        NavigationView {
            List {
                ForEach(store.tabs) { tab in
                    HStack {
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.gray)
                        
                        Text(tab.title)
                        
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
                trailing: Button("完成") {
                    dismiss()
                }
            )
        }
        .environment(\.editMode, .constant(.active))
    }
}
