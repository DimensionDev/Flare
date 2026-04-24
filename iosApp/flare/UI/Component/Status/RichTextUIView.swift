import UIKit
import Kingfisher
import KotlinSharedUI
import SwiftUI

/// Pure-UIKit renderer for the iOS `RichText` SwiftUI view.
final class RichTextUIView: UIView, TimelineHeightProviding {
    // MARK: - Public inputs

    var text: UiRichText? { didSet { if !isBatchUpdating { update() } } }
    /// Optional caller-provided stable key for `text`. When set, the structural
    /// signature compares by key instead of deep-walking Kotlin bridged values.
    /// Callers must guarantee: same key ⇒ same text.
    var contentKey: Int? { didSet { if !isBatchUpdating { update() } } }
    var lineLimit: Int? = nil {
        didSet {
            guard !isBatchUpdating, oldValue != lineLimit else { return }
            updateHorizontalLayoutPolicy()
            updateTextViews()
        }
    }
    var isTextSelectionEnabled: Bool = false {
        didSet {
            if !isBatchUpdating, oldValue != isTextSelectionEnabled {
                update()
            }
        }
    }
    var fixedVertical: Bool = true {
        didSet {
            guard !isBatchUpdating, oldValue != fixedVertical else { return }
            invalidateIntrinsicContentSize()
        }
    }
    var onOpenURL: ((URL) -> Void)? { didSet { if !isBatchUpdating { updateTextViews() } } }
    var baseTextStyle: UIFont.TextStyle = .body { didSet { if !isBatchUpdating { update() } } }
    var baseTextColor: UIColor = .label { didSet { if !isBatchUpdating { update() } } }

    // MARK: - Private state

    private let stack: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.alignment = .fill
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()

    private var inlineImages: [String: UIImage] = [:]
    private var textBlocks: [RenderedTextBlock] = []
    private var measurementBlocks: [RichTextMeasurementBlock] = []
    private var renderGeneration = 0
    private var lastLayoutWidth: CGFloat = 0
    private var isBatchUpdating = false
    private var lastStructuralSignature: StructuralSignature?
    private var traitRegistration: UITraitChangeRegistration?

    private struct StructuralSignature: Equatable {
        let contentKey: Int?
        let text: UiRichText?
        let isTextSelectionEnabled: Bool
        let baseTextStyle: UIFont.TextStyle
        let baseTextColor: UIColor

        static func == (lhs: StructuralSignature, rhs: StructuralSignature) -> Bool {
            guard lhs.isTextSelectionEnabled == rhs.isTextSelectionEnabled,
                  lhs.baseTextStyle == rhs.baseTextStyle,
                  lhs.baseTextColor.isEqual(rhs.baseTextColor) else {
                return false
            }
            if let l = lhs.contentKey, let r = rhs.contentKey {
                return l == r
            }
            return Self.textsAreEqual(lhs.text, rhs.text)
        }

