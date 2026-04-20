import UIKit
import Kingfisher
import KotlinSharedUI
import SwiftUI

// MARK: - MediaUIView
// UIKit port of MediaView.swift for a single UiMedia item.
//
// * Image / GIF: pure UIKit via Kingfisher.
// * Video: a thumbnail with a "play" glyph. Full VideoPlayer-library parity
//   (autoplay on scroll-stop, network-type-gated autoplay, progress timer)
//   was intentionally not ported here because it depends on the SwiftUI-only
//   VideoPlayer Swift package; invoking the status-media viewer on tap is the
//   authoritative play path, matching the SwiftUI tap-to-expand behaviour.
final class MediaUIView: UIView {
    private let imageView: UIImageView = {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFill
        iv.clipsToBounds = true
        iv.translatesAutoresizingMaskIntoConstraints = false
        return iv
    }()
    private let background: UIView = {
        let v = UIView()
        v.backgroundColor = .systemGray
        v.translatesAutoresizingMaskIntoConstraints = false
        return v
    }()
    private let playBadge: UIImageView = {
        let iv = UIImageView(image: UIImage(named: "fa-circle-play"))
        iv.contentMode = .scaleAspectFit
        iv.tintColor = .white
        iv.translatesAutoresizingMaskIntoConstraints = false
        iv.isHidden = true
        return iv
    }()
    private let playBadgeBg: UIView = {
        let v = UIView()
        v.backgroundColor = .black
        v.layer.cornerRadius = 16
        v.translatesAutoresizingMaskIntoConstraints = false
        v.isHidden = true
        return v
    }()

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        addSubview(background)
        addSubview(imageView)
        addSubview(playBadgeBg)
        playBadgeBg.addSubview(playBadge)
        NSLayoutConstraint.activate([
            background.topAnchor.constraint(equalTo: topAnchor),
            background.leadingAnchor.constraint(equalTo: leadingAnchor),
            background.trailingAnchor.constraint(equalTo: trailingAnchor),
            background.bottomAnchor.constraint(equalTo: bottomAnchor),
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor),
            playBadgeBg.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            playBadgeBg.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16),
            playBadgeBg.widthAnchor.constraint(equalToConstant: 32),
            playBadgeBg.heightAnchor.constraint(equalToConstant: 32),
            playBadge.centerXAnchor.constraint(equalTo: playBadgeBg.centerXAnchor),
            playBadge.centerYAnchor.constraint(equalTo: playBadgeBg.centerYAnchor),
            playBadge.widthAnchor.constraint(equalToConstant: 16),
            playBadge.heightAnchor.constraint(equalToConstant: 16),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func set(media: UiMedia, cornerRadius: CGFloat) {
        layer.cornerRadius = cornerRadius
        imageView.kf.cancelDownloadTask()
        imageView.image = nil
        playBadge.isHidden = true
        playBadgeBg.isHidden = true

        switch onEnum(of: media) {
        case .image(let image):
            loadImage(url: image.previewUrl)
        case .video(let video):
            loadImage(url: video.thumbnailUrl)
            playBadge.isHidden = false
            playBadgeBg.isHidden = false
        case .gif(let gif):
            loadGif(url: gif.url)
        case .audio:
            imageView.image = nil
        }
    }

    private func loadImage(url: String?) {
        guard let u = url.flatMap(URL.init(string:)) else { return }
        imageView.kf.setImage(with: u, options: [.transition(.fade(0.25)), .cacheOriginalImage])
    }

    private func loadGif(url: String?) {
        guard let u = url.flatMap(URL.init(string:)) else { return }
        imageView.kf.setImage(with: u, options: [.transition(.fade(0.25))])
    }
}

// MARK: - StatusMediaUIView
// Mirrors StatusMediaView.swift: grid + sensitive blur overlay + alt-text
// buttons. Up to 3 columns; rows arranged so items fill available width.
final class StatusMediaUIView: UIView {
    var onMediaClicked: ((UiMedia, Int) -> Void)?

    private let grid = UIView()
    private let blurView = UIVisualEffectView(effect: UIBlurEffect(style: .regular))
    private let toggleButton = UIButton(type: .system)

