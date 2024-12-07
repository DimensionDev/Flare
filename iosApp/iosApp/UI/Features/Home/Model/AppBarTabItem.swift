import Foundation

struct AppBarTabItem: Identifiable, Hashable, Codable {
    let id: String
    var title: String
    var tag: Int
    var isEnabled: Bool
    
    init(id: String = UUID().uuidString, title: String, tag: Int, isEnabled: Bool = true) {
        self.id = id
        self.title = title
        self.tag = tag
        self.isEnabled = isEnabled
    }
}
