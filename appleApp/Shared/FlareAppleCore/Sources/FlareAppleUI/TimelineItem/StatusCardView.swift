import SwiftUI
import KotlinSharedUI
import FlareAppleCore

struct StatusCardView: View {
    @Environment(\.openURL) private var openURL
    let data: UiCard
    let cornerRadius: CGFloat

    var body: some View {
        Button(action: openCard) {
            VStack(
                alignment: .leading,
                spacing: 0
            ) {
                if let media = data.media {
                    AdaptiveGrid(singleFollowsImageAspect: false) {
                        Color.clear
                            .overlay {
                                MediaView(data: media)
                                    .clipped()
                            }
                            .clipped()
                    }
                    .clipped()
                }
                VStack(
                    alignment: .leading,
                    spacing: 0
                ) {
                    Text(data.title)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .lineLimit(2)
                    if let desc = data.description_, !desc.isEmpty {
                        Text(desc)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    } else {
                        Text(data.url)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
                .padding(8)
            }
            .clipShape(.rect(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.flareSeparator, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func openCard() {
        guard let url = URL(string: data.url) else { return }
        openURL.callAsFunction(url)
    }
}

struct StatusCompatCardView: View {
    @Environment(\.openURL) private var openURL
    let data: UiCard
    let cornerRadius: CGFloat

    var body: some View {
        Button(action: openCard) {
            HStack(
                spacing: 0
            ) {
                if let media = data.media {
                    Color.clear
                        .overlay {
                            MediaView(data: media)
                                .clipped()
                        }
                        .clipped()
                        .frame(width: 72, height: 72)
                }
                VStack(
                    alignment: .leading,
                    spacing: 0
                ) {
                    Text(data.title)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .lineLimit(1)
                    if let desc = data.description_, !desc.isEmpty {
                        Text(desc)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    } else {
                        Text(data.url)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                .padding(8)
            }
            .clipShape(.rect(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.flareSeparator, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func openCard() {
        guard let url = URL(string: data.url) else { return }
        openURL.callAsFunction(url)
    }
}
