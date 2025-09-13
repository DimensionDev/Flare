import SwiftUI
import KotlinSharedUI
import Awesome

struct StatusView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentStatus
    let isDetail: Bool
    let isQuote: Bool
    @State private var expand = false
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            if let user = data.user {
                if isQuote {
                    UserOnelineView(data: user) {
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
                } else {
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
                    .frame(maxWidth: .infinity,)
            }
            
            if let card = data.card {
                if data.images.isEmpty && data.quote.isEmpty {
                    StatusCardView(data: card)
                        .frame(maxWidth: .infinity)
                }
            }
            
            if !data.quote.isEmpty {
                VStack {
                    ForEach(data.quote, id: \.itemKey) { quote in
                        StatusView(data: quote, isQuote: true)
                            .padding()
                        if data.quote.last != quote {
                            Divider()
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(.separator), lineWidth: 1)
                )
            }
            
            if case .reaction(let reaction) = onEnum(of: data.bottomContent) {
                StatusReactionView(data: reaction)
            }
            
            if isDetail {
                DateTimeText(data: data.createdAt, fullTime: true)
                    .font(.caption)
            }
            
            if !isQuote {
                StatusActionsView(data: data.actions)
                    .font(isDetail ? .body : .footnote)
                    .opacity(isDetail ? 1.0 : 0.6)
            }
        }
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}


extension StatusView {
    init(data: UiTimeline.ItemContentStatus, isDetail: Bool) {
        self.data = data
        self.isDetail = isDetail
        self.isQuote = false
    }
    init(data: UiTimeline.ItemContentStatus, detailStatusKey: MicroBlogKey?) {
        self.data = data
        self.isDetail = data.statusKey == detailStatusKey
        self.isQuote = false
    }
    init(data: UiTimeline.ItemContentStatus, isQuote: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = isQuote
    }
}