    private var items: [UiMedia] = []
    private var sensitive: Bool = false
    private var cornerRadius: CGFloat = 16
    private var isBlurred: Bool = false
    private var singleFollowsImageAspect: Bool = true
    private var toggleButtonPositionConstraints: [NSLayoutConstraint] = []
    private var aspectConstraint: NSLayoutConstraint?
    private var lastLayoutWidth: CGFloat = 0
    private let spacing: CGFloat = 4

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true

        grid.translatesAutoresizingMaskIntoConstraints = false
        addSubview(grid)

        blurView.translatesAutoresizingMaskIntoConstraints = false
        blurView.isHidden = true
        addSubview(blurView)

        toggleButton.translatesAutoresizingMaskIntoConstraints = false
        toggleButton.addTarget(self, action: #selector(toggleBlur), for: .touchUpInside)
        addSubview(toggleButton)

        NSLayoutConstraint.activate([
            grid.topAnchor.constraint(equalTo: topAnchor),
            grid.leadingAnchor.constraint(equalTo: leadingAnchor),
            grid.trailingAnchor.constraint(equalTo: trailingAnchor),
            grid.bottomAnchor.constraint(equalTo: bottomAnchor),
            blurView.topAnchor.constraint(equalTo: topAnchor),
            blurView.leadingAnchor.constraint(equalTo: leadingAnchor),
            blurView.trailingAnchor.constraint(equalTo: trailingAnchor),
            blurView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = cornerRadius
        layoutGrid()
        if abs(bounds.width - lastLayoutWidth) > 0.5 {
            lastLayoutWidth = bounds.width
            invalidateIntrinsicContentSize()
        }
    }

    override var intrinsicContentSize: CGSize {
        let width = bounds.width > 0 ? bounds.width : UIView.noIntrinsicMetric
        guard width != UIView.noIntrinsicMetric else {
            return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
        }
        return CGSize(width: UIView.noIntrinsicMetric, height: ceil(gridHeight(for: width)))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width.isFinite && targetSize.width > 0
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : 0)
        guard width > 0 else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        return CGSize(
            width: horizontalFittingPriority == .required ? targetSize.width : UIView.noIntrinsicMetric,
            height: ceil(gridHeight(for: width))
        )
    }

    func configure(data: [UiMedia], sensitive: Bool, cornerRadius: CGFloat, singleFollowsImageAspect: Bool) {
        self.items = data
        self.sensitive = sensitive
        self.cornerRadius = cornerRadius
        self.singleFollowsImageAspect = singleFollowsImageAspect
        self.isBlurred = sensitive
        layer.cornerRadius = cornerRadius
        updateAspectConstraint()
        rebuildGrid()
        updateBlurUI()
    }

    private func rebuildGrid() {
        grid.subviews.forEach {
            $0.removeFromSuperview()
        }
        guard !items.isEmpty else { return }

        for (index, item) in items.enumerated() {
            grid.addSubview(makeCell(media: item, index: index))
        }
        setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    private func updateAspectConstraint() {
        aspectConstraint?.isActive = false
        aspectConstraint = nil
        guard !items.isEmpty else { return }

        let spec = heightSpec()
        let constraint = heightAnchor.constraint(equalTo: widthAnchor, multiplier: spec.multiplier, constant: spec.constant)
        constraint.priority = .init(999)
        constraint.isActive = true
        aspectConstraint = constraint
    }

    private func heightSpec() -> (multiplier: CGFloat, constant: CGFloat) {
        switch items.count {
        case 1:
            return (1 / singleAspectRatio(), 0)
        case 2, 3, 4:
            return (9 / 16, 0)
        default:
            let cols = 3
            let rows = Int(ceil(Double(items.count) / Double(cols)))
            let multiplier = CGFloat(rows) / CGFloat(cols)
            let constant = CGFloat(max(0, rows - 1)) * spacing
                - CGFloat(rows * (cols - 1)) * spacing / CGFloat(cols)
            return (multiplier, constant)
        }
    }

    private func layoutGrid() {
        let frames = gridFrames(for: grid.bounds.width)
        for (view, frame) in zip(grid.subviews, frames) {
            view.frame = frame
        }
    }

    private func gridHeight(for width: CGFloat) -> CGFloat {
        guard !items.isEmpty, width > 0 else { return 0 }
        switch items.count {
        case 1:
            return width / singleAspectRatio()
        case 2, 3, 4:
            return width * 9 / 16
        default:
            let cols = 3
            let rows = Int(ceil(Double(items.count) / Double(cols)))
            let cellWidth = (width - CGFloat(cols - 1) * spacing) / CGFloat(cols)
            return CGFloat(rows) * cellWidth + CGFloat(max(0, rows - 1)) * spacing
        }
    }

    private func gridFrames(for width: CGFloat) -> [CGRect] {
        guard !items.isEmpty, width > 0 else { return [] }
        let height = gridHeight(for: width)

        switch items.count {
        case 1:
            return [CGRect(x: 0, y: 0, width: width, height: height)]

        case 2:
            let cellWidth = (width - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: cellWidth, height: height),
                CGRect(x: cellWidth + spacing, y: 0, width: cellWidth, height: height),
            ]

        case 3:
            let halfWidth = (width - spacing) / 2
            let rightHeight = (height - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: halfWidth, height: height),
                CGRect(x: halfWidth + spacing, y: 0, width: halfWidth, height: rightHeight),
                CGRect(x: halfWidth + spacing, y: rightHeight + spacing, width: halfWidth, height: rightHeight),
            ]

        case 4:
            let cellWidth = (width - spacing) / 2
            let cellHeight = (height - spacing) / 2
            return [
                CGRect(x: 0, y: 0, width: cellWidth, height: cellHeight),
                CGRect(x: cellWidth + spacing, y: 0, width: cellWidth, height: cellHeight),
                CGRect(x: 0, y: cellHeight + spacing, width: cellWidth, height: cellHeight),
                CGRect(x: cellWidth + spacing, y: cellHeight + spacing, width: cellWidth, height: cellHeight),
            ]

        default:
            return multiRowGridFrames(width: width)
        }
    }

