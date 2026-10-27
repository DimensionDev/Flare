#if os(iOS)
import UIKit
import Kingfisher
import KotlinSharedUI

open class NetworkImageView: AnimatedImageView {
    private var media: (any UiMedia)?
    private var identity: String?
    private var requestedSize: CGSize = .zero
    private var lastRequestFailed = false
    private var generation = 0
    private var completion: (@MainActor @Sendable (Result<RetrieveImageResult, KingfisherError>) -> Void)?

    public override var contentMode: UIView.ContentMode {
        didSet {
            guard oldValue != contentMode else { return }
            requestedSize = .zero
            setNeedsLayout()
        }
    }

    public override init(frame: CGRect = .zero) {
        super.init(frame: frame)
        registerForTraitChanges([UITraitDisplayScale.self]) { (view: NetworkImageView, _) in
            view.setNeedsLayout()
        }
    }
    public required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    public func setImage(media: (any UiMedia)?,
                         completionHandler: (@MainActor @Sendable (Result<RetrieveImageResult, KingfisherError>) -> Void)? = nil) {
        let identity = media.map { "\(String(describing: $0.proxy))|\($0.cacheKey)" }
        if self.identity != identity { cancelImageLoad() }
        if lastRequestFailed { requestedSize = .zero }
        self.media = media
        self.identity = identity
        completion = completionHandler
        setNeedsLayout()
    }

    public func cancelImageLoad() {
        generation += 1
        media = nil
        identity = nil
        completion = nil
        requestedSize = .zero
        lastRequestFailed = false
        cancelMediaLoad()
        image = nil
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        guard let media, bounds.width > 0, bounds.height > 0 else { return }
        let scale = max(traitCollection.displayScale, 1)
        let size = CGSize(width: ceil(bounds.width * scale), height: ceil(bounds.height * scale))
        guard size.width > requestedSize.width || size.height > requestedSize.height else { return }
        requestedSize = CGSize(width: max(size.width, requestedSize.width), height: max(size.height, requestedSize.height))
        generation += 1
        let currentGeneration = generation
        lastRequestFailed = false
        kf.cancelDownloadTask()
        let options: KingfisherOptionsInfo = [
            .processor(ImageDownsamplingProcessor(pixelSize: requestedSize, fillsBounds: contentMode != .scaleAspectFit)),
            .scaleFactor(scale), .cacheOriginalImage, .backgroundDecode, .asyncCacheTypeCheck,
            .keepCurrentImageWhileLoading, .transition(.fade(0.25)),
        ]
        setMedia(media, options: options) { [weak self] result in
            guard let self, self.generation == currentGeneration else { return }
            if case .failure = result { self.lastRequestFailed = true }
            self.completion?(result)
        }
    }
}
#endif
