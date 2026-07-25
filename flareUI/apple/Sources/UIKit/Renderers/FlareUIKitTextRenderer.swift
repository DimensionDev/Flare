#if canImport(UIKit)
import FlareUIRuntime
import UIKit

@MainActor
func makeFlareUIKitTextView(
    payload: FlareUITextPayload,
    children: [FlareUINode],
    resources: FlareAppleResources
) -> UIView {
    let label = UILabel()
    label.numberOfLines = 0
    label.textAlignment = .natural
    label.text = resources.string(payload.value)
    return label
}
#endif