    private func multiRowGridFrames(width: CGFloat) -> [CGRect] {
        let cols = 3
        let cellWidth = (width - CGFloat(cols - 1) * spacing) / CGFloat(cols)
        let fullRows = items.count / cols
        let remainder = items.count % cols
        var frames: [CGRect] = []
        frames.reserveCapacity(items.count)
        var y: CGFloat = 0

        for row in 0..<fullRows {
            for col in 0..<cols {
                frames.append(CGRect(
                    x: CGFloat(col) * (cellWidth + spacing),
                    y: y,
                    width: cellWidth,
                    height: cellWidth
                ))
            }
            y += cellWidth
            if row < fullRows - 1 || remainder > 0 {
                y += spacing
            }
        }

        if remainder > 0 {
            let tailWidth = (width - CGFloat(remainder - 1) * spacing) / CGFloat(remainder)
            for col in 0..<remainder {
                frames.append(CGRect(
                    x: CGFloat(col) * (tailWidth + spacing),
                    y: y,
                    width: tailWidth,
                    height: cellWidth
                ))
            }
        }

        return frames
    }

    private func singleAspectRatio() -> CGFloat {
        guard singleFollowsImageAspect else {
            return 16.0 / 9.0
        }
        if let ratio = items.first?.aspectRatio, ratio > 0 {
            return max(9.0 / 21.0, CGFloat(ratio))
        }
        return 1
    }

