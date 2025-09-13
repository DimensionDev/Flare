import SwiftUI
import KotlinSharedUI
import Awesome

struct StatusView: View {
    let data: UiTimeline.ItemContentStatus
    let isDetail: Bool
    @State private var expand = false
    var body: some View {
        VStack(
            alignment: .leading
        ) {
            if let user = data.user {
                UserCompatView(data: user) {
                    HStack {
                        switch onEnum(of: data.topEndContent) {
                        case .visibility(let visibility):
                            StatusVisibilityView(data: visibility.visibility)
                        case .none:
                            EmptyView()
                        }
                        if !isDetail {
                            DateTimeText(data: data.createdAt)
                                .font(.caption)
                        }
                    }
                }
            }
            if let aboveTextContent = data.aboveTextContent {
                switch onEnum(of: aboveTextContent) {
                case .replyTo(let replyTo):
                    HStack {
                        Awesome.Classic.Solid.reply.image
                            .foregroundColor(.label)
                        Text("Reply to \(replyTo.handle)")
                    }
                    .font(.caption)
                }
            }
            if let contentWarning = data.contentWarning {
                RichText(text: contentWarning)
                Button {
                    withAnimation {
                        expand = !expand
                    }
                } label: {
                    if expand {
                        Text("mastodon_item_show_less")
                    } else {
                        Text("mastodon_item_show_more")
                    }
                }
            }
            
            if expand || data.contentWarning == nil || data.contentWarning?.isEmpty == true {
                if !data.content.isEmpty {
                    RichText(text: data.content)
                }
            }
            if !data.images.isEmpty {
                StatusMediaView(data: data.images)
            }
        }
    }
}

