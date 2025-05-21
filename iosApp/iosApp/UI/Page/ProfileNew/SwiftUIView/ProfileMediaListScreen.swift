import AVKit
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import OrderedCollections
import shared
import SwiftUI

// - ProfileMediaGridItem
struct ProfileMediaGridItem: Identifiable {
    let id: Int
    let media: UiMedia
    let mediaState: UiTimeline
}

// - ProfileMediaState Extension
extension ProfileMediaState {
    var allMediaItems: [UiMedia] {
        var items: [UiMedia] = []
        if case let .success(data) = onEnum(of: mediaState) {
            for i in 0 ..< data.itemCount {
                if let mediaItem = data.peek(index: i),
                   case let .status(statusData) = onEnum(of: mediaItem.status.content)
                {
                    // 按照 timeline 顺序收集所有媒体
                    items.append(contentsOf: statusData.images)
                }
            }
        }
        return items
    }
}

// - ProfileMediaListScreen
struct ProfileMediaListScreen: View {
//    @ObservedObject var tabStore: ProfileTabSettingStore
    @State private var currentMediaPresenter: ProfileMediaPresenter?

    @State private var refreshing = false
    @State private var selectedMedia: (media: UiMedia, index: Int)?
    @State private var showingMediaPreview = false
    @Environment(\.appSettings) private var appSettings
    @Environment(\.dismiss) private var dismiss

    // , tabStore: ProfileTabSettingStore
    init(accountType _: AccountType, userKey _: MicroBlogKey?, currentMediaPresenter _: ProfileMediaPresenter) {
//        self.tabStore = tabStore
    }

