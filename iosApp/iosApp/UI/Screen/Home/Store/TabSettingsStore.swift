import Foundation
import SwiftUI

class TabSettingsStore: ObservableObject {
    @Published var tabs: [AppBarTabItem]
    
    static let defaultTabs = [
        AppBarTabItem(title: "首页", tag: 0),
        AppBarTabItem(title: "书签", tag: 1),
        AppBarTabItem(title: "精选", tag: 2)
    ]
    
    init() {
        if let data = UserDefaults.standard.data(forKey: "tabSettings"),
           let savedTabs = try? JSONDecoder().decode([AppBarTabItem].self, from: data) {
            self.tabs = savedTabs
        } else {
            self.tabs = TabSettingsStore.defaultTabs
        }
    }
    
    func saveTabs() {
        if let encoded = try? JSONEncoder().encode(tabs) {
            UserDefaults.standard.set(encoded, forKey: "tabSettings")
        }
    }
    
    func moveTab(from source: IndexSet, to destination: Int) {
        tabs.move(fromOffsets: source, toOffset: destination)
        saveTabs()
    }
    
    func toggleTab(_ id: String) {
        if let index = tabs.firstIndex(where: { $0.id == id }) {
            tabs[index].isEnabled.toggle()
            saveTabs()
        }
    }
}
