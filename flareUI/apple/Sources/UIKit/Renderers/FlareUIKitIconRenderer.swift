#if canImport(UIKit)
import FlareUIRuntime
import UIKit

@MainActor
func makeFlareUIKitIconView(
    payload: FlareUIIconPayload,
    children: [FlareUINode],
    resources: FlareAppleResources
) -> UIView {
    let image =
        UIImage(
            named: resources.imageName(payload.image),
            in: resources.bundle(for: payload.image.key.resourceNamespace),
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
#endif
