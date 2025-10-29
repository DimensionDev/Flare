import SwiftUI
import KotlinSharedUI

struct StatusReactionView: View {
    let data: UiTimeline.ItemContentStatusBottomContentReaction
    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack {
                ForEach(data.emojiReactions, id: \.name) { item in
                    Button {
                        item.onClicked()
                    } label: {
                        Label {
                            Text(item.humanizedCount)
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
