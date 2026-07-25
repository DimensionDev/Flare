@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitTextView(
    payload: FlareTextPayload,
    children: [FlareUiNodeSnapshot]
) -> NSView {
    NSTextField(wrappingLabelWithString: payload.value)
}
