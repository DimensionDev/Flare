import SwiftUI
import KotlinSharedUI
import Kingfisher
import FlareAppleCore

struct StatusReactionView: View {
    @Environment(\.openURL) private var openURL
    @State private var expanded = false

    let data: [UiTimelineV2.PostEmojiReaction]
    let isDetail: Bool

    var body: some View {
        ReactionFlowLayout(maxLines: isDetail || expanded ? nil : 2) {
            ForEach(data, id: \.name) { item in
                Button {
                    item.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                } label: {
                    HStack(spacing: 4) {
                        if item.isUnicode {
                            Text(item.name)
                        } else {
                            ReactionImage(url: item.url)
                        }
                        Text(item.count.humanized)
                    }
                    .lineLimit(1)
                    .foregroundStyle(Color.flareLabel)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        item.me
                            ? Color.accentColor.opacity(0.18)
                            : Color.flareSecondarySystemGroupedBackground,
                        in: Capsule()
                    )
                    .overlay {
                        Capsule()
                            .stroke(item.me ? Color.accentColor : Color.clear, lineWidth: 0.8)
                    }
                    .fixedSize()
                }
                .buttonStyle(.plain)
            }

            if !isDetail && !expanded {
                Button {
                    expanded = true
                } label: {
                    Text("mastodon_item_show_more", bundle: FlareAppleUILocalization.bundle)
                        .font(.caption)
                        .foregroundStyle(Color.flareSecondaryLabel)
                }
                .buttonStyle(.plain)
                .layoutValue(key: ReactionOverflowIndicatorKey.self, value: true)
            }
        }
    }
}

private struct ReactionImage: View {
    private static let height = CGFloat(16)

    let url: String
    @State private var loadedImage: LoadedImage?

    private struct LoadedImage {
        let url: String
        let image: KFCrossPlatformImage
    }

    var body: some View {
        Group {
            if let image {
                if image.kf.frameSource == nil {
                    #if os(macOS)
                    Image(nsImage: image)
                        .resizable()
                        .scaledToFit()
                    #else
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                    #endif
                } else {
                    ReactionPlatformImage(image: image)
                }
            } else {
                Color.clear
            }
        }
            .frame(width: imageWidth, height: Self.height)
            .clipped()
            .task(id: url) {
                let requestedURL = url
                guard let imageURL = URL(string: requestedURL),
                      let result = try? await KingfisherManager.shared.retrieveImage(with: imageURL),
                      !Task.isCancelled else { return }
                loadedImage = LoadedImage(url: requestedURL, image: result.image)
            }
    }

    private var image: KFCrossPlatformImage? {
        loadedImage?.url == url ? loadedImage?.image : nil
    }

    private var imageWidth: CGFloat {
        guard let image, image.size.height > 0 else { return 0 }
        let width = Self.height * image.size.width / image.size.height
        return width.isFinite && width > 0 ? width : 0
    }
}

#if os(macOS)
private struct ReactionPlatformImage: NSViewRepresentable {
    let image: KFCrossPlatformImage

    func makeNSView(context: Context) -> AnimatedImageView {
        let imageView = AnimatedImageView()
        imageView.imageScaling = .scaleProportionallyUpOrDown
        imageView.needsPrescaling = false
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return imageView
    }

    func updateNSView(_ imageView: AnimatedImageView, context: Context) {
        imageView.image = image
    }
}
#else
private struct ReactionPlatformImage: UIViewRepresentable {
    let image: KFCrossPlatformImage

    func makeUIView(context: Context) -> AnimatedImageView {
        let imageView = AnimatedImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.needsPrescaling = false
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        imageView.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return imageView
    }

    func updateUIView(_ imageView: AnimatedImageView, context: Context) {
        imageView.image = image
    }
}
#endif

nonisolated private struct ReactionOverflowIndicatorKey: LayoutValueKey {
    static let defaultValue = false
}

private struct ReactionFlowLayout: Layout {
    let maxLines: Int?
    private let spacing: CGFloat = 8

    private struct Item {
        let index: Int
        let size: CGSize
    }

