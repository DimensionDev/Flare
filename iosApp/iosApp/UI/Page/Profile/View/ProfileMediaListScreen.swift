import SwiftUI
import shared
import AVKit
import MarkdownUI
import OrderedCollections
import Kingfisher
import JXPhotoBrowser

// MARK: - ProfileMediaGridItem
struct ProfileMediaGridItem: Identifiable {
    let id: Int
    let media: UiMedia
    let mediaState: UiTimeline
}

// MARK: - ProfileMediaState Extension
extension ProfileMediaState {
    var allMediaItems: [UiMedia] {
        var items: [UiMedia] = []
        if case .success(let data) = onEnum(of: mediaState) {
            for i in 0..<data.itemCount {
                if let mediaItem = data.peek(index: i),
                   case .status(let statusData) = onEnum(of: mediaItem.status.content) {
                    // 按照 timeline 顺序收集所有媒体
                    items.append(contentsOf: statusData.images)
                }
            }
        }
        return items
    }
}

// MARK: - ProfileMediaListScreen
struct ProfileMediaListScreen: View {
    @State private var presenter: ProfileMediaPresenter
    @State private var refreshing = false
    @State private var selectedMedia: (media: UiMedia, index: Int)?
    @State private var showingMediaPreview = false
    @Environment(\.appSettings) private var appSettings
    @Environment(\.dismiss) private var dismiss
    
    init(accountType: AccountType, userKey: MicroBlogKey?) {
        presenter = .init(accountType: accountType, userKey: userKey)
    }

