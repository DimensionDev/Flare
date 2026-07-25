#if canImport(AppKit)
import FlareUIRuntime
import AppKit

@MainActor
func makeFlareAppKitButtonView(
    payload: FlareUIButtonPayload,
    children: [FlareUINode],
    resources: FlareAppleResources
) -> NSView {
    let button = FlareAppKitButton(
        title: resources.string(payload.label),
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
#endif
