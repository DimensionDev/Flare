import SwiftUI
import KotlinSharedUI

struct FeedView: View {
    @Environment(\.openURL) private var openURL
    let data: UiTimelineV2.Feed
//    @State private var showDetail = false
    private var descriptionText: String? {
        guard let description = data.description_, !description.isEmpty else {
            return nil
        }
        return description
    }
    var body: some View {
        let desc = descriptionText
        VStack(
            alignment: .leading
        ) {
            HStack {
                if let sourceIcon = data.source.icon, !sourceIcon.isEmpty {
                    NetworkImage(data: sourceIcon)
                        .frame(width: 20, height: 20)
                }
                Text(data.source.name)
                    .font(.footnote)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                if data.translationDisplayState != .hidden {
                    TranslateStatusComponent(data: data.translationDisplayState)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if let date = data.actualCreatedAt {
                    DateTimeText(data: date)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            if let title = data.title {
                Text(title)
            }
            if desc != nil || data.media != nil {
                HStack(alignment: .top, spacing: 8) {
                    if let desc {
                        Text(desc)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(5)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    if let image = data.media {
                        NetworkImage(data: image.url, customHeader: image.customHeaders)
                            .if(desc != nil, transform: { view in
                                view.frame(width: 80, height: 80)
                            })
                            .if(desc == nil, transform: { view in
                                view
                                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                                    .frame(maxWidth: .infinity)
                            })
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
        }
        .onTapGesture {
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        }
    }
}
