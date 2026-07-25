@preconcurrency import FlareUIDemoKit
import AppKit

func makeFlareAppKitTextView(
    payload: FlareTextPayload,
    children: [FlareUiNodeSnapshot],
    resources: FlareAppleResources
) -> NSView {
    NSTextField(
        wrappingLabelWithString: resources.string(payload.value)
    )
}
