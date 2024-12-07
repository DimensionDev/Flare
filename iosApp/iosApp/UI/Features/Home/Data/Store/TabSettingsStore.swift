import Foundation
import SwiftUI

class TabSettingsStore: ObservableObject {
    @Published var tabs: [AppBarTabItem]
    
    static let defaultTabs = [
        AppBarTabItem(title: "首页", tag: 0),
        AppBarTabItem(title: "公开", tag: 1),
        AppBarTabItem(title: "书签", tag: 2),
        AppBarTabItem(title: "本地", tag: 3),
        AppBarTabItem(title: "收藏", tag: 4)
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
    
    func addNewTab(title: String) {
        let newTag = (tabs.map { $0.tag }.max() ?? -1) + 1
        let newTab = AppBarTabItem(title: title, tag: newTag)
        tabs.append(newTab)
        saveTabs()
    }
    
    func removeTab(_ id: String) {
        tabs.removeAll { $0.id == id }
        saveTabs()
    }
}
