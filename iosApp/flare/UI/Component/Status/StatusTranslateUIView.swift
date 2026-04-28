import UIKit
import Combine
import KotlinSharedUI

/// UIKit translation / summary renderer for status detail cells.
///
/// This intentionally avoids UIHostingController so translated / summarized
/// content has a deterministic UIKit measurement path.
final class StatusTranslateUIView: UIView, TimelineHeightProviding {
    var content: UiRichText? { didSet { configureIfNeeded() } }
    var contentWarning: UiRichText? { didSet { configureIfNeeded() } }
    var isSummaryAvailable = false {
        didSet {
            guard isSummaryAvailable != oldValue else { return }
            if !isSummaryAvailable {
                isSummaryExpanded = false
                summaryPresenter = nil
            }
            updateButtonVisibility()
            setNeedsHeightUpdate()
        }
    }
    var onLocalHeightInvalidated: (() -> Void)?

    private let buttonRow = UIView()
    private let translateButton = UIButton(type: .system)
    private let tldrButton = UIButton(type: .system)

    private let contentTranslation = TranslationResultView()
    private let contentWarningTranslation = TranslationResultView()
    private let summaryResult = TextResultView()

    private var translationPresenter: KotlinPresenter<UiState<UiRichText>>?
    private var contentWarningPresenter: KotlinPresenter<UiState<UiRichText>>?
    private var summaryPresenter: KotlinPresenter<UiState<NSString>>?
    private var cancellables = Set<AnyCancellable>()

    private var isTranslateExpanded = false
    private var isSummaryExpanded = false
    private var lastContentSignature: String?
    private var lastContentWarningSignature: String?

    private static let verticalSpacing: CGFloat = 8
    private static let buttonSpacing: CGFloat = 12

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    @MainActor
    deinit {
        cancellables.removeAll()
        translationPresenter = nil
        contentWarningPresenter = nil
        summaryPresenter = nil
    }

