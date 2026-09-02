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
        case .public:
            Image(fontAwesome: .globe)
                .accessibilityLabel(Text("status_visibility_public", bundle: FlareAppleUILocalization.bundle))
        case .home:
            Image(fontAwesome: .lockOpen)
                .accessibilityLabel(Text("home_tab_home_title", bundle: FlareAppleUILocalization.bundle))
        case .followers:
            Image(fontAwesome: .lock)
                .accessibilityLabel(Text("matrix_followers", bundle: FlareAppleUILocalization.bundle))
        case .specified:
            Image(fontAwesome: .at)
                .accessibilityLabel(Text("status_visibility_specified", bundle: FlareAppleUILocalization.bundle))
        case .channel:
            Image(fontAwesome: .tv)
                .accessibilityLabel(Text("channel_title", bundle: FlareAppleUILocalization.bundle))
        }
    }
}
