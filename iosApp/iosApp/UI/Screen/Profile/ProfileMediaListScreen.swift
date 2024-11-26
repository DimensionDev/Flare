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
    
    init(accountType: AccountType, userKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, userKey: userKey)
    }
    
    var body: some View {
        ObservePresenter<ProfileMediaState, ProfileMediaPresenter, AnyView>(presenter: presenter) { state in
            AnyView(
                WaterfallCollectionView(state: state) { item in
                    MediaGridItem(media: item.media) {
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
        .navigationTitle("Media")
    }
    
    private func showPhotoBrowser(media: UiMedia, images: [UiMedia], initialIndex: Int) {
        let browser = JXPhotoBrowser()
        browser.scrollDirection = .horizontal
        
        // 设置图片数量回调
        browser.numberOfItems = { images.count }
        
        // 设置初始索引
        browser.pageIndex = initialIndex
        
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
        
        browser.reloadCellAtIndex = { context in
            guard context.index >= 0, context.index < images.count else { return }
            let media = images[context.index]
            
            switch onEnum(of: media) {
            case .video(let data):
                if let url = URL(string: data.url ?? ""),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.loadVideo(url: url, thumbnailUrl: data.thumbnailUrl)
                }
            case .image(let data):
                if let url = URL(string: data.url ?? ""),
                   let cell = context.cell as? JXPhotoBrowserImageCell {
                    cell.imageView.kf.setImage(with: url, options: [
                        .transition(.fade(0.25)),
                        .processor(DownsamplingImageProcessor(size: UIScreen.main.bounds.size))
                    ])
                }
            case .gif(let data):
                if let url = URL(string: data.url ?? ""),
                   let cell = context.cell as? MediaBrowserVideoCell {
                    cell.loadVideo(url: url, thumbnailUrl: data.previewUrl)
                }
            case .audio:
                break
            }
        }
        
        // 视频播放控制
        browser.cellWillAppear = { cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.player.play()
                }
            default:
                break
            }
        }
        
        browser.cellWillDisappear = { cell, index in
            let media = images[index]
            switch onEnum(of: media) {
            case .video, .gif:
                if let videoCell = cell as? MediaBrowserVideoCell {
                    videoCell.player.pause()
                }
            default:
                break
            }
        }
        
        browser.show()
    }
}

// MARK: - VideoCell
class MediaBrowserVideoCell: UIView, JXPhotoBrowserCell {
    weak var photoBrowser: JXPhotoBrowser?
    
    lazy var player = AVPlayer()
    lazy var playerLayer = AVPlayerLayer(player: player)
    private let progressView = UIProgressView(progressViewStyle: .default)
    private let thumbnailImageView = UIImageView()
    
    static func generate(with browser: JXPhotoBrowser) -> Self {
        let instance = Self.init(frame: .zero)
        instance.photoBrowser = browser
        return instance
    }
    
    required override init(frame: CGRect) {
        super.init(frame: .zero)
        backgroundColor = .black
        
        thumbnailImageView.contentMode = .scaleAspectFit
        addSubview(thumbnailImageView)
        thumbnailImageView.translatesAutoresizingMaskIntoConstraints = false
        
        progressView.progressTintColor = .white
        progressView.trackTintColor = .gray
        addSubview(progressView)
        progressView.translatesAutoresizingMaskIntoConstraints = false
        
        layer.addSublayer(playerLayer)
        
        NSLayoutConstraint.activate([
            thumbnailImageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            thumbnailImageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            thumbnailImageView.topAnchor.constraint(equalTo: topAnchor),
            thumbnailImageView.bottomAnchor.constraint(equalTo: bottomAnchor),
            
            progressView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            progressView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -20),
            progressView.centerYAnchor.constraint(equalTo: centerYAnchor)
        ])
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(click))
        addGestureRecognizer(tap)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
    
    func loadVideo(url: URL, thumbnailUrl: String?) {
        // 显示缩略图
        if let thumbnailUrl = thumbnailUrl, let url = URL(string: thumbnailUrl) {
            thumbnailImageView.kf.setImage(with: url)
        }
        
        // 设置视频
        let asset = AVURLAsset(url: url)
        let playerItem = AVPlayerItem(asset: asset)
        
        // 监听加载进度
        playerItem.addObserver(self, forKeyPath: "loadedTimeRanges", options: .new, context: nil)
        
        // 监听播放状态
        NotificationCenter.default.addObserver(self,
                                             selector: #selector(playerItemDidReadyToPlay),
                                             name: .AVPlayerItemNewErrorLogEntry,
                                             object: playerItem)
        
        player.replaceCurrentItem(with: playerItem)
    }
    
    @objc private func playerItemDidReadyToPlay() {
        thumbnailImageView.isHidden = true
        progressView.isHidden = true
        player.play()
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        if keyPath == "loadedTimeRanges",
           let playerItem = object as? AVPlayerItem {
            let loadedTimeRanges = playerItem.loadedTimeRanges
            if let timeRange = loadedTimeRanges.first?.timeRangeValue {
                let bufferedDuration = CMTimeGetSeconds(timeRange.duration)
                let totalDuration = CMTimeGetSeconds(playerItem.duration)
                let progress = Float(bufferedDuration / totalDuration)
                DispatchQueue.main.async {
                    self.progressView.progress = progress
                }
            }
        }
    }
    
    deinit {
        player.currentItem?.removeObserver(self, forKeyPath: "loadedTimeRanges")
    }
    
    @objc private func click() {
        photoBrowser?.dismiss()
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

struct MediaGridItem: View {
    let media: UiMedia
    let onTap: () -> Void
    
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
                            Text("Video")
                                .font(.caption)
                                .padding(4)
                                .background(.ultraThinMaterial)
                            Spacer()
                        }
                        Spacer()
                    }
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
                        .blur(radius: image.sensitive ? 20 : 0)
                    
                    if image.sensitive {
                        Text("Sensitive Content")
                            .foregroundColor(.white)
                            .padding(8)
                            .background(.ultraThinMaterial)
                            .cornerRadius(8)
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
