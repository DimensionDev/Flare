@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitTextView(
    payload: FlareTextPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> UIView {
    let label = UILabel()
    label.numberOfLines = 0
    label.textAlignment = .natural
    label.text = resources.string(payload.value)
    return label
}