    private func makeCell(media: UiMedia, index: Int) -> UIView {
        let container = UIView()
        let mv = MediaUIView()
        mv.set(media: media, cornerRadius: 0)
        mv.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(mv)

        NSLayoutConstraint.activate([
            mv.topAnchor.constraint(equalTo: container.topAnchor),
            mv.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            mv.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            mv.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        ])

        let tap = UITapGestureRecognizer(target: self, action: #selector(onCellTapped(_:)))
        container.tag = index
        container.isUserInteractionEnabled = true
        container.addGestureRecognizer(tap)

        // ALT overlay
        if let alt = media.description_, !alt.isEmpty {
            let alt = AltTextButton(text: alt)
            alt.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(alt)
            NSLayoutConstraint.activate([
                alt.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -8),
                alt.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -8),
            ])
        }
        return container
    }

    @objc private func onCellTapped(_ gesture: UITapGestureRecognizer) {
        guard let view = gesture.view else { return }
        let index = view.tag
        if sensitive, isBlurred { return }
        guard items.indices.contains(index) else { return }
        onMediaClicked?(items[index], index)
    }

    private func updateBlurUI() {
        blurView.isHidden = !(sensitive && isBlurred)
        NSLayoutConstraint.deactivate(toggleButtonPositionConstraints)
        toggleButtonPositionConstraints = []
        if !sensitive {
            toggleButton.isHidden = true
            return
        }
        toggleButton.isHidden = false
        var cfg = UIButton.Configuration.bordered()
        if isBlurred {
            cfg.title = String(localized: "sensitive_button_show")
            cfg.image = UIImage(named: "fa-eye")
            cfg.imagePlacement = .leading
            cfg.baseForegroundColor = .white
            toggleButton.configuration = cfg
            // Center over blurred content.
            toggleButtonPositionConstraints = [
                toggleButton.centerXAnchor.constraint(equalTo: centerXAnchor),
                toggleButton.centerYAnchor.constraint(equalTo: centerYAnchor),
            ]
        } else {
            cfg.title = nil
            cfg.image = UIImage(named: "fa-eye-slash")
            toggleButton.configuration = cfg
            toggleButtonPositionConstraints = [
                toggleButton.topAnchor.constraint(equalTo: topAnchor, constant: 12),
                toggleButton.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            ]
        }
        NSLayoutConstraint.activate(toggleButtonPositionConstraints)
    }

    @objc private func toggleBlur() {
        UIView.animate(withDuration: 0.2) { [weak self] in
            guard let self = self else { return }
            self.isBlurred.toggle()
            self.updateBlurUI()
            self.layoutIfNeeded()
        }
    }
}

// Tiny popover-on-tap button for the `description_` / alt text.
private final class AltTextButton: UIButton {
    private let altText: String
    init(text: String) {
        self.altText = text
        super.init(frame: .zero)
        var cfg = UIButton.Configuration.bordered()
        cfg.title = "ALT"
        cfg.cornerStyle = .medium
        configuration = cfg
        addTarget(self, action: #selector(showAlt), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    @objc private func showAlt() {
        guard let parent = findParentViewController() else { return }
        let alert = UIAlertController(title: nil, message: altText, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: String(localized: "OK"), style: .default))
        parent.present(alert, animated: true)
    }
    private func findParentViewController() -> UIViewController? {
        var r: UIResponder? = self
        while let n = r { if let vc = n as? UIViewController { return vc }; r = n.next }
        return nil
    }
}

// MARK: - StatusMediaContentUIView
// Mirrors StatusMediaContent in StatusView.swift. When `showMedia` (appearance)
// is off, renders a show-media button; after tapping, shows the grid.
final class StatusMediaContentUIView: UIView {
    var onMediaClicked: ((UiMedia, Int) -> Void)?

    private let grid = StatusMediaUIView()
    private let showButton = UIButton(type: .system)
    private var items: [UiMedia] = []
    private var sensitive: Bool = false
    private var cornerRadius: CGFloat = 16
    private var expanded: Bool = false
    private var appearanceShowMedia: Bool = true
    private var appearanceShowSensitive: Bool = false
    private var appearanceExpandMediaSize: Bool = true
    private var showButtonConstraints: [NSLayoutConstraint] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        grid.translatesAutoresizingMaskIntoConstraints = false
        showButton.translatesAutoresizingMaskIntoConstraints = false

