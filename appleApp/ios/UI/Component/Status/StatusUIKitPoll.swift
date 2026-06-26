import UIKit
import KotlinSharedUI

// MARK: - StatusPollUIView
// Mirrors StatusPollView.swift: vote-mode vs view-mode rows, optional expired
// footer, optional vote submit button.
final class StatusPollUIView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    var onVote: ((_ indices: [Int]) -> Void)?

    private let expiredLabel = UILabel()
    private let expiresAtLabel = UILabel()
    private let expiresAtTime = DateTimeUILabel()
    private let voteButton = UIButton(type: .system)

    private var data: UiPoll?
    private var selectedOption: [Int] = []
    private var buttonPool: [PollOptionButton] = []
    private var resultPool: [PollOptionResultView] = []
    private var optionViews: [UIView] = []
    private var footerViews: [UIView] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        expiredLabel.font = .preferredFont(forTextStyle: .caption1)
        expiredLabel.textColor = .secondaryLabel
        expiredLabel.adjustsFontForContentSizeCategory = true
        expiredLabel.text = String(localized: "poll_expired")

        expiresAtLabel.font = .preferredFont(forTextStyle: .caption1)
        expiresAtLabel.textColor = .secondaryLabel
        expiresAtLabel.adjustsFontForContentSizeCategory = true
        expiresAtLabel.text = String(localized: "poll_expires_at")

        expiresAtTime.fullTime = true

        voteButton.setTitle(String(localized: "poll_vote"), for: .normal)
        voteButton.addTarget(self, action: #selector(submitVote), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(data: UiPoll, absoluteTimestamp: Bool) {
        self.data = data
        self.selectedOption = []

        var optionDesired: [UIView] = []
        for index in 0..<Int(data.options.count) {
            let option = data.options[index]
            if data.canVote {
                while buttonPool.count <= index {
                    buttonPool.append(PollOptionButton())
                }
                let row = buttonPool[index]
                row.configure(index: index, title: option.title)
                row.onToggle = { [weak self] idx in
                    guard let self, let data = self.data else { return }
                    if data.multiple {
                        if self.selectedOption.contains(idx) {
                            self.selectedOption.removeAll { $0 == idx }
                        } else {
                            self.selectedOption.append(idx)
                        }
                    } else {
                        self.selectedOption = [idx]
                    }
                    self.reapplySelection()
                }
                optionDesired.append(row)
            } else {
                while resultPool.count <= index {
                    resultPool.append(PollOptionResultView())
                }
                let row = resultPool[index]
                row.configure(
                    title: option.title,
                    percentage: option.percentage,
                    humanizedPercentage: option.humanizedPercentage,
                    isOwnVote: data.ownVotes.contains(KotlinInt(value: Int32(index)))
                )
                optionDesired.append(row)
            }
        }

        syncManagedSubviews(parent: self, current: &optionViews, desired: optionDesired)

        var footerDesired: [UIView] = []
        if data.expired {
            footerDesired.append(expiredLabel)
        } else if let expiredAt = data.expiredAt {
            expiresAtTime.absoluteTimestamp = absoluteTimestamp
            expiresAtTime.set(data: expiredAt)
            footerDesired.append(expiresAtLabel)
            footerDesired.append(expiresAtTime)
        }

        if data.canVote {
            footerDesired.append(voteButton)
        }
        syncManagedSubviews(parent: self, current: &footerViews, desired: footerDesired)

        reapplySelection()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    private func reapplySelection() {
        for v in optionViews {
            if let btn = v as? PollOptionButton {
                btn.setSelected(selectedOption.contains(btn.index))
            }
        }
    }

    @objc private func submitVote() {
        onVote?(selectedOption)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let width = size.width.isFinite && size.width > 0 ? size.width : bounds.width
        return CGSize(width: width, height: timelineHeight(for: width) ?? 0)
    }

    override var intrinsicContentSize: CGSize {
        guard bounds.width > 0 else { return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric) }
        return sizeThatFits(CGSize(width: bounds.width, height: CGFloat.greatestFiniteMagnitude))
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width > 0 && targetSize.width.isFinite
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : 1)
        return CGSize(width: horizontalFittingPriority == .required ? width : UIView.noIntrinsicMetric,
                      height: timelineHeight(for: width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let fittingWidth = max(width, 1)
        var y: CGFloat = 0
        let allViews = optionViews + footerViews
        for (index, view) in allViews.enumerated() {
            let height = childHeight(of: view, for: fittingWidth)
            let viewWidth: CGFloat
            let x: CGFloat
            if optionViews.contains(where: { $0 === view }) {
                viewWidth = fittingWidth
                x = 0
            } else {
                viewWidth = min(childWidth(of: view, for: height), fittingWidth)
                x = fittingWidth - viewWidth
            }
            if assignFrames {
                view.frame = CGRect(x: x, y: y, width: viewWidth, height: height).integral
            }
            y += height
            if index < allViews.count - 1 {
                y += 8
            }
        }
        return y
    }
}

