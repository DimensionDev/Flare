import SwiftUI
import KotlinSharedUI

struct TimelineUserView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentUser
    var body: some View {
        VStack {
            UserCompatView(data: data.value)
                .onTapGesture {
                    data.value.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
            if !data.button.isEmpty {
                HStack {
                    ForEach(0..<data.button.count, id: \.self) { index in
                        let button = data.button[index]
                        switch onEnum(of: button) {
                        case .acceptFollowRequest(let acceptFollowButton):
                            Button {
                                acceptFollowButton.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            } label: {
                                Label {
                                    Text("Accept follow request")
                                } icon: {
                                    Image("fa-check")
                                }
                            }
                        case .rejectFollowRequest(let rejectFollowButton):
                            Button(role: .destructive) {
                                rejectFollowButton.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            } label: {
                                Label {
                                    Text("Reject follow request")
                                } icon: {
                                    Image("fa-xmark")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
