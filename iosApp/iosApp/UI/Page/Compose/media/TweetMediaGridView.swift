import shared
import SwiftUI

// tweet medias 布局gridView layout view
public struct TweetMediaGridView: View {
    static var spacing: CGFloat { 8 }
    static var cornerRadius: CGFloat { 12 }

    public let action: (ActionContext) -> Void

    @State
    public var viewModels: [FeedMediaViewModel]

    @State
    private var contentSize: CGSize = .zero

    public let idealWidth: CGFloat?
    public let idealHeight: CGFloat
    public var fixedAspectRatio: CGFloat?

    var cornerRadius: CGFloat {
        option.preferredApplyCornerRadius
            ? TweetMediaGridView.cornerRadius
            : .zero
    }

    public struct Option {
        public var horizontalPadding: CGFloat
        public var preferredPaddingGridLayout: Bool
        public var preferredApplyCornerRadius: Bool
        public var layoutAllMedias: Bool

        public init(
            horizontalPadding: CGFloat,
            preferredPaddingGridLayout: Bool,
            preferredApplyCornerRadius: Bool,
            layoutAllMedias: Bool
        ) {
            self.horizontalPadding = horizontalPadding
            self.preferredPaddingGridLayout = preferredPaddingGridLayout
            self.preferredApplyCornerRadius = preferredApplyCornerRadius
            self.layoutAllMedias = layoutAllMedias
        }
    }

    public let option: Option

    public init(
        action: @escaping (ActionContext) -> Void = { _ in },
        viewModels: [FeedMediaViewModel],
        idealWidth: CGFloat? = nil,
        idealHeight: CGFloat,
        fixedAspectRatio: CGFloat? = nil,
        horizontalPadding: CGFloat = .zero,
        preferredPaddingGridLayoutOnly: Bool = true,
        preferredApplyCornerRadius: Bool = false,
        layoutAllMedias: Bool = false
    ) {
        self.action = action
        self.viewModels = viewModels
        self.idealWidth = idealWidth
        self.idealHeight = idealHeight
        self.fixedAspectRatio = fixedAspectRatio
        option = Option(
            horizontalPadding: horizontalPadding,
            preferredPaddingGridLayout: preferredPaddingGridLayoutOnly,
            preferredApplyCornerRadius: preferredApplyCornerRadius,
            layoutAllMedias: layoutAllMedias
        )
    }

    public struct ActionContext {
        public let index: Int
        public let viewModels: [FeedMediaViewModel]
        public init(index: Int, viewModels: [FeedMediaViewModel]) {
            self.index = index
            self.viewModels = viewModels
        }
    }

    public var body: some View {
        Group {
            let viewModels = viewModels.filter(\.isActive)

            switch viewModels.count {
            case 0:
                EmptyView()
            case 1:
                singleMediaView(of: viewModels[0])
                    .frame(maxWidth: .infinity)
                    .frame(height: idealHeight)
                    .padding(.horizontal, option.horizontalPadding)
            case 2:
                HStack(spacing: Self.spacing) {
                    mediaView(of: viewModels[0], at: 0)
                    mediaView(of: viewModels[1], at: 1)
                }
                .frame(height: idealHeight / 2)
                .padding(.horizontal, option.horizontalPadding)
                .clipped()
            case 3:
                HStack(spacing: Self.spacing) {
                    mediaView(of: viewModels[0], at: 0)
                        .frame(maxWidth: .infinity)
                    VStack(spacing: Self.spacing) {
                        mediaView(of: viewModels[1], at: 1)
                        mediaView(of: viewModels[2], at: 2)
                    }
                    .frame(maxWidth: .infinity)
                }
                .frame(height: idealHeight)
                .padding(.horizontal, option.horizontalPadding)
            case 4:
                VStack(spacing: Self.spacing) {
                    HStack(spacing: Self.spacing) {
                        mediaView(of: viewModels[0], at: 0)
                        mediaView(of: viewModels[1], at: 1)
                    }
                    HStack(spacing: Self.spacing) {
                        mediaView(of: viewModels[2], at: 2)
                        mediaView(of: viewModels[3], at: 3)
                    }
                }
                .frame(height: idealHeight)
                .padding(.horizontal, option.horizontalPadding)
            default:
                CustomGrid(items: viewModels, columns: 3) { viewModel in
                    mediaView(of: viewModel, at: viewModels.firstIndex(where: { $0.id == viewModel.id }) ?? 0)
                }
                .frame(height: idealHeight)
                .padding(.horizontal, option.horizontalPadding)
            }
        }
    }

    // tweet 单张图片
    private func singleMediaView(of viewModel: FeedMediaViewModel) -> some View {
        SingleMediaView(
            viewModel: viewModel,
            isSingleVideo: true,
            fixedAspectRatio: fixedAspectRatio
        ) {
            let actionContext = ActionContext(index: 0, viewModels: viewModels)
            action(actionContext)
        }
        .cornerRadius(cornerRadius)
        .overlay(alignment: .bottomLeading) {
            if viewModel.mediaKind == .video {
                if let duration = viewModel.playableAsset?.duration {
                    TweetMediaPlayButton(duration: duration) {
                        let actionContext = ActionContext(index: 0, viewModels: viewModels)
                        action(actionContext)
                    }
                    .opacity(viewModel.mediaKind == .video ? 1.0 : 0.0)
                    .offset(x: 10, y: -10)
                }
            } else if viewModel.mediaKind == .audio {
                if let duration = viewModel.playableAsset?.duration {
                    TweetMediaPlayButton(duration: duration) {
                        let actionContext = ActionContext(index: 0, viewModels: viewModels)
                        action(actionContext)
                    }
                    .offset(x: 10, y: -10)
                }
            }
        }
    }

    // 方格中 --- 单个图片的view
    private func mediaView(of viewModel: FeedMediaViewModel, at index: Int) -> some View {
        SingleMediaView(
            viewModel: viewModel,
            isSingleVideo: false,
            fixedAspectRatio: nil
        ) {
            let actionContext = ActionContext(index: index, viewModels: viewModels)
            action(actionContext)
        }
        .cornerRadius(cornerRadius)
        .overlay(alignment: .bottomLeading) {
            if viewModel.mediaKind == .video {
                if let duration = viewModel.playableAsset?.duration {
                    TweetMediaPlayButton(duration: duration) {
                        let actionContext = ActionContext(index: index, viewModels: viewModels)
                        action(actionContext)
                    }
                    .opacity(viewModel.mediaKind == .video ? 1.0 : 0.0)
                    .offset(x: 10, y: -10)
                }
            } else if viewModel.mediaKind == .audio {
                if let duration = viewModel.playableAsset?.duration {
                    TweetMediaPlayButton(duration: duration) {
                        let actionContext = ActionContext(index: index, viewModels: viewModels)
                        action(actionContext)
                    }
                    .offset(x: 10, y: -10)
                }
            }
        }
    }
}
