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
        label.numberOfLines = 0  // 支持多行显示
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
        messageLabel.text = message
        
        // 计算动态尺寸
        let hasIcon = icon != nil
        let calculatedSize = calculateSize(for: message, hasIcon: hasIcon)
        
        // 更新 ToastView 的尺寸
        frame = CGRect(x: frame.origin.x, y: frame.origin.y, width: calculatedSize.width, height: calculatedSize.height)
        
        // 设置 containerView
        addSubview(containerView)
        containerView.frame = bounds
        
        let padding: CGFloat = 16
        
        if let icon {
            iconImageView.image = icon
            containerView.addSubview(iconImageView)
            
            // 图标居中显示
            let iconSize: CGFloat = 30
            let iconX = (calculatedSize.width - iconSize) / 2
            iconImageView.frame = CGRect(x: iconX, y: 20, width: iconSize, height: iconSize)
            
            // 文本在图标下方
            let textY: CGFloat = 20 + iconSize + 10  // 图标下方10px间距
            let textHeight = calculatedSize.height - textY - padding
            messageLabel.frame = CGRect(x: padding, y: textY, width: calculatedSize.width - padding * 2, height: textHeight)
        } else {
            // 无图标时，文本居中显示
            messageLabel.frame = CGRect(x: padding, y: padding, width: calculatedSize.width - padding * 2, height: calculatedSize.height - padding * 2)
        }
        
        containerView.addSubview(messageLabel)
    }


    private func calculateSize(for message: String, hasIcon: Bool) -> CGSize {
        // 获取屏幕宽度
        let screenWidth = UIScreen.main.bounds.width
        let maxWidth = screenWidth * 0.7  // 最大宽度为屏幕宽度的70%
        let minWidth: CGFloat = 120  // 最小宽度保持原来的120
        let padding: CGFloat = 16
        
        // 计算文本尺寸
        let textMaxWidth = maxWidth - padding * 2
        let textAttributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 14)
        ]
        
        let textRect = message.boundingRect(
            with: CGSize(width: textMaxWidth, height: CGFloat.greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: textAttributes,
            context: nil
        )
        
        // 计算所需宽度
        let textWidth = ceil(textRect.width)
        let requiredWidth = max(minWidth, min(maxWidth, textWidth + padding * 2))
        
        // 计算基础文本高度
        let baseTextHeight = ceil(textRect.height)
        
        // 简单处理换行：统计 \n 的数量，每个换行符增加一行高度
        let newlineCount = message.components(separatedBy: "\n").count - 1
        let lineHeight: CGFloat = 18  // 14号字体的行高约为18
        let additionalHeight = CGFloat(newlineCount) * lineHeight
        let textHeight = baseTextHeight + additionalHeight
        
        // 计算所需高度
        var requiredHeight: CGFloat
        
        if hasIcon {
            // 有图标：图标(30) + 上边距(20) + 图标文本间距(10) + 文本高度 + 下边距(16)
            requiredHeight = 20 + 30 + 10 + textHeight + padding
        } else {
            // 无图标：上下边距 + 文本高度
            requiredHeight = padding * 2 + textHeight
        }
        
        // 确保最小高度
        let minHeight: CGFloat = hasIcon ? 120 : 60
        requiredHeight = max(minHeight, requiredHeight)
        
        return CGSize(width: requiredWidth, height: requiredHeight)
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

extension View {
    func successToast(_ message: String, isPresented: Binding<Bool>) -> some View {
        alert(
            "Tips",
            isPresented: isPresented,
            actions: {
                Button("OK", role: .cancel) {}
            },
            message: {
                Text(message)
            }
        )
    }
}
