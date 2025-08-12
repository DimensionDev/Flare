import shared
import SwiftUI
import UIKit

class MediaCollectionViewCell: UICollectionViewCell {
    private var hostingController: UIHostingController<ProfileMediaItemView>?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
    }

    @available(*, unavailable)
    required init?(coder _: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        hostingController?.view.removeFromSuperview()
        hostingController = nil
    }

    func configure(with media: UiMedia, appSettings: AppSettings, onTap: @escaping () -> Void) {
   
        let mediaView = ProfileMediaItemView(
            media: media,
            appSetting: appSettings,
            onTap: onTap
        )

        
        hostingController?.view.removeFromSuperview()
        hostingController = nil

         
        let controller = UIHostingController(rootView: mediaView)
        hostingController = controller

   
        controller.view.backgroundColor = .clear
        contentView.addSubview(controller.view)
        controller.view.translatesAutoresizingMaskIntoConstraints = false

        NSLayoutConstraint.activate([
            controller.view.topAnchor.constraint(equalTo: contentView.topAnchor),
            controller.view.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            controller.view.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            controller.view.bottomAnchor.constraint(equalTo: contentView.bottomAnchor)
        ])
    }
}
