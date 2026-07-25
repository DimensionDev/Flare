@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitButtonView(
    payload: FlareButtonPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> UIView {
    let button = FlareUIKitButton(type: .system)
    button.setTitle(resources.string(payload.label), for: .normal)
    button.isEnabled = payload.enabled
    button.addAction(
        UIAction { _ in
            payload.performClick()
        },
        for: .touchUpInside
    )
    return button
}

/// `UIButton` on recent iOS SDKs includes style-dependent padding in its
/// intrinsic size. Flare rows follow Compose's zero-spacing layout contract,
/// so that padding must not become spacing between sibling widgets.
private final class FlareUIKitButton: UIButton {
    override var intrinsicContentSize: CGSize {
        titleLabel?.intrinsicContentSize ?? super.intrinsicContentSize
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        titleLabel?.sizeThatFits(size) ?? super.sizeThatFits(size)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        titleLabel?.frame = bounds
    }
}