    private func commonInit() {
        clipsToBounds = false
        translateButton.setTitle(String(localized: "status_translate"), for: .normal)
        translateButton.contentHorizontalAlignment = .leading
        translateButton.addTarget(self, action: #selector(toggleTranslate), for: .touchUpInside)

        tldrButton.setTitle(String(localized: "status_tldr"), for: .normal)
        tldrButton.contentHorizontalAlignment = .leading
        tldrButton.addTarget(self, action: #selector(toggleSummary), for: .touchUpInside)

        addSubview(buttonRow)
        buttonRow.addSubview(translateButton)
        buttonRow.addSubview(tldrButton)
        addSubview(contentWarningTranslation)
        addSubview(contentTranslation)
        addSubview(summaryResult)

        contentWarningTranslation.isHidden = true
        contentTranslation.isHidden = true
        summaryResult.isHidden = true
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        performLayout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width.isFinite && targetSize.width > 0
            ? targetSize.width
            : bounds.width
        guard width > 0, width.isFinite else {
            return super.systemLayoutSizeFitting(
                targetSize,
                withHorizontalFittingPriority: horizontalFittingPriority,
                verticalFittingPriority: verticalFittingPriority
            )
        }
        return CGSize(width: width, height: timelineHeight(for: width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard content != nil, width > 0, width.isFinite else { return nil }
        return ceil(performLayout(width: width, assignFrames: false))
    }

    func prepareForPoolRemoval() {
        onLocalHeightInvalidated = nil
        content = nil
        contentWarning = nil
        lastContentSignature = nil
        lastContentWarningSignature = nil
        isTranslateExpanded = false
        isSummaryExpanded = false
        resetPresenters()
        contentTranslation.reset()
        contentWarningTranslation.reset()
        summaryResult.reset()
        updateButtonVisibility()
        removeFromSuperview()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func configureIfNeeded() {
        guard let content else {
            resetPresenters()
            lastContentSignature = nil
            lastContentWarningSignature = nil
            isTranslateExpanded = false
            isSummaryExpanded = false
            updateButtonVisibility()
            setNeedsHeightUpdate()
            return
        }

        let contentSignature = content.toTranslatableText()
        let contentWarningSignature = contentWarning?.toTranslatableText()
        guard contentSignature != lastContentSignature ||
              contentWarningSignature != lastContentWarningSignature else {
            updateButtonVisibility()
            return
        }

        lastContentSignature = contentSignature
        lastContentWarningSignature = contentWarningSignature
        resetPresenters()
        contentTranslation.reset()
        contentWarningTranslation.reset()
        summaryResult.reset()
        isTranslateExpanded = false
        isSummaryExpanded = false
        updateButtonVisibility()
        setNeedsHeightUpdate()
    }

    private func updateButtonVisibility() {
        let hasContent = content?.isEmpty == false
        buttonRow.isHidden = !hasContent
        translateButton.isHidden = !hasContent
        tldrButton.isHidden = !(hasContent && content?.isLongText == true && isSummaryAvailable)
        contentWarningTranslation.isHidden = !isTranslateExpanded || contentWarning == nil
        contentTranslation.isHidden = !isTranslateExpanded
        summaryResult.isHidden = !isSummaryExpanded || !isSummaryAvailable
    }

    @objc private func toggleTranslate() {
        isTranslateExpanded.toggle()
        updateButtonVisibility()
        if isTranslateExpanded {
            ensureTranslationPresenters()
        }
        setNeedsHeightUpdate()
        invalidateContainingCollectionLayout()
    }

    @objc private func toggleSummary() {
        guard isSummaryAvailable else { return }
        isSummaryExpanded.toggle()
        updateButtonVisibility()
        if isSummaryExpanded {
            ensureSummaryPresenter()
        }
        setNeedsHeightUpdate()
        invalidateContainingCollectionLayout()
    }

    private func ensureTranslationPresenters() {
        guard let content else { return }
        if translationPresenter == nil {
            let presenter = KotlinPresenter(
                presenter: TranslatePresenter(source: content, targetLanguage: currentTargetLanguage())
            )
            translationPresenter = presenter
            presenter.$state
                .receive(on: DispatchQueue.main)
                .sink { [weak self] state in
                    self?.contentTranslation.render(state: state)
                    self?.setNeedsHeightUpdate()
                    self?.invalidateContainingCollectionLayout()
                }
                .store(in: &cancellables)
        }

        if let contentWarning, contentWarningPresenter == nil {
            let presenter = KotlinPresenter(
                presenter: TranslatePresenter(source: contentWarning, targetLanguage: currentTargetLanguage())
            )
            contentWarningPresenter = presenter
            presenter.$state
                .receive(on: DispatchQueue.main)
                .sink { [weak self] state in
                    self?.contentWarningTranslation.render(state: state)
                    self?.setNeedsHeightUpdate()
                    self?.invalidateContainingCollectionLayout()
                }
                .store(in: &cancellables)
        }
    }

    private func ensureSummaryPresenter() {
        guard let content, summaryPresenter == nil else { return }
        let source: String
        if let contentWarning, !contentWarning.isEmpty {
            source = "Content Warning:\n\(contentWarning.toTranslatableText())\n\nContent:\n\(content.toTranslatableText())"
        } else {
            source = "Content:\n\(content.toTranslatableText())"
        }
        let presenter = KotlinPresenter(
            presenter: AiTLDRPresenter(source: source, targetLanguage: currentTargetLanguage())
        )
        summaryPresenter = presenter
        presenter.$state
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state in
                self?.summaryResult.render(state: state)
                self?.setNeedsHeightUpdate()
                self?.invalidateContainingCollectionLayout()
            }
            .store(in: &cancellables)
    }

    private func resetPresenters() {
        cancellables.removeAll()
        translationPresenter = nil
        contentWarningPresenter = nil
        summaryPresenter = nil
    }

    private func currentTargetLanguage() -> String {
        Locale.current.language.languageCode?.identifier ?? "en"
    }

    private func setNeedsHeightUpdate() {
        invalidateIntrinsicContentSize()
        setNeedsLayout()
        superview?.setNeedsLayout()
        onLocalHeightInvalidated?()
    }

    private func invalidateContainingCollectionLayout() {
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
                } else {
                    collectionView.collectionViewLayout.invalidateLayout()
                }
                collectionView.performBatchUpdates(nil)
                return
            }
            responder = current.next
        }
    }

    @discardableResult
    private func performLayout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        guard width > 0, width.isFinite, content?.isEmpty == false else { return 0 }
        var y: CGFloat = 0

        let rowHeight = buttonRowHeight()
        if assignFrames {
            buttonRow.frame = CGRect(x: 0, y: y, width: width, height: rowHeight)
            layoutButtons(width: width, height: rowHeight)
        }
        y += rowHeight

        for view in [contentWarningTranslation, contentTranslation, summaryResult] where !view.isHidden {
            y += Self.verticalSpacing
            let height = childHeight(of: view, for: width)
            if assignFrames {
                view.frame = CGRect(x: 0, y: y, width: width, height: height)
            }
            y += height
        }
        return y
    }

