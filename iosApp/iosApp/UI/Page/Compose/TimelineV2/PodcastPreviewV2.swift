import SwiftUI

struct PodcastPreviewV2: View {
    let card: Card

    private var podcastId: String {
        URL(string: card.url)?.lastPathComponent ?? ""
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "headphones.circle.fill")
                .imageScale(.large)
                .foregroundColor(.pink)

            VStack(alignment: .leading, spacing: 2) {
                Text("X Audio Space")
                    .font(.subheadline.weight(.medium))
                    .foregroundColor(.primary)

                if !podcastId.isEmpty {
                    Text(podcastId)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer()
        }
        .padding(12)
        .background(.thinMaterial)
        .cornerRadius(10)
        .contentShape(Rectangle())
    }
}
