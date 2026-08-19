import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import FlareAppleCore

private let statusMediaMaxVisibleMediaCount = 9

struct StatusMediaView: View {
    let post: UiTimelineV2.Post
    let data: [any UiMedia]
    let sensitive: Bool
    let onMediaClicked: (any UiMedia, Int) -> Void
    let cornerRadius: CGFloat
    let allowsCarousel: Bool
    let carouselLeadingPadding: CGFloat
    let carouselTrailingPadding: CGFloat
    @Environment(\.timelineAppearance.expandMediaSize) private var expandMediaSize
    @Environment(\.timelineAppearance.mediaLayout) private var mediaLayout
    @Environment(\.timelineAppearance.limitMediaGridToNine) private var limitMediaGridToNine
    @Environment(\.timelineMediaActionHandler) private var timelineMediaActionHandler
    @State private var isBlur: Bool
    @State private var activeCarouselIndex: Int?
    @State private var autoplayCarouselIndex: Int?
//    @State private var selectedIndex: Int? = nil

    init(
        post: UiTimelineV2.Post,
        data: [any UiMedia],
        sensitive: Bool,
        cornerRadius: CGFloat,
        allowsCarousel: Bool,
        carouselLeadingPadding: CGFloat,
        carouselTrailingPadding: CGFloat,
        onMediaClicked: @escaping (any UiMedia, Int) -> Void
    ) {
        self.post = post
        self.data = data
        self.sensitive = sensitive
        self.onMediaClicked = onMediaClicked
        self.cornerRadius = cornerRadius
        self.allowsCarousel = allowsCarousel
        self.carouselLeadingPadding = carouselLeadingPadding
        self.carouselTrailingPadding = carouselTrailingPadding
        self._isBlur = State(initialValue: sensitive)
        self._activeCarouselIndex = State(initialValue: nil)
        self._autoplayCarouselIndex = State(initialValue: data.indices.first)
    }

    var body: some View {
        mediaContent
        .blur(radius: isBlur ? 20 : 0)
        .overlay(
            alignment: isBlur ? .center : .topLeading
        ) {
            if sensitive {
                if isBlur {
                    Button {
                        withAnimation {
                            isBlur = false
                        }
                    } label: {
                        Label {
                            Text(
                                "profile_blocked_gate_show",
                                bundle: FlareAppleUILocalization.bundle,
                                comment: "Button to show sensitive media"
                            )
                        } icon: {
                            Image(fontAwesome: .eye)
                                .foregroundStyle(.white)
                        }
                    }
                    .backport
                    .glassProminentButtonStyle()
                    .padding()
                } else {
                    Button {
                        withAnimation {
                            isBlur = true
                        }
                    } label: {
                        Image(fontAwesome: .eyeSlash)
                    }
                    .backport
                    .glassButtonStyle(fallbackStyle: .bordered)
                    .padding()
                }
            } else {
                EmptyView()
            }
        }
        .if(!usesCarousel) { view in
            view.clipShape(.rect(cornerRadius: cornerRadius))
        }
        .onChange(of: usesCarousel) { _, enabled in
            if enabled {
                autoplayCarouselIndex = data.indices.first
            } else {
                activeCarouselIndex = nil
                autoplayCarouselIndex = nil
            }
        }
        .onChange(of: data.count) { _, count in
            if let activeCarouselIndex, activeCarouselIndex >= count {
                self.activeCarouselIndex = nil
            }
            if let autoplayCarouselIndex, autoplayCarouselIndex >= count {
                self.autoplayCarouselIndex = data.indices.first
            }
        }
    }

    private var usesCarousel: Bool {
        allowsCarousel && mediaLayout == .carousel && data.count > 1
    }