        var cfg = UIButton.Configuration.bordered()
        cfg.title = String(localized: "show_media_button")
        cfg.image = UIImage(named: "fa-image")
        cfg.imagePlacement = .leading
        cfg.imagePadding = 4
        showButton.configuration = cfg
        showButton.addTarget(self, action: #selector(onShowTapped), for: .touchUpInside)

        addSubview(grid)
        addSubview(showButton)
        NSLayoutConstraint.activate([
            grid.topAnchor.constraint(equalTo: topAnchor),
            grid.leadingAnchor.constraint(equalTo: leadingAnchor),
            grid.trailingAnchor.constraint(equalTo: trailingAnchor),
            grid.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        showButtonConstraints = [
            showButton.topAnchor.constraint(equalTo: topAnchor),
            showButton.leadingAnchor.constraint(equalTo: leadingAnchor),
            showButton.bottomAnchor.constraint(equalTo: bottomAnchor),
        ]
        NSLayoutConstraint.activate(showButtonConstraints)
        grid.onMediaClicked = { [weak self] media, index in
            self?.onMediaClicked?(media, index)
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override var intrinsicContentSize: CGSize {
        if !grid.isHidden {
            guard bounds.width > 0 else {
                return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
            }
            let size = grid.systemLayoutSizeFitting(
                CGSize(width: bounds.width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            )
            return CGSize(width: UIView.noIntrinsicMetric, height: ceil(size.height))
        }
        let size = showButton.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        return CGSize(width: UIView.noIntrinsicMetric, height: ceil(size.height))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        guard grid.isHidden else {
            let width = targetSize.width.isFinite && targetSize.width > 0
                ? targetSize.width
                : (bounds.width > 0 ? bounds.width : 0)
            let size = grid.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: width > 0 ? .required : .fittingSizeLevel,
                verticalFittingPriority: verticalFittingPriority
            )
            return CGSize(width: targetSize.width, height: ceil(size.height))
        }
        let size = showButton.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        return CGSize(width: targetSize.width, height: ceil(size.height))
    }

    func configure(
        data: [UiMedia],
        sensitive: Bool,
        cornerRadius: CGFloat,
        appearanceShowMedia: Bool,
        appearanceShowSensitive: Bool,
        appearanceExpandMediaSize: Bool
    ) {
        self.items = data
        self.sensitive = sensitive
        self.cornerRadius = cornerRadius
        self.appearanceShowMedia = appearanceShowMedia
        self.appearanceShowSensitive = appearanceShowSensitive
        self.appearanceExpandMediaSize = appearanceExpandMediaSize
        self.expanded = false
        applyVisibility()
        invalidateIntrinsicContentSize()
    }

    private func applyVisibility() {
        if appearanceShowMedia || expanded {
            grid.isHidden = false
            showButton.isHidden = true
            NSLayoutConstraint.deactivate(showButtonConstraints)
            grid.configure(
                data: items,
                sensitive: !appearanceShowSensitive && sensitive,
                cornerRadius: cornerRadius,
                singleFollowsImageAspect: appearanceExpandMediaSize
            )
        } else {
            grid.isHidden = true
            showButton.isHidden = false
            NSLayoutConstraint.activate(showButtonConstraints)
            grid.configure(
                data: [],
                sensitive: false,
                cornerRadius: cornerRadius,
                singleFollowsImageAspect: appearanceExpandMediaSize
            )
        }
        setNeedsLayout()
        superview?.setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    @objc private func onShowTapped() {
        expanded = true
        applyVisibility()
        invalidateContainingCollectionLayout()
        UIView.animate(withDuration: 0.2) { [weak self] in
            guard let self = self else { return }
            self.superview?.layoutIfNeeded()
        } completion: { _ in
            self.invalidateContainingCollectionLayout()
        }
    }

    private func invalidateContainingCollectionLayout() {
        invalidateIntrinsicContentSize()
        var responder: UIResponder? = self
        var cellRef: UICollectionViewCell?
        while let current = responder {
            if cellRef == nil, let cell = current as? UICollectionViewCell {
                cellRef = cell
            }
            if let collectionView = current as? UICollectionView {
                if let cell = cellRef, let indexPath = collectionView.indexPath(for: cell) {
                    let context = UICollectionViewLayoutInvalidationContext()
                    context.invalidateItems(at: [indexPath])
                    collectionView.collectionViewLayout.invalidateLayout(with: context)
                }
                collectionView.performBatchUpdates(nil)
                return
            }
            responder = current.next
        }
    }
}