    private func buttonRowHeight() -> CGFloat {
        let fittingSize = CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        let translateHeight = translateButton.sizeThatFits(fittingSize).height
        let summaryHeight = tldrButton.isHidden ? 0 : tldrButton.sizeThatFits(fittingSize).height
        return ceil(max(translateHeight, summaryHeight, 1))
    }

    private func layoutButtons(width: CGFloat, height: CGFloat) {
        let translateWidth = min(
            translateButton.sizeThatFits(CGSize(width: .greatestFiniteMagnitude, height: height)).width,
            width
        )
        translateButton.frame = CGRect(x: 0, y: 0, width: translateWidth, height: height)

        if tldrButton.isHidden {
            tldrButton.frame = .zero
            return
        }
        let summaryX = min(translateWidth + Self.buttonSpacing, width)
        let summaryWidth = max(width - summaryX, 0)
        tldrButton.frame = CGRect(x: summaryX, y: 0, width: summaryWidth, height: height)
    }
}

private final class TranslationResultView: UIView, TimelineHeightProviding {
    private let richText = RichTextUIView()
    private let textResult = TextResultView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(richText)
        addSubview(textResult)
        richText.isHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func reset() {
        richText.configure(text: nil, lineLimit: nil, isTextSelectionEnabled: false, onOpenURL: nil)
        richText.isHidden = true
        textResult.reset()
        textResult.isHidden = false
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func render(state: UiState<UiRichText>) {
        switch onEnum(of: state) {
        case .success(let success):
            richText.configure(
                text: success.data,
                lineLimit: nil,
                isTextSelectionEnabled: false,
                onOpenURL: nil,
                contentKey: success.data.toTranslatableText().hashValue
            )
            richText.isHidden = false
            textResult.isHidden = true
        case .error(let error):
            richText.isHidden = true
            textResult.isHidden = false
            textResult.showText(error.throwable.message ?? "Unknown Error")
        case .loading:
            richText.isHidden = true
            textResult.isHidden = false
            textResult.showLoading()
        }
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let target = richText.isHidden ? textResult : richText
        target.frame = bounds
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return richText.isHidden ? textResult.timelineHeight(for: width) : richText.timelineHeight(for: width)
    }
}

private final class TextResultView: UIView, TimelineHeightProviding {
    private let label = UILabel()
    private let spinner = UIActivityIndicatorView(style: .medium)
    private var isLoading = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        label.numberOfLines = 0
        label.font = .preferredFont(forTextStyle: .body)
        label.textColor = .label
        addSubview(label)
        addSubview(spinner)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func reset() {
        showLoading()
    }

    func showLoading() {
        isLoading = true
        label.text = nil
        spinner.startAnimating()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func showText(_ text: String) {
        isLoading = false
        spinner.stopAnimating()
        label.text = text
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func render(state: UiState<NSString>) {
        switch onEnum(of: state) {
        case .success(let success):
            showText(String(success.data))
        case .error(let error):
            showText(error.throwable.message ?? "Unknown Error")
        case .loading:
            showLoading()
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        if isLoading {
            spinner.frame = CGRect(x: 0, y: 0, width: 28, height: 28)
            label.frame = .zero
        } else {
            spinner.frame = .zero
            label.frame = bounds
        }
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        CGSize(width: size.width, height: timelineHeight(for: size.width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        if isLoading {
            return 28
        }
        let size = label.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude))
        return ceil(size.height)
    }
}
