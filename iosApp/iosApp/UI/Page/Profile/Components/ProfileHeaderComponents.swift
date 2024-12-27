import SwiftUI
import shared
import MarkdownUI

struct ProfileHeaderSuccess: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    
    var body: some View {
        CommonProfileHeader(user: user, relation: relation, isMe: isMe, onFollowClick: onFollowClick)
    }
}

struct MatrixView: View {
    let followCount: String
    let fansCount: String
    var body: some View {
        HStack {
            Text(followCount)
                .fontWeight(.bold)
            Text("正在关注")
                .foregroundColor(.secondary)
                .font(.footnote)
            Divider()
            Text(fansCount)
                .fontWeight(.bold)
            Text("关注者")
                .foregroundColor(.secondary)
                .font(.footnote)
        }
        .font(.caption)
    }
}

struct FieldsView: View {
    let fields: [String: UiRichText]
    var body: some View {
        if fields.count > 0 {
            VStack(alignment: .leading) {
                let keys = fields.map {
                    $0.key
                }.sorted()  
                ForEach(0..<keys.count, id: \.self) { index in
                    let key = keys[index]
                    Text(key)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Markdown(fields[key]?.markdown ?? "").markdownTextStyle(textStyle: {
                        FontFamilyVariant(.normal)
                        FontSize(.em(0.9))
                        ForegroundColor(.primary)
                    }).markdownInlineImageProvider(.emoji)
                        .padding(.vertical, 4)
                    if index != keys.count - 1 {
                        Divider()
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical)
            #if os(iOS)
                .background(Color(UIColor.secondarySystemBackground))
            #else
                .background(Color(NSColor.windowBackgroundColor))
            #endif
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            EmptyView()
        }
    }
} 