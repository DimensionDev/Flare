import SwiftUI
import UIKit

public class ToastView: UIView {
    private let containerView: UIView = {
        let view = UIView()
        view.backgroundColor = UIColor.black.withAlphaComponent(0.8)
        view.layer.cornerRadius = 10
        return view
    }()

    private let iconImageView: UIImageView = {
        let imageView = UIImageView()
        imageView.tintColor = .white
        imageView.contentMode = .scaleAspectFit
        return imageView
    }()

    private let messageLabel: UILabel = {
        let label = UILabel()
        label.textColor = .white
        label.textAlignment = .center
        label.font = .systemFont(ofSize: 14)
        return label
    }()

    public init(icon: UIImage?, message: String) {
        super.init(frame: CGRect(x: 0, y: 0, width: 120, height: 120))
        setupView(icon: icon, message: message)
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupView(icon: UIImage?, message: String) {
        addSubview(containerView)
        containerView.frame = bounds

        if let icon {
            iconImageView.image = icon
            containerView.addSubview(iconImageView)
            iconImageView.frame = CGRect(x: 45, y: 30, width: 30, height: 30)
        }

        messageLabel.text = message
        containerView.addSubview(messageLabel)
        messageLabel.frame = CGRect(x: 0, y: 70, width: 120, height: 20)
    }

    public func show(duration: TimeInterval = 1.3) {
        let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene
        let window = windowScene!.windows.first
        center = window!.center
        window!.addSubview(self)

        UIView.animate(withDuration: 0.3, delay: duration, options: [], animations: {
            self.alpha = 0
        }) { _ in
            self.removeFromSuperview()
        }
    }
}