    @ViewBuilder
    private var mediaContent: some View {
        if usesCarousel {
            let layoutSpec = carouselLayoutSpec
            let horizontalInsets = carouselLeadingPadding + carouselTrailingPadding
            TimelineCarouselFrameLayout(
                widthMultiplier: CGFloat(layoutSpec.widthMultiplier),
                spacingMultiplier: CGFloat(layoutSpec.spacingMultiplier),
                horizontalInsets: horizontalInsets,
                itemSpacing: 4
            ) {
                GeometryReader { geometry in
                    let contentWidth = max(0, geometry.size.width - horizontalInsets)
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: 4) {
                            ForEach(data.indices, id: \.self) { index in
                                let itemSize = carouselItemSize(
                                    data[index],
                                    containerHeight: geometry.size.height,
                                    contentWidth: contentWidth,
                                    maxItemWidthMultiplier: CGFloat(layoutSpec.maxItemWidthMultiplier)
                                )
                                mediaItem(
                                    data[index],
                                    index: index,
                                    overflowCount: 0,
                                    allowsAutoplay: !isBlur &&
                                        index == autoplayCarouselIndex
                                )
                                .frame(width: itemSize.width, height: itemSize.height)
                                .clipShape(.rect(cornerRadius: cornerRadius))
                                .id(index)
                            }
                        }
                        .scrollTargetLayout()
                        .frame(height: geometry.size.height)
                        .padding(.leading, carouselLeadingPadding)
                        .padding(.trailing, carouselTrailingPadding)
                    }
                    .scrollIndicators(.hidden)
                    .scrollPosition(id: $activeCarouselIndex, anchor: .center)
                    .onChange(of: activeCarouselIndex) { _, index in
                        if let index, index != autoplayCarouselIndex {
                            autoplayCarouselIndex = nil
                        }
                    }
                    .task(id: activeCarouselIndex) {
                        guard let activeCarouselIndex else { return }
                        try? await Task.sleep(nanoseconds: 150_000_000)
                        guard !Task.isCancelled else { return }
                        autoplayCarouselIndex = activeCarouselIndex
                    }
                }
            }
            .padding(.leading, -carouselLeadingPadding)
            .padding(.trailing, -carouselTrailingPadding)
            .fixedSize(horizontal: false, vertical: true)
        } else {
            mediaGrid
        }
    }

    private var carouselLayoutSpec: TimelineCarouselLayoutSpec {
        TimelineCarouselLayout.shared.spec(
            mediaCount: Int32(data.count),
            firstAspectRatio: Float(data.first?.carouselLayoutAspectRatio ?? 0),
            secondAspectRatio: Float(data.dropFirst().first?.carouselLayoutAspectRatio ?? 0)
        )
    }

    private func carouselItemSize(
        _ item: any UiMedia,
        containerHeight: CGFloat,
        contentWidth: CGFloat,
        maxItemWidthMultiplier: CGFloat
    ) -> CGSize {
        let sourceRatio = item.aspectRatio.flatMap { $0.isFinite && $0 > 0 ? $0 : nil } ?? 1
        return CGSize(
            width: min(containerHeight * sourceRatio, contentWidth * maxItemWidthMultiplier),
            height: containerHeight
        )
    }

    private var mediaGrid: some View {
        let visibleData = limitMediaGridToNine ? Array(data.prefix(statusMediaMaxVisibleMediaCount)) : data
        let overflowCount = data.count - visibleData.count
        return AdaptiveGrid(
            singleFollowsImageAspect: expandMediaSize,
            singleViewAspectRatio: data.first?.aspectRatio,
            spacing: 4,
            maxColumns: 3,
        ) {
            ForEach(0..<visibleData.count, id: \.self) { index in
                mediaItem(
                    visibleData[index],
                    index: index,
                    overflowCount: index == visibleData.count - 1 ? overflowCount : 0,
                    allowsAutoplay: !isBlur
                )
            }
        }
    }

    private func mediaItem(
        _ item: any UiMedia,
        index: Int,
        overflowCount: Int,
        allowsAutoplay: Bool
    ) -> some View {
        MediaView(data: item, allowsAutoplay: allowsAutoplay)
            .onTapGesture {
                if !sensitive || !isBlur {
                    onMediaClicked(item, index)
                }
            }
            .overlay {
                if overflowCount > 0 {
                    MediaOverflowOverlay(count: overflowCount)
                        .allowsHitTesting(false)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if let alt = item.description_, !alt.isEmpty {
                    AltTextOverlay(altText: alt)
                }
            }
            .if(!isBlur && timelineMediaActionHandler != nil) { view in
                view.contextMenu {
                    if let timelineMediaActionHandler {
                        TimelineMediaContextMenu(
                            post: post,
                            media: item,
                            showsDownloadAll: data.count > 1,
                            actionHandler: timelineMediaActionHandler
                        )
                    }
                }
            }
    }
}