    var body: some View {
        ObservePresenter<ProfileMediaState, ProfileMediaPresenter, AnyView>(presenter: presenter) { state in
            AnyView(
                WaterfallCollectionView(state: state) { item in
                    ProfileMediaItemView(media: item.media, appSetting: appSettings) {
                        let allImages = state.allMediaItems
                        if !allImages.isEmpty,
                           let mediaIndex = allImages.firstIndex(where: { $0 === item.media }) {
                            print("Debug: Opening browser with \(allImages.count) images at index \(mediaIndex)")
                            showPhotoBrowser(media: item.media, images: allImages, initialIndex: mediaIndex)
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            )
        }
//        .navigationTitle("profile_tab_media")
    }

    private func showPhotoBrowser(media: UiMedia, images: [UiMedia], initialIndex: Int) {
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
            case .video(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.load(url: url, previewUrl: URL(string: data.thumbnailUrl), isGIF: false)
                }
            case .gif(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.load(url: url, previewUrl: URL(string: data.previewUrl), isGIF: true)
                }
            case .image(let data):
                if let url = URL(string: data.url),
                   let cell = context.cell as? JXPhotoBrowserImageCell {
                    cell.imageView.kf.setImage(with: url, options: [
                        .transition(.fade(0.25)),
                        .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size))
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
            return true
        }

        browser.show()
    }
}

// MARK: - WaterfallCollectionView
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
            if case .success(let success) = onEnum(of: parent.state.mediaState) {
                items = (0..<success.itemCount).compactMap { index -> ProfileMediaGridItem? in
                    guard let item = success.peek(index: index) else { return nil }
                    return ProfileMediaGridItem(id: Int(index), media: item.media, mediaState: item.status)
                }
                print("Updated items count: \(items.count)")
            }
        }

        // MARK: - UICollectionViewDataSource
        func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
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

        // MARK: - ZJFlexibleDataSource
        func numberOfCols(at section: Int) -> Int {
            return 2
        }

        func sizeOfItemAtIndexPath(at indexPath: IndexPath) -> CGSize {
            let item = items[indexPath.item]
            let width = (UIScreen.main.bounds.width - spaceOfCells(at: 0) * 3) / 2
            let height: CGFloat

            switch onEnum(of: item.media) {
            case .image(let data):
                print("Image size - width: \(data.width), height: \(data.height)")
                let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
                height = width / aspectRatio
            case .video(let data):
                print("Video size - width: \(data.width), height: \(data.height)")
                let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
                height = width / aspectRatio
            case .gif(let data):
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

        func spaceOfCells(at section: Int) -> CGFloat {
            return 4
        }

        func sectionInsets(at section: Int) -> UIEdgeInsets {
            return UIEdgeInsets(top: 4, left: 4, bottom: 4, right: 4)  // 减小边距
        }

        func sizeOfHeader(at section: Int) -> CGSize {
            return .zero
        }

        func heightOfAdditionalContent(at indexPath: IndexPath) -> CGFloat {
            return 0
        }
    }
}

// MARK: - HostingCell
class HostingCell: UICollectionViewCell {
    private var hostingController: UIHostingController<AnyView>?

    func setup(with view: AnyView) {
        if let hostingController = hostingController {
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
                controller.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
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
        case .image(let image):
            self._hideSensitive = State(initialValue: !appSetting.appearanceSettings.showSensitiveContent && image.sensitive)
        default:
            self._hideSensitive = State(initialValue: false)
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
//                        .blur(radius: media.url.sensitive ? 20 : 0)

//                    if media.sensitive {
//                        Text("Sensitive Content")
//                            .foregroundColor(.white)
//                            .padding(8)
//                            .background(.ultraThinMaterial)
//                            .cornerRadius(8)
//                    }
                }
            case .gif(let gif):
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
            case .image(let image):
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
//                        .fade(duration: 0.25)
                        .if(!appSetting.appearanceSettings.showSensitiveContent && image.sensitive && hideSensitive) { view in
                            view.blur(radius: 32)
                        }

                    if !appSetting.appearanceSettings.showSensitiveContent && image.sensitive {
                        SensitiveContentButton(
                            hideSensitive: hideSensitive,
                            action: { hideSensitive.toggle() }
                        )
                    }
                }
            case .video(let video):
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
//                        .fade(duration: 0.25)
//                        .blur(radius: video.sensitive ? 20 : 0)

                    Image(systemName: "play.circle.fill")
                        .font(.largeTitle)
                        .foregroundColor(.white)
                        .shadow(radius: 2)
//
//                    if video.sensitive {
//                        Text("Sensitive Content")
//                            .foregroundColor(.white)
//                            .padding(8)
//                            .background(.ultraThinMaterial)
//                            .cornerRadius(8)
//                    }
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - VideoCell
class MediaBrowserVideoCell: UIView, UIGestureRecognizerDelegate {
    weak var photoBrowser: JXPhotoBrowser?
    private var videoViewController: MediaPreviewVideoViewController?
    private let mediaSaver: MediaSaver
    private var currentURL: URL?
    private var existedPan: UIPanGestureRecognizer?

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    required init(frame: CGRect, mediaSaver: MediaSaver = DefaultMediaSaver.shared) {
        self.mediaSaver = mediaSaver
        super.init(frame: frame)
        setupUI()
    }

    private func setupUI() {
        backgroundColor = .black

        // 添加拖动手势
        let pan = UIPanGestureRecognizer(target: self, action: #selector(onPan(_:)))
        pan.delegate = self
        addGestureRecognizer(pan)
        existedPan = pan

        // 添加点击手势
        let tap = UITapGestureRecognizer(target: self, action: #selector(click))
        addGestureRecognizer(tap)
    }

    @objc private func onPan(_ pan: UIPanGestureRecognizer) {
        guard let parentView = superview else { return }

        let translation = pan.translation(in: parentView)
        let scale = 1 - abs(translation.y) / parentView.bounds.height

        switch pan.state {
        case .changed:
            // 跟随手指移动
            transform = CGAffineTransform(translationX: translation.x, y: translation.y)
                .scaledBy(x: scale, y: scale)

            // 调整背景透明度
            parentView.backgroundColor = UIColor.black.withAlphaComponent(scale)

        case .ended, .cancelled:
            let velocity = pan.velocity(in: parentView)
            let shouldDismiss = abs(translation.y) > 100 || abs(velocity.y) > 500

            if shouldDismiss {
                // 关闭浏览器
                photoBrowser?.dismiss()
            } else {
                // 恢复原位
                UIView.animate(withDuration: 0.3) {
                    self.transform = .identity
                    parentView.backgroundColor = .black
                }
            }

        default:
            break
        }
    }

    // UIGestureRecognizerDelegate
    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        // 允许同时识别多个手势
        return true
    }

    func load(url: URL, previewUrl: URL?, isGIF: Bool) {
        // 如果已经加载了相同的 URL，不需要重新加载
        if currentURL == url {
            return
        }

        // 先清理旧的资源
        cleanupCurrentVideo()
        currentURL = url

        // Create view model
        let viewModel = MediaPreviewVideoViewModel(
            mediaSaver: mediaSaver,
            item: isGIF ? .gif(.init(assetURL: url, previewURL: previewUrl))
                       : .video(.init(assetURL: url, previewURL: previewUrl))
        )

        // Create and setup new view controller
        let newVC = MediaPreviewVideoViewController()
        newVC.viewModel = viewModel
        videoViewController = newVC

        // Add to view hierarchy
        addSubview(newVC.view)
        newVC.view.translatesAutoresizingMaskIntoConstraints = false

        // Add as child view controller
        if let parentViewController = self.findViewController() {
            parentViewController.addChild(newVC)
            newVC.didMove(toParent: parentViewController)
        }

        NSLayoutConstraint.activate([
            newVC.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            newVC.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            newVC.view.topAnchor.constraint(equalTo: topAnchor),
            newVC.view.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])

        // 默认不自动播放，等待 willDisplay 时再播放
        viewModel.player?.pause()
    }

    private func cleanupCurrentVideo() {
        // 暂停当前播放的视频
        if let viewModel = videoViewController?.viewModel,
           let player = viewModel.player {
            player.pause()
            player.replaceCurrentItem(with: nil)  // 清除播放器项
        }

        // 移除视频控制器
        if let vc = videoViewController {
            vc.willMove(toParent: nil)
            vc.view.removeFromSuperview()
            vc.removeFromParent()
            videoViewController = nil
        }
        currentURL = nil
    }

    func willDisplay() {
        // 显示时开始播放视频
        if let viewModel = videoViewController?.viewModel,
           let player = viewModel.player {
            player.play()
        }
    }

    func didEndDisplaying() {
        cleanupCurrentVideo()
    }

    @objc private func click() {
        photoBrowser?.dismiss()
    }

    // Helper method to find parent view controller
    private func findViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let nextResponder = responder?.next {
            if let viewController = nextResponder as? UIViewController {
                return viewController
            }
            responder = nextResponder
        }
        return nil
    }
}

// MARK: - JXPhotoBrowserCell
extension MediaBrowserVideoCell: JXPhotoBrowserCell {
    static func generate(with browser: JXPhotoBrowser) -> Self {
        let instance = Self.init(frame: .zero)
        instance.photoBrowser = browser
        return instance
    }
}

//struct SensitiveContentButton: View {
//    let hideSensitive: Bool
//    let action: () -> Void
//    
//    var body: some View {
//        if hideSensitive {
//            Button(action: {
//                action()
//            }, label: {
//                Text("status_sensitive_media_show", comment: "Status media sensitive button")
//                    .foregroundColor(.white)
//                    .padding(.horizontal, 16)
//                    .padding(.vertical, 8)
//                    .background(.ultraThinMaterial)
//                    .cornerRadius(8)
//            })
//            .buttonStyle(.plain)
//            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
//        } else {
//            VStack {
//                HStack {
//                    Button(action: {
//                        action()
//                    }, label: {
//                        Image(systemName: "eye.slash")
//                            .foregroundColor(Color(uiColor: UIColor.systemBackground))
//                    })
//                    .padding()
//                    .buttonStyle(.borderedProminent)
//                    .tint(Color.primary)
//                    Spacer()
//                }
//                Spacer()
//            }
//        }
//    }
//}
