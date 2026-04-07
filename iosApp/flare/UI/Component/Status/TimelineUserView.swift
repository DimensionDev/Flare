import SwiftUI
import KotlinSharedUI

struct TimelineUserView: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.appearanceSettings.showNumbers) private var showNumbers
    @ScaledMetric(relativeTo: .footnote) var fontSize = 13
    let data: UiTimelineV2.User
    var body: some View {
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