    var body: some View {
        if let presenter = currentMediaPresenter {
            ObservePresenter<ProfileMediaState, ProfileMediaPresenter, AnyView>(presenter: presenter) { state in
                AnyView(
                    WaterfallCollectionView(state: state) { item in
                        ProfileMediaItemView(media: item.media, appSetting: appSettings) {
                            let allImages = state.allMediaItems
                            if !allImages.isEmpty,
                               let mediaIndex = allImages.firstIndex(where: { $0 === item.media })
                            {
                                print("Debug: Opening browser with \(allImages.count) images at index \(mediaIndex)")
                                showPhotoBrowser(media: item.media, images: allImages, initialIndex: mediaIndex)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                )
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func showPhotoBrowser(media _: UiMedia, images: [UiMedia], initialIndex: Int) {
        let browser = JXPhotoBrowser()
        browser.scrollDirection = .horizontal
        browser.numberOfItems = { images.count }
        browser.pageIndex = initialIndex

        // 设置淡入淡出动画
        browser.transitionAnimator = JXPhotoBrowserFadeAnimator()

        // 根据媒体类型返回对应的 Cell
        browser.cellClassAtIndex = { index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                return MediaBrowserVideoCell.self
            default:
                return JXPhotoBrowserImageCell.self
            }
        }

        // 加载媒体内容
        browser.reloadCellAtIndex = { context in
            guard context.index >= 0, context.index < images.count else { return }
            let media = images[context.index]

            switch onEnum(of: media) {
            case let .video(data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell
                {
                    cell.load(url: url, previewUrl: URL(string: data.thumbnailUrl), isGIF: false)
                }
            case let .gif(data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell
                {
                    cell.load(url: url, previewUrl: URL(string: data.previewUrl), isGIF: true)
                }
            case let .image(data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? JXPhotoBrowserImageCell
                {
                    cell.imageView.kf.setImage(with: url, options: [
                        .transition(.fade(0.25)),
                        .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size)),
                    ])
                }
            default:
                break
            }
        }

        // Cell 将要显示
        browser.cellWillAppear = { cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.willDisplay()
                }
            default:
                break
            }
        }

        // Cell 将要消失
        browser.cellWillDisappear = { cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.didEndDisplaying()
                }
            default:
                break
            }
        }

        // 即将关闭时的处理
        browser.willDismiss = { _ in
            // 返回 true 表示执行动画
            true
        }

        browser.show()
    }
}

// - WaterfallCollectionView
struct WaterfallCollectionView: UIViewRepresentable {
    let state: ProfileMediaState
    let content: (ProfileMediaGridItem) -> AnyView

    init(state: ProfileMediaState, @ViewBuilder content: @escaping (ProfileMediaGridItem) -> some View) {
        self.state = state
        self.content = { AnyView(content($0)) }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> UICollectionView {
        let layout = ZJFlexibleLayout(delegate: context.coordinator)
        let collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.backgroundColor = .clear
        collectionView.dataSource = context.coordinator
        collectionView.register(HostingCell.self, forCellWithReuseIdentifier: "Cell")

        collectionView.translatesAutoresizingMaskIntoConstraints = false

        return collectionView
    }

    func updateUIView(_ collectionView: UICollectionView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.updateItems()

        DispatchQueue.main.async {
            collectionView.reloadData()
            collectionView.collectionViewLayout.invalidateLayout()
        }
    }

    class Coordinator: NSObject, UICollectionViewDataSource, ZJFlexibleDataSource {
        var parent: WaterfallCollectionView
        var items: [ProfileMediaGridItem] = []

        init(_ parent: WaterfallCollectionView) {
            self.parent = parent
            super.init()
            updateItems()
        }

        func updateItems() {
            if case let .success(success) = onEnum(of: parent.state.mediaState) {
                items = (0 ..< success.itemCount).compactMap { index -> ProfileMediaGridItem? in
                    guard let item = success.peek(index: index) else { return nil }
                    return ProfileMediaGridItem(id: Int(index), media: item.media, mediaState: item.status)
                }
                print("Updated items count: \(items.count)")
            }
        }

        // - UICollectionViewDataSource
        func collectionView(_: UICollectionView, numberOfItemsInSection _: Int) -> Int {
            print("numberOfItemsInSection: \(items.count)")
            return items.count
        }

        func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
            let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "Cell", for: indexPath) as! HostingCell
            let item = items[indexPath.item]
            print("Setting up cell at index: \(indexPath.item)")
            cell.setup(with: parent.content(item))
            return cell
        }

        // - ZJFlexibleDataSource
        func numberOfCols(at _: Int) -> Int {
            2
        }

        func sizeOfItemAtIndexPath(at indexPath: IndexPath) -> CGSize {
            let item = items[indexPath.item]
            let width = (UIScreen.main.bounds.width - spaceOfCells(at: 0) * 3) / 2
            let height: CGFloat

            switch onEnum(of: item.media) {
            case let .image(data):
                print("Image size - width: \(data.width), height: \(data.height)")
                let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
                height = width / aspectRatio
            case let .video(data):
                print("Video size - width: \(data.width), height: \(data.height)")
                let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
                height = width / aspectRatio
            case let .gif(data):
                print("Gif size - width: \(data.width), height: \(data.height)")
                let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
                height = width / aspectRatio
            case .audio:
                print("Audio item")
                height = width
            case .video:
                print("Video item")
                height = width
            }

            print("Calculated size - width: \(width), height: \(height)")
            return CGSize(width: width, height: height)
        }

        func spaceOfCells(at _: Int) -> CGFloat {
            4
        }

        func sectionInsets(at _: Int) -> UIEdgeInsets {
            UIEdgeInsets(top: 4, left: 4, bottom: 4, right: 4) // 减小边距
        }

        func sizeOfHeader(at _: Int) -> CGSize {
            .zero
        }

        func heightOfAdditionalContent(at _: IndexPath) -> CGFloat {
            0
        }
    }
}

// - HostingCell
class HostingCell: UICollectionViewCell {
    private var hostingController: UIHostingController<AnyView>?

