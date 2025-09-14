import SwiftUI
import KotlinSharedUI
import Awesome

struct StatusView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimeline.ItemContentStatus
    let isDetail: Bool
    let isQuote: Bool
    let withLeadingPadding: Bool
    @State private var expand = false
    var body: some View {
        VStack(
            alignment: .leading,
        ) {
            if !data.parents.isEmpty {
                ZStack(
                    alignment: .leading
                ) {
                    Rectangle().fill(.separator).frame(minWidth: 1, maxWidth: 1, maxHeight: .infinity).padding(.leading, 22)
                    VStack {
                        ForEach(data.parents, id: \.itemKey) { parent in
                            StatusView(data: parent, withLeadingPadding: true)
                            .id(parent.itemKey)
                        }
                    }
                }
            }
            
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
            VStack(
                alignment: .leading,
                spacing: 8,
            ) {
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
                            .if(isDetail) { richText in
                                richText
                            } else: { richText in
                                richText.lineLimit(5)
                            }

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
            .if(withLeadingPadding, if: { stack in
                stack.padding(.leading, 50)
            }, else: { stack in
                stack
            })
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
        self.withLeadingPadding = false
    }
    init(data: UiTimeline.ItemContentStatus, detailStatusKey: MicroBlogKey?) {
        self.data = data
        self.isDetail = data.statusKey == detailStatusKey
        self.isQuote = false
        self.withLeadingPadding = false
    }
    init(data: UiTimeline.ItemContentStatus, isQuote: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = isQuote
        self.withLeadingPadding = false
    }
    init(data: UiTimeline.ItemContentStatus, withLeadingPadding: Bool) {
        self.data = data
        self.isDetail = false
        self.isQuote = false
        self.withLeadingPadding = withLeadingPadding
    }
}
