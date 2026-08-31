import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct StatusVisibilityView: View {
    private let data: UiTimelineV2.PostVisibility

    public init(data: UiTimelineV2.PostVisibility) {
        self.data = data
    }

    public var body: some View {
        Group {
            switch data {
            case .public:    Image(fontAwesome: .globe)
            case .home:      Image(fontAwesome: .lockOpen)
            case .followers: Image(fontAwesome: .lock)
            case .specified: Image(fontAwesome: .at)
            case .channel:   Image(fontAwesome: .tv)
            }
        }
        .accessibilityLabel(Text(LocalizedStringKey(accessibilityKey), bundle: FlareAppleUILocalization.bundle))
    }

    private var accessibilityKey: String {
        switch data {
        case .public: "status_visibility_public"
        case .home: "home_tab_home_title"
        case .followers: "matrix_followers"
        case .specified: "status_visibility_specified"
        case .channel: "channel_title"
        }
    }
}
