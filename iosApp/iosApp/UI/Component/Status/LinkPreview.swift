import SwiftUI
import shared

struct LinkPreview: View {
    let card: UiCard
    var body: some View {
        Link(destination: URL(string: card.url)!) {
            HStack {
                if let media = card.media {
                    MediaItemComponent(media: media)
                        .frame(width: 64, height: 64)
                }
                VStack(alignment: .leading) {
                    Text(card.title)
                        .lineLimit(1)
                    if let desc = card.description_ {
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
        .buttonStyle(.plain)
        .background(Color(uiColor: UIColor.secondarySystemBackground))
        .cornerRadius(8)
    }
}
