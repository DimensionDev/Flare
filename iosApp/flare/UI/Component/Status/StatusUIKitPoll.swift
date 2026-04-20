import UIKit
import KotlinSharedUI

// MARK: - StatusPollUIView
// Mirrors StatusPollView.swift: vote-mode vs view-mode rows, optional expired
// footer, optional vote submit button.
final class StatusPollUIView: UIView {
    var onVote: ((_ indices: [Int]) -> Void)?

    private let column = UIStackView()
    private let optionsColumn = UIStackView()
    private let expiredLabel = UILabel()
    private let expiresAtLabel = UILabel()
    private let expiresAtTime = DateTimeUILabel()
    private let voteButton = UIButton(type: .system)

    private var data: UiPoll?
    private var selectedOption: [Int] = []
    private var buttonPool: [PollOptionButton] = []
    private var resultPool: [PollOptionResultView] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        column.axis = .vertical
        column.alignment = .trailing
        column.spacing = 8
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        optionsColumn.axis = .vertical
        optionsColumn.alignment = .fill
        optionsColumn.spacing = 8
        optionsColumn.translatesAutoresizingMaskIntoConstraints = false
//        optionsColumn.widthAnchor.constraint(equalTo: column.widthAnchor).isActive = true

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
        optionsColumn.flareSyncArrangedSubviews(optionDesired)

        var columnDesired: [UIView] = [optionsColumn]
        if data.expired {
            columnDesired.append(expiredLabel)
        } else if let expiredAt = data.expiredAt {
            expiresAtTime.absoluteTimestamp = absoluteTimestamp
            expiresAtTime.set(data: expiredAt)
            columnDesired.append(expiresAtLabel)
            columnDesired.append(expiresAtTime)
        }

        if data.canVote {
            columnDesired.append(voteButton)
        }
        column.flareSyncArrangedSubviews(columnDesired)

        reapplySelection()
    }

    private func reapplySelection() {
        for v in optionsColumn.arrangedSubviews {
            if let btn = v as? PollOptionButton {
                btn.setSelected(selectedOption.contains(btn.index))
            }
        }
    }

    @objc private func submitVote() {
        onVote?(selectedOption)
    }
}

// Vote-mode row.
private final class PollOptionButton: UIControl {
    private(set) var index: Int = 0
    var onToggle: ((Int) -> Void)?

    private let titleLabel = UILabel()
    private let checkmark = UIImageView(image: UIImage(systemName: "checkmark.circle"))
    private let hStack = UIStackView()
    private let bg = UIView()

    override init(frame: CGRect) {
        super.init(frame: frame)

        bg.layer.cornerRadius = 8
        bg.layer.cornerCurve = .continuous
        bg.translatesAutoresizingMaskIntoConstraints = false
        addSubview(bg)

        titleLabel.font = .preferredFont(forTextStyle: .caption1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.numberOfLines = 0
        titleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)

        checkmark.tintColor = .secondaryLabel
        checkmark.contentMode = .scaleAspectFit
        checkmark.setContentHuggingPriority(.required, for: .horizontal)

        hStack.axis = .horizontal
        hStack.alignment = .center
        hStack.spacing = 8
        hStack.isUserInteractionEnabled = false
        hStack.translatesAutoresizingMaskIntoConstraints = false
        hStack.addArrangedSubview(titleLabel)
        hStack.addArrangedSubview(checkmark)
        addSubview(hStack)

        NSLayoutConstraint.activate([
            bg.topAnchor.constraint(equalTo: topAnchor),
            bg.leadingAnchor.constraint(equalTo: leadingAnchor),
            bg.trailingAnchor.constraint(equalTo: trailingAnchor),
            bg.bottomAnchor.constraint(equalTo: bottomAnchor),
            hStack.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            hStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            hStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            hStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            checkmark.widthAnchor.constraint(equalToConstant: 16),
            checkmark.heightAnchor.constraint(equalToConstant: 16),
        ])

        setSelected(false)
        addTarget(self, action: #selector(onTapped), for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(index: Int, title: String) {
        self.index = index
        titleLabel.text = title
        setSelected(false)
    }

    func setSelected(_ selected: Bool) {
        checkmark.isHidden = !selected
        bg.backgroundColor = selected
            ? UIColor.tintColor.withAlphaComponent(0.2)
            : .systemGroupedBackground
    }

    @objc private func onTapped() { onToggle?(index) }
}

// View-mode row.
private final class PollOptionResultView: UIView {
    private let column = UIStackView()
    private let row = UIStackView()
    private let titleLabel = UILabel()
    private let check = UIImageView(image: UIImage(systemName: "checkmark.circle"))
    private let pct = UILabel()
    private let progress = UIProgressView(progressViewStyle: .default)

    override init(frame: CGRect) {
        super.init(frame: frame)
        column.axis = .vertical
        column.spacing = 4
        column.alignment = .fill
        column.translatesAutoresizingMaskIntoConstraints = false
        addSubview(column)
        NSLayoutConstraint.activate([
            column.topAnchor.constraint(equalTo: topAnchor),
            column.leadingAnchor.constraint(equalTo: leadingAnchor),
            column.trailingAnchor.constraint(equalTo: trailingAnchor),
            column.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 8

        titleLabel.font = .preferredFont(forTextStyle: .caption1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.numberOfLines = 0
        titleLabel.setContentHuggingPriority(.defaultLow, for: .horizontal)

        check.tintColor = .label
        check.contentMode = .scaleAspectFit
        check.setContentHuggingPriority(.required, for: .horizontal)
        NSLayoutConstraint.activate([
            check.widthAnchor.constraint(equalToConstant: 16),
            check.heightAnchor.constraint(equalToConstant: 16),
        ])

        pct.font = .preferredFont(forTextStyle: .caption1)
        pct.textColor = .secondaryLabel
        pct.adjustsFontForContentSizeCategory = true
        pct.setContentHuggingPriority(.required, for: .horizontal)

        column.addArrangedSubview(row)

        progress.progressTintColor = .tintColor
        column.addArrangedSubview(progress)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func configure(title: String, percentage: Float, humanizedPercentage: String, isOwnVote: Bool) {
        titleLabel.text = title
        pct.text = humanizedPercentage
        progress.progress = percentage
        var desired: [UIView] = [titleLabel]
        if isOwnVote {
            desired.append(check)
        }
        desired.append(pct)
        row.flareSyncArrangedSubviews(desired)
    }
}
