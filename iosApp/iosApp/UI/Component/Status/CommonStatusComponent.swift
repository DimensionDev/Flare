import SwiftUI
import MarkdownUI
import shared
import NetworkImage

struct CommonStatusComponent<HeaderTrailing>: View where HeaderTrailing: View {
    let content: String
    let user: UiUser
    let medias: [UiMedia]
    let timestamp: Int64
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    let onMediaClick: (UiMedia) -> Void
    var body: some View {
        VStack(alignment:.leading) {
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
            Markdown(content)
                .font(.body)
                .markdownInlineImageProvider(.emoji)
            if !medias.isEmpty {
                Spacer()
                    .frame(height: 8)
                MediaComponent(medias: medias, onMediaClick: onMediaClick)
            }
        }.frame(alignment: .leading)
    }
    
    
    private func dateFormatter(_ date: Date) -> some View {
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
}

