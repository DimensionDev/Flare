import shared
import SwiftUI
import UIKit

class HomeNewTimelineCell: UITableViewCell {
    private var hostingController: UIHostingController<StatusItemView>?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupUI()
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupUI() {
        // 禁用 cell 选中效果
        selectionStyle = .none
        backgroundColor = .clear
        // 添加内边距容器视图
        contentView.layoutMargins = UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
    }

    func configure(with item: UiTimeline) {
        // 移除旧的视图
        hostingController?.view.removeFromSuperview()
        hostingController = nil

        // 创建新的 StatusItemView
        let statusView = StatusItemView(data: item, detailKey: nil, enableTranslation: false)
        let hostingController = UIHostingController(rootView: statusView)
        self.hostingController = hostingController

        // 添加到 contentView
        contentView.addSubview(hostingController.view)
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: contentView.layoutMarginsGuide.topAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.trailingAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: contentView.layoutMarginsGuide.bottomAnchor),
        ])

        // 设置背景色和选中样式
        backgroundColor = .clear
        selectionStyle = .none
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        hostingController?.view.removeFromSuperview()
        hostingController = nil
    }

    override func setSelected(_ selected: Bool, animated: Bool) {
        super.setSelected(selected, animated: animated)
        // 保持禁用状态
        selectionStyle = .none
    }
}

extension UIView {
    var parentViewController: UIViewController? {
        var parentResponder: UIResponder? = self
        while parentResponder != nil {
            parentResponder = parentResponder?.next
            if let viewController = parentResponder as? UIViewController {
                return viewController
            }
        }
        return nil
    }
}
