import SwiftUI
import KotlinSharedUI
import FlareAppleCore

public struct TimelineUserView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineAppearance.showNumbers) private var showNumbers
    @ScaledMetric(relativeTo: .footnote) private var fontSize = 13
    private let data: UiTimelineV2.User

    public init(data: UiTimelineV2.User) {
        self.data = data
    }

    public var body: some View {
        VStack {
            UserCompatView(data: data.value)
                .onTapGesture {
                    data.value.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
            if !data.button.isEmpty {
                HStack {
                    ForEach(Array(data.button.enumerated()), id: \.offset) { _, button in
                        StatusActionItemView(data: button, useText: true, isFixedWidth: false, fontSize: fontSize, showNumbers: showNumbers, openURL: openURL)
                    }
                }
            }
        }
    }
}
