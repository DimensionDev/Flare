import SwiftUI
import KotlinSharedUI

struct StatusReactionView: View {
    @Environment(\.openURL) private var openURL
    let data: [UiTimelineV2.PostEmojiReaction]
    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack {
                ForEach(data, id: \.name) { item in
                    Button {
                        item.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                    } label: {
                        Label {
                            Text(item.count.humanized)
                                .foregroundStyle(item.me ? Color.white : Color(.label))
                        } icon: {
                            if item.isUnicode {
                                Text(item.name)
                            } else {
                                NetworkImage(data: item.url)
                            }
                        }
                    }
                    .if(item.me, if: { button in
                        button.buttonStyle(.borderedProminent)
                    }, else: { button in
                        button.buttonStyle(.bordered)
                    })
                }
            }
            .frame(height: 36)
        }
        .scrollIndicators(.hidden)
    }
}
