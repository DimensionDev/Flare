import SwiftUI
import MarkdownUI
import shared
import NetworkImage

struct CommonStatusComponent<HeaderTrailing>: View where HeaderTrailing: View {
    @State var expanded: Bool
    let content: String
    let contentWarning: String?
    let user: UiUser
    let medias: [UiMedia]
    let timestamp: Int64
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    let onMediaClick: (UiMedia) -> Void
    let sensitive: Bool
    init(
        content: String,
        contentWarning: String?,
        user: UiUser,
        medias: [UiMedia],
        timestamp: Int64,
        headerTrailing: @escaping () -> HeaderTrailing,
        onMediaClick: @escaping (UiMedia) -> Void,
        sensitive: Bool
    ) {
        self.content = content
        self.contentWarning = contentWarning
        self.user = user
        self.medias = medias
        self.timestamp = timestamp
        self.headerTrailing = headerTrailing
        self.onMediaClick = onMediaClick
        self.sensitive = sensitive
        _expanded = State(initialValue: contentWarning == nil)
    }
    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                UserComponent(user: user)
                Spacer()
                HStack {
                    headerTrailing()
                    dateFormatter(Date(timeIntervalSince1970: .init(integerLiteral: timestamp)))
                }
                .foregroundColor(.gray)
                .font(.caption)
            }
            if let cwText = contentWarning, !cwText.isEmpty {
                Button(action: {
                    withAnimation {
                        expanded = !expanded
                    }
                }, label: {
                    Image(systemName: "exclamationmark.triangle")
                    Text(cwText)
                    Spacer()
                    if expanded {
                        Image(systemName: "arrowtriangle.down.circle.fill")
                    } else {
                        Image(systemName: "arrowtriangle.left.circle.fill")
                    }
                })
                .opacity(0.5)
                .buttonStyle(.plain)
                if expanded {
                    Spacer()
                        .frame(height: 8)
                }
            }
            if expanded {
                Markdown(content)
                    .font(.body)
                    .markdownInlineImageProvider(.emoji)
            }
            if !medias.isEmpty {
                Spacer()
                    .frame(height: 8)
                MediaComponent(hideSensitive: sensitive, medias: medias, onMediaClick: onMediaClick)
            }
        }.frame(alignment: .leading)
    }
}

func dateFormatter(_ date: Date) -> some View {
    let now = Date()
    let oneDayAgo = Calendar.current.date(byAdding: .day, value: -1, to: now)!
    
    if date > oneDayAgo {
        // If the date is within the last day, use the .timer style
        return Text(date, style: .relative)
    } else {
        // Otherwise, use the .dateTime style
        return Text(date, style: .date)
    }
}
