@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitIconView(
    payload: FlareIconPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> NSView {
    let imageView = NSImageView()
    imageView.image =
        resources.bundle(for: payload.image.key.namespace_)
            .image(
                forResource: NSImage.Name(
                    resources.imageName(payload.image)
                )
            )
    imageView.image?.isTemplate = true
    imageView.imageScaling = .scaleProportionallyDown

    if let description = payload.contentDescription {
        imageView.setAccessibilityElement(true)
        imageView.setAccessibilityLabel(resources.string(description))
    } else {
        imageView.setAccessibilityElement(false)
    }
    return imageView
}