    private struct Row {
        var items: [Item] = []
        var width: CGFloat = 0
        var height: CGFloat = 0

        mutating func append(_ item: Item, spacing: CGFloat) {
            if !items.isEmpty {
                width += spacing
            }
            items.append(item)
            width += item.size.width
            height = max(height, item.size.height)
        }

        mutating func removeLast(spacing: CGFloat) {
            let item = items.removeLast()
            width -= item.size.width
            if !items.isEmpty {
                width -= spacing
            }
            height = items.map(\.size.height).max() ?? 0
        }

        func canFit(_ item: Item, in maxWidth: CGFloat, spacing: CGFloat) -> Bool {
            items.isEmpty || width + spacing + item.size.width <= maxWidth
        }
    }

    private struct Result {
        let rows: [Row]
        let size: CGSize
    }

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        makeResult(maxWidth: proposal.width, subviews: subviews).size
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let result = makeResult(maxWidth: bounds.width, subviews: subviews)
        var rowY = bounds.minY

        for row in result.rows {
            var leading = CGFloat.zero
            for item in row.items {
                let itemX = if subviews.layoutDirection == .rightToLeft {
                    bounds.maxX - leading - item.size.width
                } else {
                    bounds.minX + leading
                }
                subviews[item.index].place(
                    at: CGPoint(x: itemX, y: rowY + (row.height - item.size.height) / 2),
                    anchor: .topLeading,
                    proposal: ProposedViewSize(item.size)
                )
                leading += item.size.width + spacing
            }
            rowY += row.height + spacing
        }

        let placedIndices = Set(result.rows.flatMap { $0.items.map(\.index) })
        for index in subviews.indices where !placedIndices.contains(index) {
            subviews[index].place(
                at: CGPoint(x: bounds.maxX + 10_000, y: bounds.maxY + 10_000),
                anchor: .topLeading,
                proposal: .zero
            )
        }
    }

    private func makeResult(maxWidth proposedWidth: CGFloat?, subviews: Subviews) -> Result {
        let overflowIndex = subviews.indices.first { subviews[$0][ReactionOverflowIndicatorKey.self] }
        let contentIndices = subviews.indices.filter { $0 != overflowIndex }
        let content = contentIndices.map { Item(index: $0, size: subviews[$0].sizeThatFits(.unspecified)) }
        let naturalWidth = content.reduce(CGFloat.zero) { $0 + $1.size.width }
            + spacing * CGFloat(max(content.count - 1, 0))
        let maxWidth = proposedWidth?.isFinite == true ? max(proposedWidth ?? 0, 0) : naturalWidth
        let lineLimit = max(maxLines ?? Int.max, 1)
        var rows: [Row] = []
        var placedCount = 0

        for item in content {
            if rows.isEmpty {
                rows.append(Row())
            }
            if !rows[rows.count - 1].canFit(item, in: maxWidth, spacing: spacing) {
                guard rows.count < lineLimit else { break }
                rows.append(Row())
            }
            rows[rows.count - 1].append(item, spacing: spacing)
            placedCount += 1
        }

        if placedCount < content.count, let overflowIndex {
            let overflow = Item(index: overflowIndex, size: subviews[overflowIndex].sizeThatFits(.unspecified))
            while let last = rows.indices.last,
                  !rows[last].canFit(overflow, in: maxWidth, spacing: spacing),
                  !rows[last].items.isEmpty {
                rows[last].removeLast(spacing: spacing)
            }
            if rows.isEmpty {
                rows.append(Row())
            }
            rows[rows.count - 1].append(overflow, spacing: spacing)
        }

        assert(maxLines == nil || rows.count <= lineLimit)
        let usedWidth = rows.map(\.width).max() ?? 0
        let height = rows.reduce(CGFloat.zero) { $0 + $1.height }
            + spacing * CGFloat(max(rows.count - 1, 0))
        let width = proposedWidth?.isFinite == true ? maxWidth : usedWidth
        return Result(rows: rows, size: CGSize(width: width, height: height))
    }
}
