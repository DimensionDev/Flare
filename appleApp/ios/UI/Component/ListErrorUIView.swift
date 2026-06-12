import UIKit
import KotlinSharedUI

/// UIKit port of `ListErrorView`.
///
/// Matches the three cases of the SwiftUI implementation:
///   * `LoginExpiredException` → login-expired prompt with account key
///   * `RequireReLoginException` → permission-denied prompt
///   * everything else → generic error with retry + message
final class ListErrorUIView: UIView {
    var onOpenURL: ((URL) -> Void)?
    private var onRetry: (() -> Void)?
    private var loginDeeplinkURL: URL? {
        URL(string: DeeplinkRoute.Login.shared.toUri())
    }

    private let iconView: UIImageView = {
        let iv = UIImageView()
        iv.contentMode = .scaleAspectFit
        iv.tintColor = .label
        iv.translatesAutoresizingMaskIntoConstraints = false
        return iv
    }()
    private let titleLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .headline)
        l.textAlignment = .center
        l.numberOfLines = 0
        return l
    }()
    private let messageLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .body)
        l.textAlignment = .center
        l.numberOfLines = 0
        l.isHidden = true
        return l
    }()
    private let button: UIButton = {
        var cfg = UIButton.Configuration.borderedProminent()
        cfg.cornerStyle = .capsule
        let b = UIButton(configuration: cfg)
        return b
    }()
    private let detailLabel: UILabel = {
        let l = UILabel()
        l.font = .preferredFont(forTextStyle: .caption1)
        l.textColor = .secondaryLabel
        l.textAlignment = .center
        l.numberOfLines = 0
        l.isHidden = true
        return l
    }()
    private let stack = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 8
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(iconView)
        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(messageLabel)
        stack.addArrangedSubview(button)
        stack.addArrangedSubview(detailLabel)
        addSubview(stack)
        NSLayoutConstraint.activate([
            iconView.widthAnchor.constraint(equalToConstant: 64),
            iconView.heightAnchor.constraint(equalToConstant: 64),
            stack.centerXAnchor.constraint(equalTo: centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: centerYAnchor),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -16),
            stack.topAnchor.constraint(greaterThanOrEqualTo: topAnchor, constant: 16),
        ])
        button.addAction(UIAction { [weak self] _ in self?.handleButtonTap() }, for: .touchUpInside)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    enum Mode {
        case retry
        case openLogin
    }
    private var mode: Mode = .retry

    func configure(error: KotlinThrowable, onRetry: @escaping () -> Void) {
        self.onRetry = onRetry
        detailLabel.isHidden = true
        messageLabel.isHidden = true

        if let expired = error as? LoginExpiredException {
            iconView.image = UIImage(systemName: "person.badge.shield.exclamationmark")
            titleLabel.text = String(format: String(localized: "error_login_expired %@"), "\(expired.accountKey)")
            button.setTitle(String(localized: "error_login_expired_action"), for: .normal)
            mode = .openLogin
        } else if error is RequireReLoginException {
            iconView.image = UIImage(systemName: "person.badge.shield.exclamationmark")
            titleLabel.text = String(localized: "permission_denied_title")
            messageLabel.text = String(localized: "permission_denied_message")
            messageLabel.isHidden = false
            button.setTitle(String(localized: "error_login_expired_action"), for: .normal)
            mode = .openLogin
        } else {
            iconView.image = UIImage(systemName: "exclamationmark.triangle.text.page")
            titleLabel.text = String(localized: "error_generic")
            button.setTitle(String(localized: "action_retry"), for: .normal)
            mode = .retry
            if let message = error.message {
                detailLabel.text = message
                detailLabel.isHidden = false
            }
        }
    }

    private func handleButtonTap() {
        switch mode {
        case .retry:
            onRetry?()
        case .openLogin:
            if let url = loginDeeplinkURL { onOpenURL?(url) }
        }
    }
}
