@preconcurrency import FlareUIDemoKit
import UIKit

func makeFlareUIKitTextView(
    payload: FlareTextPayload,
    children: [FlareUiNodeSnapshot]
) -> UIView {
    let label = UILabel()
    label.numberOfLines = 0
    label.textAlignment = .natural
    label.text = payload.value
    return label
}
