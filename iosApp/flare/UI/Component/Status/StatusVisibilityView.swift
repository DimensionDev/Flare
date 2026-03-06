import SwiftUI
import KotlinSharedUI

struct StatusVisibilityView: View {
    let data: UiTimelineV2.PostVisibility
    var body: some View {
        switch data {
        case .public:    Image("fa-globe")
        case .home:      Image("fa-lock-open")
        case .followers: Image("fa-lock")
        case .specified: Image("fa-at")
        case .channel:   Image(.faTv)
        }
    }
}
