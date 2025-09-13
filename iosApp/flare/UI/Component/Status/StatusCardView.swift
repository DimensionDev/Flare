import SwiftUI
import KotlinSharedUI

struct StatusCardView: View {
    @Environment(\.openURL) private var openURL
    let data: UiCard
    
    var body: some View {
        VStack {
            if let media = data.media {
                AdaptiveMosaic([media], singleMode: .force16x9) { item in
                    MediaView(data: item)
                        .clipped()
                }
                .clipped()
            }
            VStack {
                Text(data.title)
                    .lineLimit(2)
                if let desc = data.description_ {
                    Text(desc)
                        .font(.caption)
                        .lineLimit(2)
                }
            }
            .padding()
        }
        .clipShape(.rect(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .onTapGesture {
            openURL.callAsFunction(.init(string: data.url)!)
        }
    }
}
