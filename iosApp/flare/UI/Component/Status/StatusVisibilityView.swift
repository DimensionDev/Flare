import SwiftUI
import KotlinSharedUI

struct StatusVisibilityView: View {
    let data: UiTimeline.ItemContentStatusTopEndContentVisibilityType
    var body: some View {
        switch data {
        case .public:    Image("fa-globe")
        case .home:      Image("fa-lock-open")
        case .followers: Image("fa-lock")
        case .specified: Image("fa-at")
        }
    }
}
