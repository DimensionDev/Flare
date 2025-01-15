import UIKit
import SwiftUI
import shared
import JXSegmentedView
import MJRefresh
import JXPhotoBrowser
import Kingfisher

class ProfileMediaViewController: UIViewController {
    //  - Properties
    private var presenterWrapper: ProfileMediaPresenterWrapper?
    private var scrollCallback: ((UIScrollView) -> ())?
    private var appSettings: AppSettings?
    
    private lazy var collectionView: UICollectionView = {
        let layout = ZJFlexibleLayout(delegate: self)
        let collection = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collection.backgroundColor = .clear
        collection.delegate = self
        collection.dataSource = self
        collection.register(MediaCollectionViewCell.self, forCellWithReuseIdentifier: "MediaCell")
        return collection
    }()
    
    private var items: [ProfileMediaGridItem] = []
    
    // - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupRefresh()
    }
    
    deinit {
        presenterWrapper = nil
        scrollCallback = nil
    }
    
    //  - Setup
    private func setupUI() {
        view.addSubview(collectionView)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func setupRefresh() {
        // 下拉刷新
        collectionView.mj_header = MJRefreshNormalHeader(refreshingBlock: { [weak self] in
            Task {
//                if let mediaPresenterWrapper = self?.presenterWrapper,
//                   case .success(let data) = onEnum(of: mediaPresenterWrapper.presenter.models.value.mediaState) {
//                    data.retry()
//                    await MainActor.run {
                        self?.collectionView.mj_header?.endRefreshing()
//                    }
//                }
            }
        })
        
        // 上拉加载更多
        collectionView.mj_footer = MJRefreshAutoNormalFooter(refreshingBlock: { [weak self] in
            Task {
//                if let mediaPresenterWrapper = self?.presenterWrapper,
//                   case .success(let data) = onEnum(of: mediaPresenterWrapper.presenter.models.value.mediaState) {
//                    // 检查是否还有更多数据
//                    let appendState = data.appendState
//                    if let notLoading = appendState as? Paging_commonLoadState.NotLoading,
//                       !notLoading.endOfPaginationReached {
//                        data.retry()
//                    }
//                    await MainActor.run {
//                        if let notLoading = appendState as? Paging_commonLoadState.NotLoading,
//                           notLoading.endOfPaginationReached {
//                            self?.collectionView.mj_footer?.endRefreshingWithNoMoreData()
//                        } else {
                            self?.collectionView.mj_footer?.endRefreshing()
//                        }
//                    }
//                }
             }
        })
    }
    
    //  - Public Methods
    func updateMediaPresenter(presenterWrapper: ProfileMediaPresenterWrapper) {
        self.presenterWrapper = presenterWrapper
        // 监听数据变化
        Task { @MainActor in
            let presenter = presenterWrapper.presenter
            for await state in presenter.models {
                self.handleState(state.mediaState)
            }
        }
    }
    
    func configure(with appSettings: AppSettings) {
        self.appSettings = appSettings
    }
    
    //  - Private Methods
    private func handleState(_ state: PagingState<ProfileMedia>) {
        if case .success(let data) = onEnum(of: state) {
            items = (0..<data.itemCount).compactMap { index -> ProfileMediaGridItem? in
                guard let item = data.peek(index: index) else { return nil }
                return ProfileMediaGridItem(id: Int(index), media: item.media, mediaState: item.status)
            }
            
            collectionView.reloadData()
            collectionView.mj_header?.endRefreshing()
            collectionView.mj_footer?.endRefreshing()
        } else {
            items = []
            collectionView.reloadData()
            collectionView.mj_header?.endRefreshing()
            collectionView.mj_footer?.endRefreshing()
        }
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

        browser.show()
    }
}

// MARK: - UICollectionViewDataSource
extension ProfileMediaViewController: UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return items.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "MediaCell", for: indexPath) as! MediaCollectionViewCell
        let item = items[indexPath.item]
        
        cell.configure(with: item.media, appSettings: appSettings ?? AppSettings()) { [weak self] in
            guard let self = self else { return }
            let allImages = self.items.map { $0.media }
            if !allImages.isEmpty {
                self.showPhotoBrowser(media: item.media, images: allImages, initialIndex: indexPath.item)
            }
        }
        
        return cell
    }
}

//   - UICollectionViewDelegate
extension ProfileMediaViewController: UICollectionViewDelegate {
    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        scrollCallback?(scrollView)
    }
}

//  - ZJFlexibleDataSource
extension ProfileMediaViewController: ZJFlexibleDataSource {
    func numberOfCols(at section: Int) -> Int {
        return 2
    }
    
    func sizeOfItemAtIndexPath(at indexPath: IndexPath) -> CGSize {
        let item = items[indexPath.item]
        let width = (UIScreen.main.bounds.width - spaceOfCells(at: 0) * 3) / 2
        let height: CGFloat
        
        switch onEnum(of: item.media) {
        case .image(let data):
            let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
            height = width / aspectRatio
        case .video(let data):
            let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
            height = width / aspectRatio
        case .gif(let data):
            let aspectRatio = CGFloat(data.width / (data.height == 0 ? 1 : data.height)).isZero ? 1 : CGFloat(data.width / data.height)
            height = width / aspectRatio
        case .audio:
            height = width
        case .video:
            height = width
        }
        
        return CGSize(width: width, height: height)
    }
    
    func spaceOfCells(at section: Int) -> CGFloat {
        return 4
    }
    
    func sectionInsets(at section: Int) -> UIEdgeInsets {
        return UIEdgeInsets(top: 4, left: 4, bottom: 4, right: 4)
    }
    
    func sizeOfHeader(at section: Int) -> CGSize {
        return .zero
    }
    
    func heightOfAdditionalContent(at indexPath: IndexPath) -> CGFloat {
        return 0
    }
}

// - JXPagingViewListViewDelegate
extension ProfileMediaViewController: JXPagingViewListViewDelegate {
    func listView() -> UIView {
        return view
    }
    
    func listScrollView() -> UIScrollView {
        return collectionView
    }
    
    func listViewDidScrollCallback(callback: @escaping (UIScrollView) -> ()) {
        self.scrollCallback = callback
    }
} 