        private static func textsAreEqual(_ lhs: UiRichText?, _ rhs: UiRichText?) -> Bool {
            switch (lhs, rhs) {
            case (nil, nil):
                return true
            case let (l?, r?):
                if (l as AnyObject) === (r as AnyObject) { return true }
                return (l as AnyObject).isEqual(r)
            default:
                return false
            }
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    private func commonInit() {
        addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
        setContentHuggingPriority(.required, for: .vertical)
        setContentCompressionResistancePriority(.required, for: .vertical)
        updateHorizontalLayoutPolicy()
        traitRegistration = registerForTraitChanges([UITraitUserInterfaceStyle.self]) { (view: RichTextUIView, _) in
            view.updateTextViews()
        }
    }

    // MARK: - Rendering

    func configure(
        text: UiRichText?,
        lineLimit: Int?,
        isTextSelectionEnabled: Bool,
        onOpenURL: ((URL) -> Void)?,
        baseTextStyle: UIFont.TextStyle = .body,
        baseTextColor: UIColor = .label,
        contentKey: Int? = nil
    ) {
        let oldLineLimit = self.lineLimit
        isBatchUpdating = true
        self.text = text
        self.contentKey = contentKey
        self.lineLimit = lineLimit
        self.isTextSelectionEnabled = isTextSelectionEnabled
        self.onOpenURL = onOpenURL
        self.baseTextStyle = baseTextStyle
        self.baseTextColor = baseTextColor
        isBatchUpdating = false

        if oldLineLimit != lineLimit {
            updateHorizontalLayoutPolicy()
        }
        update()
    }

    private func update(force: Bool = false) {
        let structuralSignature = StructuralSignature(
            contentKey: contentKey,
            text: text,
            isTextSelectionEnabled: isTextSelectionEnabled,
            baseTextStyle: baseTextStyle,
            baseTextColor: baseTextColor
        )
        guard force || lastStructuralSignature != structuralSignature else {
            updateTextViews()
            return
        }
        lastStructuralSignature = structuralSignature
        renderGeneration += 1
        clearStack()

        guard let text else {
            invalidateIntrinsicContentSize()
            return
        }

        inlineImages = inlineImages.filter { text.imageUrls.contains($0.key) }
        let generation = renderGeneration
        for content in text.renderRuns {
            switch content {
            case let textContent as RenderContent.Text:
                addTextContent(textContent)
            case let imageContent as RenderContent.BlockImage:
                addBlockImage(url: imageContent.url, href: imageContent.href)
            default:
                continue
            }
        }

        loadInlineImages(for: text, generation: generation)
        invalidateIntrinsicContentSize()
    }

    private func clearStack() {
        textBlocks = []
        measurementBlocks = []
        lastLayoutWidth = 0
        stack.arrangedSubviews.forEach {
            stack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > 0 else { return }
        let widthKey = Self.measurementWidthKey(width)
        guard widthKey != Self.measurementWidthKey(lastLayoutWidth) else { return }
        lastLayoutWidth = width
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        stack.bounds = CGRect(x: 0, y: 0, width: width, height: stack.bounds.height)
        for subview in stack.arrangedSubviews {
            prepareSubview(subview, forFittingWidth: width)
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func prepareSubview(_ view: UIView, forFittingWidth width: CGFloat) {
        if let fittingView = view as? RichTextFittingPreparing {
            fittingView.prepareForFitting(width: width)
            return
        }
        guard Self.measurementWidthKey(view.bounds.width) != Self.measurementWidthKey(width) else { return }
        view.bounds = CGRect(x: view.bounds.minX, y: view.bounds.minY, width: width, height: view.bounds.height)
        for subview in view.subviews {
            prepareSubview(subview, forFittingWidth: width)
        }
        view.invalidateIntrinsicContentSize()
        view.setNeedsLayout()
    }

//    override var intrinsicContentSize: CGSize {
//        let exposesWidth = exposesHorizontalIntrinsicSize
//        let size = measuredStackSize(for: bounds.width, exposesWidth: exposesWidth)
//        return CGSize(
//            width: exposesWidth ? ceil(size.width) : UIView.noIntrinsicMetric,
//            height: fixedVertical ? ceil(size.height) : UIView.noIntrinsicMetric
//        )
//    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let exposesWidth = exposesHorizontalIntrinsicSize && horizontalFittingPriority != .required
        if horizontalFittingPriority == .required,
           targetSize.width.isFinite,
           targetSize.width > 0 {
            prepareForFitting(width: targetSize.width)
        }
        let size = measuredStackSize(for: targetSize.width, exposesWidth: exposesWidth)
        return CGSize(
            width: horizontalFittingPriority == .required ? targetSize.width : (exposesWidth ? ceil(size.width) : UIView.noIntrinsicMetric),
            height: fixedVertical ? ceil(size.height) : UIView.noIntrinsicMetric
        )
    }

    private func measuredStackSize(for proposedWidth: CGFloat, exposesWidth: Bool) -> CGSize {
        if exposesWidth {
            return measuredSingleLineStackSize()
        }

        let width = proposedWidth.isFinite && proposedWidth > 0
            ? proposedWidth
            : (bounds.width > 0 ? bounds.width : UIScreen.main.bounds.width)
        guard width > 0 else { return .zero }

        var height: CGFloat = 0
        var measuredWidth: CGFloat = 0
        for (index, block) in measurementBlocks.enumerated() {
            let size = block.measuredSize(width: width, lineLimit: lineLimit)
            if index > 0 {
                height += stack.spacing
            }
            height += ceil(size.height)
            measuredWidth = max(measuredWidth, size.width)
        }
        return CGSize(width: measuredWidth, height: height)
    }

    func singleLineContentSize() -> CGSize {
        measuredSingleLineStackSize()
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width.isFinite, width > 0 else { return nil }
        return ceil(measuredStackSize(for: width, exposesWidth: false).height)
    }

    private func measuredSingleLineStackSize() -> CGSize {
        var height: CGFloat = 0
        var measuredWidth: CGFloat = 0
        for (index, block) in measurementBlocks.enumerated() {
            let size = block.singleLineSize(lineLimit: lineLimit)
            if index > 0 {
                height += stack.spacing
            }
            height += ceil(size.height)
            measuredWidth = max(measuredWidth, size.width)
        }
        return CGSize(width: measuredWidth, height: height)
    }

//    override func layoutSubviews() {
//        super.layoutSubviews()
//        if abs(bounds.width - lastLayoutWidth) > 0.5 {
//            lastLayoutWidth = bounds.width
//            invalidateIntrinsicContentSize()
//        }
//    }

    override var forFirstBaselineLayout: UIView {
        firstBaselineCandidate(in: stack) ?? super.forFirstBaselineLayout
    }

    override var forLastBaselineLayout: UIView {
        lastBaselineCandidate(in: stack) ?? super.forLastBaselineLayout
    }

    private var exposesHorizontalIntrinsicSize: Bool {
        lineLimit == 1
    }

    fileprivate static let singleLineMeasurementWidth: CGFloat = 10_000

    fileprivate static func measurementWidthKey(_ width: CGFloat) -> Int {
        guard width.isFinite, width > 0 else { return 0 }
        return Int((width * UIScreen.main.scale).rounded(.toNearestOrAwayFromZero))
    }

    private func updateHorizontalLayoutPolicy() {
        setContentHuggingPriority(exposesHorizontalIntrinsicSize ? .required : .defaultLow, for: .horizontal)
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        invalidateIntrinsicContentSize()
    }

    private func firstBaselineCandidate(in view: UIView) -> UIView? {
        if view is UILabel || view is UITextView {
            return view
        }
        for subview in view.subviews {
            if let candidate = firstBaselineCandidate(in: subview) {
                return candidate
            }
        }
        return nil
    }

    private func lastBaselineCandidate(in view: UIView) -> UIView? {
        if view is UILabel || view is UITextView {
            return view
        }
        for subview in view.subviews.reversed() {
            if let candidate = lastBaselineCandidate(in: subview) {
                return candidate
            }
        }
        return nil
    }

    private func addTextContent(_ content: RenderContent.Text) {
        let attributedText = attributedString(for: content)
        let renderer = makeTextRenderer(
            content: content,
            attributedText: attributedText
        )
        let measurement = RichTextTextMeasurement(
            attributedText: attributedText,
            fallbackTextStyle: baseTextStyle
        )
        textBlocks.append(RenderedTextBlock(content: content, renderer: renderer, measurement: measurement))

        if content.block.isBlockQuote {
            stack.addArrangedSubview(RichTextQuoteBlockView(contentView: renderer.renderedView))
            measurementBlocks.append(.quote(measurement))
        } else {
            stack.addArrangedSubview(renderer.renderedView)
            measurementBlocks.append(.text(measurement))
        }
    }

    private func addBlockImage(url: String, href: String?) {
        guard URL(string: url) != nil else { return }
        let imageView = RichTextBlockImageView(url: url, href: href)
        let measurement = RichTextBlockImageMeasurement()
        imageView.onOpenURL = onOpenURL
        imageView.onAspectRatioChanged = { [weak self, measurement] ratio in
            measurement.update(aspectRatio: ratio)
            self?.lastLayoutWidth = 0
            self?.invalidateIntrinsicContentSize()
            self?.setNeedsLayout()
        }
        stack.addArrangedSubview(imageView)
        measurementBlocks.append(.blockImage(measurement))
    }

    private func attributedString(for content: RenderContent.Text) -> NSAttributedString {
        let result = NSMutableAttributedString()
        for run in content.runs {
            switch run {
            case let textRun as RenderRun.Text:
                result.append(
                    NSAttributedString(
                        string: textRun.text,
                        attributes: attributes(for: textRun.style, block: content.block)
                    )
                )
            case let imageRun as RenderRun.Image:
                result.append(attributedImageRun(imageRun))
            default:
                continue
            }
        }
        return result
    }

    private func attributedImageRun(_ run: RenderRun.Image) -> NSAttributedString {
        guard let image = inlineImages[run.url] else {
            return NSAttributedString(
                string: run.alt.isEmpty ? run.url : run.alt,
                attributes: baseAttributes()
            )
        }

        let targetHeight = UIFontMetrics(forTextStyle: .body).scaledValue(for: 17)
        let ratio = image.size.width / max(image.size.height, 1)
        let attachment = NSTextAttachment()
        attachment.image = image
        attachment.bounds = CGRect(x: 0, y: -3, width: targetHeight * ratio, height: targetHeight)
        return NSAttributedString(attachment: attachment)
    }

    private func loadInlineImages(for text: UiRichText, generation: Int) {
        for urlString in text.imageUrls where inlineImages[urlString] == nil {
            guard let url = URL(string: urlString) else { continue }
            KingfisherManager.shared.retrieveImage(with: url) { [weak self] result in
                Task { @MainActor [weak self] in
                    guard let self, self.renderGeneration == generation else { return }
                    if case .success(let value) = result {
                        self.inlineImages[urlString] = value.image
                        self.refreshTextBlocks()
                    }
                }
            }
        }
    }

    private func refreshTextBlocks() {
        for block in textBlocks {
            let attributedText = attributedString(for: block.content)
            block.renderer.applyAttributedText(attributedText)
            block.measurement.update(attributedText: attributedText)
        }
        lastLayoutWidth = 0
        invalidateIntrinsicContentSize()
    }

    // MARK: - Text styles

    private func attributes(for style: RenderTextStyle, block: RenderBlockStyle) -> [NSAttributedString.Key: Any] {
        var attributes = baseAttributes(font: font(for: style, block: block), color: color(for: style, block: block))

        if let link = style.link, let url = URL(string: link) {
            attributes[.link] = url
            attributes[.foregroundColor] = linkColor()
        }
        if style.strikethrough {
            attributes[.strikethroughStyle] = NSUnderlineStyle.single.rawValue
        }
        if style.underline {
            attributes[.underlineStyle] = NSUnderlineStyle.single.rawValue
        }
        if style.time {
            attributes[.foregroundColor] = UIColor.secondaryLabel
            attributes[.backgroundColor] = UIColor.secondaryLabel.withAlphaComponent(0.08)
        }

        return attributes
    }

    private func baseAttributes(font: UIFont? = nil, color: UIColor? = nil) -> [NSAttributedString.Key: Any] {
        [
            .font: font ?? UIFont.preferredFont(forTextStyle: baseTextStyle),
            .foregroundColor: color ?? baseTextColor,
        ]
    }

    private func color(for style: RenderTextStyle, block: RenderBlockStyle) -> UIColor {
        if block.isBlockQuote || style.small {
            return baseTextColor.withAlphaComponent(0.7)
        }
        return baseTextColor
    }

    private func font(for style: RenderTextStyle, block: RenderBlockStyle) -> UIFont {
        if let headingLevel = block.headingLevel {
            switch headingLevel.intValue {
            case 1:
                return .preferredFont(forTextStyle: .title1)
            case 2:
                return .preferredFont(forTextStyle: .title2)
            case 3:
                return .preferredFont(forTextStyle: .title3)
            case 4:
                return .preferredFont(forTextStyle: .headline)
            case 5:
                return .preferredFont(forTextStyle: .subheadline)
            default:
                return .preferredFont(forTextStyle: .body)
            }
        }

        let textStyle = baseTextStyle
        let baseSize = UIFont.preferredFont(forTextStyle: textStyle).pointSize
        let baseFont: UIFont
        if style.code || style.monospace {
            baseFont = UIFont.monospacedSystemFont(
                ofSize: style.small ? baseSize * 0.8 : baseSize,
                weight: style.bold ? .bold : .regular
            )
        } else if style.small {
            baseFont = UIFont.systemFont(ofSize: baseSize * 0.8)
        } else {
            baseFont = UIFont.preferredFont(forTextStyle: textStyle)
        }

        var traits: UIFontDescriptor.SymbolicTraits = []
        if style.bold {
            traits.insert(.traitBold)
        }
        if style.italic || block.isBlockQuote || block.isFigCaption {
            traits.insert(.traitItalic)
        }
        guard !traits.isEmpty,
              let descriptor = baseFont.fontDescriptor.withSymbolicTraits(traits) else {
            return baseFont
        }
        return UIFont(descriptor: descriptor, size: baseFont.pointSize)
    }

    private func linkColor() -> UIColor {
        traitCollection.userInterfaceStyle == .dark
            ? UIColor(red: 0.60, green: 0.76, blue: 1.00, alpha: 1.00)
            : UIColor(red: 0.00, green: 0.40, blue: 0.80, alpha: 1.00)
    }

    private func textAlignment(for block: RenderBlockStyle) -> NSTextAlignment {
        switch block.textAlignment {
        case .center:
            return .center
        default:
            return .natural
        }
    }

    // MARK: - Text view configuration

    private func makeTextRenderer(content: RenderContent.Text, attributedText: NSAttributedString) -> RichTextTextRendering {
//        if isTextSelectionEnabled || hasLinks(in: content) {
//            return makeTextView(
//                attributedText: attributedText,
//                alignment: textAlignment(for: content.block)
//            )
//        }
//        return makeLabel(
//            attributedText: attributedText,
//            alignment: textAlignment(for: content.block)
//        )
        return makeTextView(
            attributedText: attributedText,
            alignment: textAlignment(for: content.block)
        )
    }

    private func makeTextView(attributedText: NSAttributedString, alignment: NSTextAlignment) -> RichTextTextView {
        let textView = RichTextTextView()
        textView.configure(
            attributedText: attributedText,
            alignment: alignment,
            lineLimit: lineLimit,
            selectionEnabled: isTextSelectionEnabled,
            linkColor: linkColor(),
            onOpenURL: onOpenURL
        )
        return textView
    }

    private func makeLabel(attributedText: NSAttributedString, alignment: NSTextAlignment) -> RichTextLabel {
        let label = RichTextLabel()
        label.configure(
            attributedText: attributedText,
            alignment: alignment,
            lineLimit: lineLimit
        )
        return label
    }

    private func hasLinks(in content: RenderContent.Text) -> Bool {
        content.runs.contains { run in
            guard let textRun = run as? RenderRun.Text,
                  let link = textRun.style.link else {
                return false
            }
            return URL(string: link) != nil
        }
    }

    private func updateTextViews() {
        for view in stack.arrangedSubviews {
            updateTextViews(in: view)
            if let imageView = view as? RichTextBlockImageView {
                imageView.onOpenURL = onOpenURL
            }
        }
        lastLayoutWidth = 0
        invalidateIntrinsicContentSize()
    }

    private func updateTextViews(in view: UIView) {
        if let renderer = view as? RichTextTextRendering {
            renderer.update(
                lineLimit: lineLimit,
                selectionEnabled: isTextSelectionEnabled,
                linkColor: linkColor(),
                onOpenURL: onOpenURL
            )
        }
        view.subviews.forEach { updateTextViews(in: $0) }
    }
}

private struct RenderedTextBlock {
    let content: RenderContent.Text
    let renderer: RichTextTextRendering
    let measurement: RichTextTextMeasurement
}

private enum RichTextMeasurementBlock {
    case text(RichTextTextMeasurement)
    case quote(RichTextTextMeasurement)
    case blockImage(RichTextBlockImageMeasurement)

    func measuredSize(width: CGFloat, lineLimit: Int?) -> CGSize {
        switch self {
        case .text(let text):
            return text.measuredSize(width: width, lineLimit: lineLimit)
        case .quote(let text):
            let contentWidth = max(width - RichTextQuoteBlockView.horizontalInset, 1)
            let size = text.measuredSize(width: contentWidth, lineLimit: lineLimit)
            return CGSize(width: width, height: ceil(size.height + RichTextQuoteBlockView.verticalInset * 2))
        case .blockImage(let image):
            return image.measuredSize(width: width)
        }
    }

    func singleLineSize(lineLimit: Int?) -> CGSize {
        switch self {
        case .text(let text):
            return text.singleLineSize(lineLimit: lineLimit)
        case .quote(let text):
            let size = text.singleLineSize(lineLimit: lineLimit)
            return CGSize(
                width: ceil(size.width + RichTextQuoteBlockView.horizontalInset),
                height: ceil(size.height + RichTextQuoteBlockView.verticalInset * 2)
            )
        case .blockImage(let image):
            return image.measuredSize(width: RichTextUIView.singleLineMeasurementWidth)
        }
    }
}

private final class RichTextTextMeasurement {
    private var attributedText: NSAttributedString
    private let fallbackTextStyle: UIFont.TextStyle
    private var measuredSizeCache: [MeasurementKey: CGSize] = [:]

    init(attributedText: NSAttributedString, fallbackTextStyle: UIFont.TextStyle) {
        self.attributedText = attributedText
        self.fallbackTextStyle = fallbackTextStyle
    }

    func update(attributedText: NSAttributedString) {
        self.attributedText = attributedText
        measuredSizeCache.removeAll(keepingCapacity: true)
    }

    func measuredSize(width: CGFloat, lineLimit: Int?) -> CGSize {
        guard width.isFinite, width > 0 else { return .zero }
        let key = MeasurementKey(widthKey: RichTextUIView.measurementWidthKey(width), lineLimit: lineLimit ?? -1)
        if let cached = measuredSizeCache[key] {
            return cached
        }

        let size = measure(width: width, lineLimit: lineLimit)
        measuredSizeCache[key] = size
        return size
    }

    func singleLineSize(lineLimit: Int?) -> CGSize {
        measuredSize(width: RichTextUIView.singleLineMeasurementWidth, lineLimit: lineLimit ?? 1)
    }

    private func measure(width: CGFloat, lineLimit: Int?) -> CGSize {
        guard attributedText.length > 0 else { return .zero }
        let boundingSize = CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)
        let rect = attributedText.boundingRect(
            with: boundingSize,
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            context: nil
        )
        let lineHeight = maxLineHeight()
        let maxHeight = lineLimit.map { CGFloat(max($0, 1)) * lineHeight } ?? CGFloat.greatestFiniteMagnitude
        let height = min(ceil(max(rect.height, lineHeight)), ceil(maxHeight))
        return CGSize(width: min(ceil(max(rect.width, 0)), width), height: height)
    }

    private func maxLineHeight() -> CGFloat {
        var lineHeight = UIFont.preferredFont(forTextStyle: fallbackTextStyle).lineHeight
        attributedText.enumerateAttribute(.font, in: NSRange(location: 0, length: attributedText.length)) { value, _, _ in
            if let font = value as? UIFont {
                lineHeight = max(lineHeight, font.lineHeight)
            }
        }
        return lineHeight
    }

    private struct MeasurementKey: Hashable {
        let widthKey: Int
        let lineLimit: Int
    }
}

private final class RichTextBlockImageMeasurement {
    private var aspectRatio: CGFloat = 9.0 / 16.0

    func update(aspectRatio: CGFloat) {
        guard aspectRatio.isFinite, aspectRatio > 0 else { return }
        self.aspectRatio = aspectRatio
    }

    func measuredSize(width: CGFloat) -> CGSize {
        guard width.isFinite, width > 0 else { return .zero }
        return CGSize(width: width, height: ceil(width * aspectRatio))
    }
}

private protocol RichTextTextRendering: AnyObject {
    var renderedView: UIView { get }
    func applyAttributedText(_ attributedText: NSAttributedString)
    func update(lineLimit: Int?, selectionEnabled: Bool, linkColor: UIColor, onOpenURL: ((URL) -> Void)?)
}

private protocol RichTextFittingPreparing: AnyObject {
    func prepareForFitting(width: CGFloat)
}

private final class RichTextLabel: UILabel, RichTextTextRendering, RichTextFittingPreparing {
    private var lastLayoutWidth: CGFloat = 0
    var renderedView: UIView { self }

    init() {
        super.init(frame: .zero)
        backgroundColor = .clear
        adjustsFontForContentSizeCategory = true
        setContentHuggingPriority(.required, for: .vertical)
        setContentCompressionResistancePriority(.required, for: .vertical)
        setContentHuggingPriority(.defaultLow, for: .horizontal)
        setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(attributedText: NSAttributedString, alignment: NSTextAlignment, lineLimit: Int?) {
        self.attributedText = attributedText
        textAlignment = alignment
        update(lineLimit: lineLimit, selectionEnabled: false, linkColor: .label, onOpenURL: nil)
    }

    func applyAttributedText(_ attributedText: NSAttributedString) {
        self.attributedText = attributedText
        lastLayoutWidth = 0
        setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    func update(lineLimit: Int?, selectionEnabled: Bool, linkColor: UIColor, onOpenURL: ((URL) -> Void)?) {
        numberOfLines = lineLimit ?? 0
        lineBreakMode = lineLimit == nil ? .byWordWrapping : .byTruncatingTail
        setContentHuggingPriority(lineLimit == 1 ? .required : .defaultLow, for: .horizontal)
        lastLayoutWidth = 0
        setNeedsLayout()
        invalidateIntrinsicContentSize()
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > 0 else { return }
        let widthKey = RichTextUIView.measurementWidthKey(width)
        guard widthKey != RichTextUIView.measurementWidthKey(lastLayoutWidth) else { return }
        lastLayoutWidth = width
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        guard numberOfLines != 1 else { return }
        preferredMaxLayoutWidth = width
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

//    override var intrinsicContentSize: CGSize {
//        guard numberOfLines != 1, bounds.width > 0 else {
//            return super.intrinsicContentSize
//        }
//        return measuredSize(for: bounds.width)
//    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        guard numberOfLines != 1,
              targetSize.width.isFinite,
              targetSize.width > 0 else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        let size = measuredSize(for: targetSize.width)
        return CGSize(
            width: horizontalFittingPriority == .required ? targetSize.width : size.width,
            height: size.height
        )
    }

    private func measuredSize(for width: CGFloat) -> CGSize {
        preferredMaxLayoutWidth = width
        let size = sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: UIView.noIntrinsicMetric, height: ceil(size.height))
    }
}

private final class RichTextTextView: UITextView, UITextViewDelegate, RichTextTextRendering, RichTextFittingPreparing {
    private var linkHandler: ((URL) -> Void)?
    private var selectionEnabled = false
    private var lastLayoutWidth: CGFloat = 0
    var renderedView: UIView { self }

    init() {
        super.init(frame: .zero, textContainer: nil)
        backgroundColor = .clear
        isEditable = false
        isScrollEnabled = false
        textContainerInset = .zero
        textContainer.lineFragmentPadding = 0
        adjustsFontForContentSizeCategory = true
        dataDetectorTypes = []
        textDragInteraction?.isEnabled = false
        setContentHuggingPriority(.required, for: .vertical)
        setContentCompressionResistancePriority(.required, for: .vertical)
        delegate = self
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(
        attributedText: NSAttributedString,
        alignment: NSTextAlignment,
        lineLimit: Int?,
        selectionEnabled: Bool,
        linkColor: UIColor,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.attributedText = attributedText
        textAlignment = alignment
        update(
            lineLimit: lineLimit,
            selectionEnabled: selectionEnabled,
            linkColor: linkColor,
            onOpenURL: onOpenURL
        )
    }

    func applyAttributedText(_ attributedText: NSAttributedString) {
        self.attributedText = attributedText
        lastLayoutWidth = 0
        invalidateIntrinsicContentSize()
    }

    func update(
        lineLimit: Int?,
        selectionEnabled: Bool,
        linkColor: UIColor,
        onOpenURL: ((URL) -> Void)?
    ) {
        self.selectionEnabled = selectionEnabled
        linkHandler = onOpenURL
        isSelectable = true
        linkTextAttributes = [
            .foregroundColor: linkColor,
            .underlineStyle: 0,
        ]
        textContainer.maximumNumberOfLines = lineLimit ?? 0
        textContainer.lineBreakMode = lineLimit == nil ? .byWordWrapping : .byTruncatingTail
        gestureRecognizers?.forEach {
            if $0 is UILongPressGestureRecognizer {
                $0.isEnabled = selectionEnabled
            }
        }
        if !selectionEnabled {
            selectedTextRange = nil
        }
        lastLayoutWidth = 0
        invalidateIntrinsicContentSize()
    }

    override var selectedTextRange: UITextRange? {
        get { selectionEnabled ? super.selectedTextRange : nil }
        set { super.selectedTextRange = selectionEnabled ? newValue : nil }
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if selectionEnabled {
            return super.point(inside: point, with: event)
        }
        return url(at: point) != nil
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > 0 else { return }
        let widthKey = RichTextUIView.measurementWidthKey(width)
        guard widthKey != RichTextUIView.measurementWidthKey(lastLayoutWidth) else { return }
        lastLayoutWidth = width
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

//
//    override var intrinsicContentSize: CGSize {
//        let width = bounds.width > 0 ? bounds.width : UIScreen.main.bounds.width
//        return measuredSize(for: width)
//    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width.isFinite && targetSize.width > 0
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : UIScreen.main.bounds.width)
        let size = measuredSize(for: width)
        return CGSize(
            width: horizontalFittingPriority == .required ? width : size.width,
            height: size.height
        )
    }

    private func measuredSize(for width: CGFloat) -> CGSize {
        let size = sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: UIView.noIntrinsicMetric, height: ceil(size.height))
    }

    func textView(_ textView: UITextView, primaryActionFor textItem: UITextItem, defaultAction: UIAction) -> UIAction? {
        guard case .link(let url) = textItem.content else { return defaultAction }
        
        return UIAction { [weak self] _ in
            self?.linkHandler?(url)
        }
    }
    
    func textView(_ textView: UITextView, menuConfigurationFor textItem: UITextItem, defaultMenu: UIMenu) -> UITextItem.MenuConfiguration? {
        guard case .link(let url) = textItem.content else {
            return UITextItem.MenuConfiguration(
                preview: .none,
                menu: defaultMenu
            )
        }
        if url.absoluteString.hasPrefix("flare://"), let route = Route.fromDeepLink(url: url.absoluteString) {
            let host = UIHostingController(rootView: route.view(onNavigate: { _ in
                
            }, clearToHome: {
                
            }))
            host.view.backgroundColor = .clear
            host.sizingOptions = [.intrinsicContentSize]


            return UITextItem.MenuConfiguration(
                preview: .view(host.view),
                menu: UIMenu(title: "", children: [])
            )
        } else {
            return UITextItem.MenuConfiguration(
                preview: .default,
                menu: defaultMenu
            )
        }
    }

    private func url(at point: CGPoint) -> URL? {
        guard attributedText.length > 0,
              let position = closestPosition(to: point) else {
            return nil
        }
        let characterIndex = offset(from: beginningOfDocument, to: position)
        guard characterIndex < attributedText.length else { return nil }

        if let url = attributedText.attribute(.link, at: characterIndex, effectiveRange: nil) as? URL {
            return url
        }
        if let string = attributedText.attribute(.link, at: characterIndex, effectiveRange: nil) as? String {
            return URL(string: string)
        }
        return nil
    }
}

private final class RichTextQuoteBlockView: UIView, RichTextFittingPreparing {
    static var barWidth: CGFloat { UIFontMetrics(forTextStyle: .body).scaledValue(for: 3) }
    static var horizontalInset: CGFloat { 12 + barWidth + 11 + 12 }
    static let verticalInset: CGFloat = 8

    private let contentView: UIView
    private var lastLayoutWidth: CGFloat = 0

    init(contentView: UIView) {
        self.contentView = contentView
        super.init(frame: .zero)
        backgroundColor = UIColor.secondaryLabel.withAlphaComponent(0.08)
        layer.cornerRadius = 8
        layer.masksToBounds = true

        let bar = UIView()
        bar.backgroundColor = UIColor.secondaryLabel.withAlphaComponent(0.18)
        bar.translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false

        addSubview(bar)
        addSubview(contentView)

        NSLayoutConstraint.activate([
            bar.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            bar.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            bar.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            bar.widthAnchor.constraint(equalToConstant: Self.barWidth),

            contentView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            contentView.leadingAnchor.constraint(equalTo: bar.trailingAnchor, constant: 11),
            contentView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            contentView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > Self.horizontalInset else { return }
        let widthKey = RichTextUIView.measurementWidthKey(width)
        guard widthKey != RichTextUIView.measurementWidthKey(lastLayoutWidth) else { return }
        lastLayoutWidth = width
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        let contentWidth = width - Self.horizontalInset
        if let fittingView = contentView as? RichTextFittingPreparing {
            fittingView.prepareForFitting(width: contentWidth)
        } else {
            guard RichTextUIView.measurementWidthKey(contentView.bounds.width) != RichTextUIView.measurementWidthKey(contentWidth) else { return }
            contentView.bounds = CGRect(
                x: contentView.bounds.minX,
                y: contentView.bounds.minY,
                width: contentWidth,
                height: contentView.bounds.height
            )
            contentView.invalidateIntrinsicContentSize()
            contentView.setNeedsLayout()
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width.isFinite && targetSize.width > 0
            ? targetSize.width
            : bounds.width
        guard width > Self.horizontalInset else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        prepareForFitting(width: width)
        let contentWidth = width - Self.horizontalInset
        let contentSize = contentView.systemLayoutSizeFitting(
            CGSize(width: contentWidth, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        return CGSize(
            width: horizontalFittingPriority == .required ? targetSize.width : contentSize.width + Self.horizontalInset,
            height: ceil(contentSize.height + Self.verticalInset * 2)
        )
    }
}

private final class RichTextBlockImageView: UIView, RichTextFittingPreparing {
    var onOpenURL: ((URL) -> Void)?
    var onAspectRatioChanged: ((CGFloat) -> Void)?

    private let url: String
    private let href: String?
    private let imageView = UIImageView()
    private var aspectConstraint: NSLayoutConstraint?
    private var aspectRatio: CGFloat?
    private var lastLayoutWidth: CGFloat = 0

    init(url: String, href: String?) {
        self.url = url
        self.href = href
        super.init(frame: .zero)
        setup()
        loadImage()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    private func setup() {
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.layer.cornerRadius = 12
        imageView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(imageView)
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: topAnchor),
            imageView.leadingAnchor.constraint(equalTo: leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        if href?.isEmpty == false {
            isUserInteractionEnabled = true
            addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(onTapped)))
        }
    }

    private func loadImage() {
        guard let imageURL = URL(string: url) else { return }
        imageView.kf.setImage(with: imageURL) { [weak self] result in
            guard let self else { return }
            if case .success(let value) = result {
                self.updateAspectRatio(for: value.image)
            }
        }
    }

    private func updateAspectRatio(for image: UIImage) {
        aspectConstraint?.isActive = false
        let ratio = image.size.height / max(image.size.width, 1)
        aspectRatio = ratio
        aspectConstraint = heightAnchor.constraint(equalTo: widthAnchor, multiplier: ratio)
        aspectConstraint?.isActive = true
        lastLayoutWidth = 0
        invalidateIntrinsicContentSize()
        onAspectRatioChanged?(ratio)
    }

    func prepareForFitting(width: CGFloat) {
        guard width.isFinite, width > 0 else { return }
        let widthKey = RichTextUIView.measurementWidthKey(width)
        guard widthKey != RichTextUIView.measurementWidthKey(lastLayoutWidth) else { return }
        lastLayoutWidth = width
        bounds = CGRect(x: bounds.minX, y: bounds.minY, width: width, height: bounds.height)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func measuredSize(width: CGFloat) -> CGSize {
        guard width.isFinite, width > 0 else { return .zero }
        let ratio = aspectRatio ?? 9.0 / 16.0
        return CGSize(width: width, height: ceil(width * ratio))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width.isFinite && targetSize.width > 0
            ? targetSize.width
            : bounds.width
        guard width > 0 else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        let size = measuredSize(width: width)
        return CGSize(width: horizontalFittingPriority == .required ? targetSize.width : size.width, height: size.height)
    }

    @objc private func onTapped() {
        guard let href, let url = URL(string: href) else { return }
        onOpenURL?(url)
    }
}
