#if os(iOS)
import UIKit
import Kingfisher

open class NetworkImageView: AnimatedImageView {
    private var url: URL?
    private var headers: [String: String]?
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

    public func setImage(with url: URL?, customHeaders: [String: String]? = nil,
                         completionHandler: (@MainActor @Sendable (Result<RetrieveImageResult, KingfisherError>) -> Void)? = nil) {
        if self.url != url || headers != customHeaders { cancelImageLoad() }
        if lastRequestFailed { requestedSize = .zero }
        self.url = url
        headers = customHeaders
        completion = completionHandler
        setNeedsLayout()
    }

    public func cancelImageLoad() {
        generation += 1
        url = nil
        headers = nil
        completion = nil
        requestedSize = .zero
        lastRequestFailed = false
        kf.cancelDownloadTask()
        image = nil
    }

    public override func layoutSubviews() {
        super.layoutSubviews()
        guard let url, bounds.width > 0, bounds.height > 0 else { return }
        let scale = max(traitCollection.displayScale, 1)
        let size = CGSize(width: ceil(bounds.width * scale), height: ceil(bounds.height * scale))
        guard size.width > requestedSize.width || size.height > requestedSize.height else { return }
        requestedSize = CGSize(width: max(size.width, requestedSize.width), height: max(size.height, requestedSize.height))
        generation += 1
        let currentGeneration = generation
        lastRequestFailed = false
        kf.cancelDownloadTask()
        var options: KingfisherOptionsInfo = [
            .processor(ImageDownsamplingProcessor(pixelSize: requestedSize, fillsBounds: contentMode != .scaleAspectFit)),
            .scaleFactor(scale), .cacheOriginalImage, .backgroundDecode, .asyncCacheTypeCheck,
            .keepCurrentImageWhileLoading, .transition(.fade(0.25)),
        ]
        if let headers, !headers.isEmpty {
            options.append(.requestModifier(AnyModifier { request in
                var request = request
                headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
                return request
            }))
        }
        kf.setImage(with: url, options: options) { [weak self] result in
            guard let self, self.generation == currentGeneration else { return }
            if case .failure = result { self.lastRequestFailed = true }
            self.completion?(result)
        }
    }
}
#endif
