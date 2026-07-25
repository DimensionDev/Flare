@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitIconView(
    payload: FlareIconPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> UIView {
    let image =
        UIImage(
            named: resources.imageName(payload.image),
            in: resources.bundle(for: payload.image.key.namespace_),
            compatibleWith: nil
        )?
        .withRenderingMode(.alwaysTemplate)
    let imageView = UIImageView(image: image)
    imageView.tintColor = .label
    imageView.contentMode = .scaleAspectFit

    if let description = payload.contentDescription {
        imageView.isAccessibilityElement = true
        imageView.accessibilityLabel = resources.string(description)
    } else {
        imageView.isAccessibilityElement = false
    }
    return imageView
}