// Vote-mode row.
private final class PollOptionButton: UIControl, ManualLayoutMeasurable, TimelineHeightProviding {
    private(set) var index: Int = 0
    var onToggle: ((Int) -> Void)?

    private let titleLabel = UILabel()
    private let checkmark = UIImageView(image: UIImage(systemName: "checkmark.circle"))
    private let bg = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)

        bg.layer.cornerRadius = 8
        bg.layer.cornerCurve = .continuous
        addSubview(bg)

        titleLabel.font = .preferredFont(forTextStyle: .caption1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.numberOfLines = 0
        titleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)

        checkmark.tintColor = .secondaryLabel
        checkmark.contentMode = .scaleAspectFit
        checkmark.setContentHuggingPriority(.required, for: .horizontal)

        addSubview(titleLabel)
        addSubview(checkmark)

        setSelected(false)
        addTarget(self, action: #selector(onTapped), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(index: Int, title: String) {
        self.index = index
        titleLabel.text = title
        setSelected(false)
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func setSelected(_ selected: Bool) {
        checkmark.isHidden = !selected
        bg.backgroundColor = selected
            ? UIColor.tintColor.withAlphaComponent(0.2)
            : .systemGroupedBackground
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    @objc private func onTapped() { onToggle?(index) }

    override func layoutSubviews() {
        super.layoutSubviews()
        layout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let width = size.width.isFinite && size.width > 0 ? size.width : bounds.width
        return CGSize(width: width, height: timelineHeight(for: width) ?? 0)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width > 0 && targetSize.width.isFinite
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : 1)
        return CGSize(width: horizontalFittingPriority == .required ? width : UIView.noIntrinsicMetric,
                      height: timelineHeight(for: width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let inset: CGFloat = 8
        let spacing: CGFloat = checkmark.isHidden ? 0 : 8
        let checkWidth: CGFloat = checkmark.isHidden ? 0 : 16
        let titleWidth = max(width - inset * 2 - spacing - checkWidth, 1)
        let titleHeight = titleLabel.sizeThatFits(CGSize(width: titleWidth, height: CGFloat.greatestFiniteMagnitude)).height
        let contentHeight = max(ceil(titleHeight), checkmark.isHidden ? 0 : 16)
        let totalHeight = contentHeight + inset * 2

        if assignFrames {
            bg.frame = CGRect(x: 0, y: 0, width: width, height: totalHeight).integral
            titleLabel.frame = CGRect(x: inset, y: inset + (contentHeight - titleHeight) / 2, width: titleWidth, height: ceil(titleHeight)).integral
            if checkmark.isHidden {
                checkmark.frame = .zero
            } else {
                checkmark.frame = CGRect(x: width - inset - 16, y: inset + (contentHeight - 16) / 2, width: 16, height: 16).integral
            }
        }
        return totalHeight
    }
}

// View-mode row.
private final class PollOptionResultView: UIView, ManualLayoutMeasurable, TimelineHeightProviding {
    private let titleLabel = UILabel()
    private let check = UIImageView(image: UIImage(systemName: "checkmark.circle"))
    private let pct = UILabel()
    private let progress = UIProgressView(progressViewStyle: .default)

    override init(frame: CGRect) {
        super.init(frame: frame)
        titleLabel.font = .preferredFont(forTextStyle: .caption1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.numberOfLines = 0
        titleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)

        check.tintColor = .label
        check.contentMode = .scaleAspectFit
        check.setContentHuggingPriority(.required, for: .horizontal)

        pct.font = .preferredFont(forTextStyle: .caption1)
        pct.textColor = .secondaryLabel
        pct.adjustsFontForContentSizeCategory = true
        pct.setContentHuggingPriority(.required, for: .horizontal)

        progress.progressTintColor = .tintColor
        addSubview(titleLabel)
        addSubview(check)
        addSubview(pct)
        addSubview(progress)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(title: String, percentage: Float, humanizedPercentage: String, isOwnVote: Bool) {
        titleLabel.text = title
        pct.text = humanizedPercentage
        progress.progress = percentage
        check.isHidden = !isOwnVote
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        layout(width: bounds.width, assignFrames: true)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        let width = size.width.isFinite && size.width > 0 ? size.width : bounds.width
        return CGSize(width: width, height: timelineHeight(for: width) ?? 0)
    }

    override func systemLayoutSizeFitting(
        _ targetSize: CGSize,
        withHorizontalFittingPriority horizontalFittingPriority: UILayoutPriority,
        verticalFittingPriority: UILayoutPriority
    ) -> CGSize {
        let width = targetSize.width > 0 && targetSize.width.isFinite
            ? targetSize.width
            : (bounds.width > 0 ? bounds.width : 1)
        return CGSize(width: horizontalFittingPriority == .required ? width : UIView.noIntrinsicMetric,
                      height: timelineHeight(for: width) ?? 0)
    }

    func timelineHeight(for width: CGFloat) -> CGFloat? {
        guard width > 0, width.isFinite else { return nil }
        return ceil(layout(width: width, assignFrames: false))
    }

    @discardableResult
    private func layout(width: CGFloat, assignFrames: Bool) -> CGFloat {
        let spacing: CGFloat = 8
        let progressSpacing: CGFloat = 4
        let checkWidth: CGFloat = check.isHidden ? 0 : 16
        let pctSize = pct.sizeThatFits(CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
        let titleWidth = max(width - pctSize.width - checkWidth - spacing * (check.isHidden ? 1 : 2), 1)
        let titleHeight = titleLabel.sizeThatFits(CGSize(width: titleWidth, height: CGFloat.greatestFiniteMagnitude)).height
        let rowHeight = max(ceil(titleHeight), pctSize.height, check.isHidden ? 0 : 16)
        let progressHeight = progress.sizeThatFits(CGSize(width: width, height: CGFloat.greatestFiniteMagnitude)).height
        let totalHeight = rowHeight + progressSpacing + ceil(progressHeight)

        if assignFrames {
            var x: CGFloat = 0
            titleLabel.frame = CGRect(x: x, y: (rowHeight - titleHeight) / 2, width: titleWidth, height: ceil(titleHeight)).integral
            x += titleWidth + spacing
            if !check.isHidden {
                check.frame = CGRect(x: x, y: (rowHeight - 16) / 2, width: 16, height: 16).integral
                x += 16 + spacing
            } else {
                check.frame = .zero
            }
            pct.frame = CGRect(x: x, y: (rowHeight - pctSize.height) / 2, width: ceil(pctSize.width), height: ceil(pctSize.height)).integral
            progress.frame = CGRect(x: 0, y: rowHeight + progressSpacing, width: width, height: ceil(progressHeight)).integral
        }
        return totalHeight
    }
}
