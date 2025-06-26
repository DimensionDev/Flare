import SwiftUI

struct LinkPreviewV2: View {
    let card: Card  // 使用Swift Card类型
    var body: some View {
        Link(destination: URL(string: card.url)!) {
            HStack {
                if let media = card.media {
                    // 使用V2版本的MediaItemComponent
                    MediaItemComponentV2(media: media)
                        .frame(width: 64, height: 64)
                }
                VStack(alignment: .leading) {
                    Text(card.title ?? "")  // 处理可选值
                        .lineLimit(1)
                    if let desc = card.description, !desc.isEmpty {  // 确保description不为空
                        Text(desc)
                            .font(.caption)
                            .foregroundStyle(.gray)
                            .lineLimit(2)
                    }
                }
                .foregroundStyle(.foreground)
                .if(card.media == nil) { view in
                    view.padding()
                }
                Spacer()
            }
        }
        .frame(maxWidth: 600)
        .buttonStyle(.plain)
        #if os(iOS)
            .background(Color(UIColor.secondarySystemBackground))
        #else
            .background(Color(NSColor.windowBackgroundColor))
        #endif
            .cornerRadius(8)
    }
}
