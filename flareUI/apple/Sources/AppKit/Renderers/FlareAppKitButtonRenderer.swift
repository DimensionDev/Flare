@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitButtonView(
    payload: FlareButtonPayload,
    children: [FlareUiNodeSnapshot]
) -> NSView {
    let button = FlareAppKitButton(
        title: payload.label,
        onClick: {
            payload.performClick()
        }
    )
    button.isEnabled = payload.enabled
    return button
}

private final class FlareAppKitButton: NSButton {
    private let onClick: () -> Void

    init(
        title: String,
        onClick: @escaping () -> Void
    ) {
        self.onClick = onClick
        super.init(frame: .zero)
        self.title = title
        bezelStyle = .rounded
        setButtonType(.momentaryPushIn)
        target = self
        action = #selector(handleClick)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("Use init(title:onClick:) instead")
    }

    @objc
    private func handleClick() {
        onClick()
    }
}