private struct TimelineCarouselFrameLayout: Layout {
    let widthMultiplier: CGFloat
    let spacingMultiplier: CGFloat
    let horizontalInsets: CGFloat
    let itemSpacing: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let width = max(0, proposal.width ?? 320)
        let contentWidth = max(0, width - horizontalInsets)
        let height = max(0, contentWidth * widthMultiplier + itemSpacing * spacingMultiplier)
        return CGSize(width: width, height: height)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        guard let subview = subviews.first else { return }
        subview.place(
            at: bounds.origin,
            anchor: .topLeading,
            proposal: ProposedViewSize(width: bounds.width, height: bounds.height)
        )
    }
}

private struct TimelineMediaContextMenu: View {
    let post: UiTimelineV2.Post
    let media: any UiMedia
    let showsDownloadAll: Bool
    let actionHandler: TimelineMediaActionHandler

    var body: some View {
        Button {
            actionHandler(post, media, .download)
        } label: {
            Label {
                Text("media_menu_download", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(fontAwesome: .download)
            }
        }

        if showsDownloadAll {
            Button {
                actionHandler(post, media, .downloadAll)
            } label: {
                Label {
                    Text("media_menu_download_all", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(fontAwesome: .download)
                }
            }
        }

        if case .image = onEnum(of: media) {
            Button {
                actionHandler(post, media, .shareImage)
            } label: {
                Label {
                    Text("media_menu_share_image", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(fontAwesome: .shareNodes)
                }
            }
        }

        Button {
            actionHandler(post, media, .copyLink)
        } label: {
            Label {
                Text("media_menu_copy_link", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(systemName: "doc.on.doc")
            }
        }
    }
}

private struct MediaOverflowOverlay: View {
    let count: Int

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
            Text(count.mediaOverflowDisplayText)
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundStyle(.white)
        }
    }
}

private extension Int {
    var mediaOverflowDisplayText: String {
        self >= 100 ? "99+" : "+\(self)"
    }
}

struct AltTextOverlay: View {
    let altText: String
    @State private var showAltText: Bool = false

    var body: some View {
        Button {
            showAltText = true
        } label: {
            Text("ALT", bundle: FlareAppleUILocalization.bundle)
        }
        .padding()
        .backport
        .glassButtonStyle(fallbackStyle: .bordered)
        .popover(isPresented: $showAltText) {
            Text(altText)
                .padding()
                .frame(width: 280)
                .presentationCompactAdaptation(.popover)
        }
    }
}

public extension UiMedia {
    var aspectRatio: CGFloat? {
        switch onEnum(of: self) {
        case .image(let image): return CGFloat(image.aspectRatio)
        case .video(let video): return CGFloat(video.aspectRatio)
        case .gif(let gifv): return CGFloat(gifv.aspectRatio)
        case .audio: return nil
        }
    }

    var carouselLayoutAspectRatio: CGFloat? {
        let size: (width: CGFloat, height: CGFloat)? = switch onEnum(of: self) {
        case .image(let image): (CGFloat(image.width), CGFloat(image.height))
        case .video(let video): (CGFloat(video.width), CGFloat(video.height))
        case .gif(let gif): (CGFloat(gif.width), CGFloat(gif.height))
        case .audio: nil
        }
        guard let size,
              size.width.isFinite,
              size.height.isFinite,
              size.width > 0,
              size.height > 0 else {
            return nil
        }
        return size.width / size.height
    }

    var mediaPreviewURL: String? {
        switch onEnum(of: self) {
        case .image(let image): image.previewUrl
        case .video(let video): video.thumbnailUrl
        case .gif(let gif): gif.previewUrl
        case .audio: nil
        }
    }

    var isVideoMedia: Bool {
        if case .video = onEnum(of: self) {
            return true
        }
        return false
    }
}
