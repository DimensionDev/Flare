import SwiftUI
import KotlinSharedUI

struct StatusReactionView: View {
    let data: UiTimeline.ItemContentStatusBottomContentReaction
    var body: some View {
        ScrollView {
            LazyHStack {
                ForEach(data.emojiReactions, id: \.name) { item in
                    Button {
                        
                    } label: {
                        Label {
                            Text(item.humanizedCount)
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
                .listStyle(.plain)
            }
        }
    }
}
