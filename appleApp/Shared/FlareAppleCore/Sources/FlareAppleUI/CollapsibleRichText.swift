import SwiftUI
import FlareAppleCore
import KotlinSharedUI

struct CollapsibleRichText: View {
    let text: UiRichText
    let lineLimit: Int?
    let collapseThreshold: Int
    let isExpanded: Bool
    let isTextSelectionEnabled: Bool
    let onOverflowChanged: (Bool) -> Void

    @ScaledMetric(relativeTo: .body) private var baseFallbackLineHeight: CGFloat = 20
    @Environment(\.fontScale) private var fontScale
    private var fallbackLineHeight: CGFloat {
        baseFallbackLineHeight * fontScale
    }
    @State private var fullHeight: CGFloat = 0
    @State private var lineHeight: CGFloat = 0

    private var effectiveLineHeight: CGFloat {
        max(lineHeight, fallbackLineHeight)
    }

    private var overflows: Bool {
        fullHeight > ceil(effectiveLineHeight * CGFloat(max(collapseThreshold, 1))) + 1
    }

    private var showsPreview: Bool {
        lineLimit != nil && !isExpanded && (fullHeight == 0 || overflows)
    }

    var body: some View {
        richText(previewLineLimit: showsPreview ? lineLimit : nil)
            .fixedSize(horizontal: false, vertical: true)
            .onGeometryChange(for: CGFloat.self) { proxy in
                proxy.size.height
            } action: { value in
                if !showsPreview {
                    fullHeight = value
                    publishOverflow(fullHeight: value, lineHeight: lineHeight)
                }
            }
            .background(alignment: .topLeading) {
                if showsPreview {
                    richText(previewLineLimit: nil)
                        .fixedSize(horizontal: false, vertical: true)
                        .hidden()
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { value in
                            fullHeight = value
                            publishOverflow(fullHeight: value, lineHeight: lineHeight)
                        }
                }
            }
            .overlay(alignment: .topLeading) {
                Text(verbatim: "A")
                    .fixedSize()
                    .hidden()
                    .onGeometryChange(for: CGFloat.self) { proxy in
                        proxy.size.height
                    } action: { value in
                        lineHeight = value
                        publishOverflow(fullHeight: fullHeight, lineHeight: value)
                    }
                    .allowsHitTesting(false)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .onChange(of: lineLimit) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
            .onChange(of: collapseThreshold) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
            .onChange(of: isExpanded) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
            .onChange(of: text.raw) { _, _ in
                publishOverflow(fullHeight: fullHeight, lineHeight: lineHeight)
            }
    }

    @ViewBuilder
    private func richText(previewLineLimit: Int?) -> some View {
        if isTextSelectionEnabled {
            RichText(text: text, previewLineLimit: previewLineLimit, previewLineHeight: effectiveLineHeight)
                .textSelection(.enabled)
        } else {
            RichText(text: text, previewLineLimit: previewLineLimit, previewLineHeight: effectiveLineHeight)
        }
    }

    private func publishOverflow(fullHeight: CGFloat, lineHeight: CGFloat) {
        guard lineLimit != nil, !isExpanded else {
            onOverflowChanged(false)
            return
        }
        let thresholdHeight = ceil(max(lineHeight, fallbackLineHeight) * CGFloat(max(collapseThreshold, 1)))
        onOverflowChanged(fullHeight > thresholdHeight + 1)
    }
}
