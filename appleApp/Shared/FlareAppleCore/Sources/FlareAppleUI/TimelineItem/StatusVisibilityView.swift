import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct StatusVisibilityView: View {
    private let data: UiTimelineV2.PostVisibility

    public init(data: UiTimelineV2.PostVisibility) {
        self.data = data
    }

    public var body: some View {
        switch data {
        case .public:    Image(fontAwesome: .globe)
        case .home:      Image(fontAwesome: .lockOpen)
        case .followers: Image(fontAwesome: .lock)
        case .specified: Image(fontAwesome: .at)
        case .channel:   Image(fontAwesome: .tv)
        case .private:   Image(fontAwesome: .lock)
        }
    }
}
