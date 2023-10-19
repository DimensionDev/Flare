import Foundation
import shared

@Observable
class ComposeViewModel {
    let status: ComposeStatus?
    var text = ""
    var cw = ""
    var enableCW = false
    init(status: ComposeStatus?) {
        self.status = status
    }
    func toggleCW() {
        enableCW = !enableCW
    }
}

enum ComposeStatus {
    case quote(key: MicroBlogKey)
    case reply(key: MicroBlogKey)
}