    func setup(with view: AnyView) {
        if let hostingController {
            hostingController.rootView = view
        } else {
            let controller = UIHostingController(rootView: view)
            hostingController = controller
            controller.view.backgroundColor = .clear

            contentView.addSubview(controller.view)
            controller.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                controller.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
                controller.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
                controller.view.topAnchor.constraint(equalTo: contentView.topAnchor),
                controller.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            ])
        }
    }
}

struct ProfileMediaItemView: View {
    let media: UiMedia
    let appSetting: AppSettings
    let onTap: () -> Void
    @State private var hideSensitive: Bool

    init(media: UiMedia, appSetting: AppSettings, onTap: @escaping () -> Void) {
        self.media = media
        self.appSetting = appSetting
        self.onTap = onTap

        // 初始化 hideSensitive
        switch onEnum(of: media) {
        case let .image(image):
            _hideSensitive = State(initialValue: !appSetting.appearanceSettings.showSensitiveContent && image.sensitive)
        default:
            _hideSensitive = State(initialValue: false)
        }
    }

    var body: some View {
        ZStack {
            switch onEnum(of: media) {
            case .audio:
                ZStack {
                    Image(systemName: "waveform")
                        .font(.largeTitle)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(Color.gray.opacity(0.2))
//                       .blur(radius: media.url.sensitive ? 20 : 0)

//                   if media.sensitive {
//                       Text("Sensitive Content")
//                           .foregroundColor(.white)
//                           .padding(8)
//                           .background(.ultraThinMaterial)
//                           .cornerRadius(8)
//                   }
                }
            case let .gif(gif):
                ZStack {
                    KFImage(URL(string: gif.previewUrl))
                        .cacheOriginalImage()
                        .loadDiskFileSynchronously()
                        .placeholder {
                            ProgressView()
                        }
                        .onFailure { _ in
                            Image(systemName: "exclamationmark.triangle")
                                .font(.largeTitle)
                                .foregroundColor(.red)
                        }
                        .resizable()
                        .scaledToFit()

                    VStack {
                        HStack {
                            Text("GIF")
                                .font(.caption)
                                .padding(4)
                                .background(.ultraThinMaterial)
                            Spacer()
                        }
                        Spacer()
                    }
                }
                .onTapGesture {
                    onTap()
                }
            case let .image(image):
                ZStack {
                    KFImage(URL(string: image.previewUrl))
                        .cacheOriginalImage()
                        .loadDiskFileSynchronously()
                        .placeholder {
                            ProgressView()
                        }
                        .onFailure { _ in
                            Image(systemName: "exclamationmark.triangle")
                                .font(.largeTitle)
                                .foregroundColor(.red)
                        }
                        .resizable()
                        .scaledToFit()
//                       .fade(duration: 0.25)
                        .if(!appSetting.appearanceSettings.showSensitiveContent && image.sensitive && hideSensitive) { view in
                            view.blur(radius: 32)
                        }

                    if !appSetting.appearanceSettings.showSensitiveContent, image.sensitive {
                        SensitiveContentButton(
                            hideSensitive: hideSensitive,
                            action: { hideSensitive.toggle() }
                        )
                    }
                }
            case let .video(video):
                ZStack {
                    KFImage(URL(string: video.thumbnailUrl))
                        .cacheOriginalImage()
                        .loadDiskFileSynchronously()
                        .placeholder {
                            ProgressView()
                        }
                        .onFailure { _ in
                            Image(systemName: "exclamationmark.triangle")
                                .font(.largeTitle)
                                .foregroundColor(.red)
                        }
                        .resizable()
                        .scaledToFit()
//                       .fade(duration: 0.25)
//                       .blur(radius: video.sensitive ? 20 : 0)

                    Image(systemName: "play.circle.fill")
                        .font(.largeTitle)
                        .foregroundColor(.white)
                        .shadow(radius: 2)
//
//                   if video.sensitive {
//                       Text("Sensitive Content")
//                           .foregroundColor(.white)
//                           .padding(8)
//                           .background(.ultraThinMaterial)
//                           .cornerRadius(8)
//                   }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
}
