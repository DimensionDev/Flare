#if canImport(AppKit)
import FlareUIRuntime
import AppKit

@MainActor
func makeFlareAppKitIconView(
    payload: FlareUIIconPayload,
    children: [FlareUINode],
    resources: FlareAppleResources
) -> NSView {
    let imageView = NSImageView()
    imageView.image =
        resources.bundle(for: payload.image.key.resourceNamespace)
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
#endif
