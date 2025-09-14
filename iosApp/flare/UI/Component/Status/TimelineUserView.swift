import SwiftUI
import KotlinSharedUI
import Awesome

struct TimelineUserView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentUser
    var body: some View {
        VStack {
            UserCompatView(data: data.value)
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
                                    Awesome.Classic.Solid.check.image
                                    
                                }
                            }
                        case .rejectFollowRequest(let rejectFollowButton):
                            Button(role: .destructive) {
                                rejectFollowButton.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                            } label: {
                                Label {
                                    Text("Reject follow request")
                                } icon: {
                                    Awesome.Classic.Solid.xmark.image
                                    
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
