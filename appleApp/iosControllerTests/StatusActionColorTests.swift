import XCTest
import SwiftUI
import KotlinSharedUI
import FlareAppleUI
@testable import Flare

@MainActor
final class StatusActionColorTests: XCTestCase {
    func testActionsAndGroupLabelsFollowAndroidColorsAndDisabledOpacity() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.makeKeyAndVisible()
        defer {
            window.isHidden = true
            previousWindow?.makeKeyAndVisible()
        }
        let colors: [ActionMenu.ItemColor?] = [nil, .contentColor, .primaryColor, .red]
        let uiView = StatusActionsUIView()

        for theme in [UIUserInterfaceStyle.light, .dark] {
            window.overrideUserInterfaceStyle = theme
            for isDetail in [false, true] {
                for isGroup in [false, true] {
                    for enabled in [true, false] {
                        for color in colors {
                            let item = ActionMenu.Item(updateKey: "color", icon: .unlike, text: nil,
                                                       count: UiNumber(value: 888), color: color,
                                                       clickEvent: ClickEventNoop.shared,
                                                       actionFamily: .like, enabled: enabled)
                            let action: ActionMenu = isGroup ? ActionMenu.Group(displayItem: item, actions: [item]) : item
                            let host = UIHostingController(rootView:
                                StatusActionsView(data: [action], useText: false, applyPostActionLayout: false, isDetail: isDetail)
                                    .font(isDetail ? .body : .footnote)
                                    .foregroundStyle(isDetail ? .primary : .secondary)
                                    .environment(\.colorScheme, theme == .dark ? .dark : .light)
                            )
                            host.safeAreaRegions = []
                            root.addChild(host)
                            let swiftImage = await snapshot(host.view, in: root, theme: theme)
                            host.removeFromParent()
                            uiView.configure(data: [action], useText: false, postActionStyle: .leftAligned,
                                             applyPostActionLayout: false, showNumbers: true, isDetail: isDetail)
                            let uiImage = await snapshot(uiView, in: root, theme: theme)
                            let expected = expectedRGB(color: color, isDetail: isDetail, enabled: enabled, theme: theme)
                            let context = "theme=\(theme.rawValue), detail=\(isDetail), group=\(isGroup), enabled=\(enabled), color=\(String(describing: color))"
                            for (renderer, image) in [("SwiftUI", swiftImage), ("UIKit", uiImage)] {
                                let actual = try dominantRGB(image, theme: theme)
                                for channel in 0..<3 {
                                    XCTAssertEqual(actual[channel], expected[channel], accuracy: 3,
                                                   "\(renderer): \(context), RGB=\(actual), expected=\(expected)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    func testButtonPositionsMatchAcrossAlignmentFontWidthAndCountSettings() async throws {
        try await verifyPositions(rightToLeft: false)
    }

    func testRightToLeftButtonPositionsMatch() async throws {
        try await verifyPositions(rightToLeft: true)
    }

    private func verifyPositions(rightToLeft: Bool) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.overrideUserInterfaceStyle = .light
        window.makeKeyAndVisible()
        defer { window.isHidden = true; previous?.makeKeyAndVisible() }
        let make: (UiIcon, Int64?, ActionMenu.ItemColor?) -> ActionMenu.Item = { icon, count, color in
            ActionMenu.Item(updateKey: "layout", icon: icon, text: nil, count: count.map { UiNumber(value: $0) },
                            color: color, clickEvent: ClickEventNoop.shared, actionFamily: nil, enabled: true)
        }
        let more = make(.more, nil, nil)
        let data: [ActionMenu] = [make(.reply, 12, nil), make(.retweet, 24, .primaryColor),
                                 make(.unlike, 128, .red), make(.bookmark, 0, nil),
                                 ActionMenu.Group(displayItem: more, actions: [make(.like, 1, nil)])]
        let native = StatusActionsUIView()
        native.semanticContentAttribute = rightToLeft ? .forceRightToLeft : .forceLeftToRight
        for style in [PostActionStyle.leftAligned, .rightAligned, .stretch] {
            for detail in [false, true] {
                for width: CGFloat in [295, 362] {
                    for fixed in [false, true] {
                        for numbers in [false, true] {
                            let host = UIHostingController(rootView:
                                StatusActionsView(data: data, useText: false, applyPostActionLayout: false, isDetail: detail)
                                    .environment(\.timelineAppearance, appearance(style: style, fixed: fixed, numbers: numbers))
                                    .environment(\.colorScheme, .light)
                                    .environment(\.layoutDirection, rightToLeft ? .rightToLeft : .leftToRight)
                            )
                            host.safeAreaRegions = []
                            root.addChild(host)
                            let swift = await snapshot(host.view, in: root, theme: .light, width: width)
                            host.removeFromParent()
                            native.configure(data: data, useText: false, postActionStyle: style,
                                             postActionFixedWidth: fixed, applyPostActionLayout: false,
                                             showNumbers: numbers, isDetail: detail)
                            let ui = await snapshot(native, in: root, theme: .light, width: width)
                            for green in [false, true] {
                                let a = try coloredBounds(swift, green: green)
                                let b = try coloredBounds(ui, green: green)
                                let message = "style=\(style), detail=\(detail), width=\(width), fixed=\(fixed), numbers=\(numbers), green=\(green)"
                                XCTAssertEqual(a.minX, b.minX, accuracy: 1.1, message)
                                XCTAssertEqual(a.maxX, b.maxX, accuracy: 1.1, message)
                            }
                        }
                    }
                }
            }
        }
    }

    private func appearance(style: PostActionStyle, fixed: Bool, numbers: Bool) -> TimelineAppearance {
        let base = TimelineAppearance.companion.Default
        return base.doCopy(
            avatarShape: base.avatarShape,
            showMedia: base.showMedia,
            showSensitiveContent: base.showSensitiveContent,
            expandContentWarning: base.expandContentWarning,
            expandMediaSize: base.expandMediaSize,
            limitSingleMediaHeight: base.limitSingleMediaHeight,
            mediaLayout: base.mediaLayout,
            limitMediaGridToNine: base.limitMediaGridToNine,
            videoAutoplay: base.videoAutoplay,
            showLinkPreview: base.showLinkPreview,
            compatLinkPreview: base.compatLinkPreview,
            showEmojiReactions: base.showEmojiReactions,
            showNumbers: numbers,
            postActionStyle: style,
            postActionLayout: base.postActionLayout,
            postActionFixedWidth: fixed,
            fullWidthPost: base.fullWidthPost,
            absoluteTimestamp: base.absoluteTimestamp,
            showPlatformLogo: base.showPlatformLogo,
            timelineDisplayMode: base.timelineDisplayMode,
            aiConfig: base.aiConfig,
            lineLimit: base.lineLimit,
            showTranslateButton: base.showTranslateButton
        )
    }

    private func coloredBounds(_ image: UIImage, green: Bool) throws -> CGRect {
        let cg = try XCTUnwrap(image.cgImage)
        var pixels = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let context = try XCTUnwrap(CGContext(data: &pixels, width: cg.width, height: cg.height,
                                            bitsPerComponent: 8, bytesPerRow: cg.width * 4,
                                            space: CGColorSpaceCreateDeviceRGB(),
                                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue |
                                                CGBitmapInfo.byteOrder32Big.rawValue))
        context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        var minX = cg.width, maxX = -1
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            let r = Int(pixels[offset]), g = Int(pixels[offset + 1]), b = Int(pixels[offset + 2])
            if green ? (g > r + 25 && g > b + 10) : (r > g + 40 && r > b + 40) {
                let x = (offset / 4) % cg.width
                minX = min(minX, x); maxX = max(maxX, x)
            }
        }
        XCTAssertGreaterThanOrEqual(maxX, minX)
        return CGRect(x: minX, y: 0, width: maxX - minX + 1, height: cg.height)
    }

    private func snapshot(_ view: UIView, in root: UIViewController, theme: UIUserInterfaceStyle, width: CGFloat = 240) async -> UIImage {
        view.frame = CGRect(x: 0, y: 0, width: width, height: 48)
        view.backgroundColor = theme == .dark ? .black : .white
        root.view.addSubview(view)
        view.setNeedsLayout()
        view.layoutIfNeeded()
        try? await Task.sleep(for: .milliseconds(60))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let image = UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        view.removeFromSuperview()
        return image
    }

    private func expectedRGB(color: ActionMenu.ItemColor?, isDetail: Bool, enabled: Bool,
                             theme: UIUserInterfaceStyle) -> [CGFloat] {
        let base: UIColor
        switch color {
        case .red: base = .systemRed
        case .primaryColor: base = UIColor(red: 0, green: 186.0 / 255, blue: 124.0 / 255, alpha: 1)
        default: base = isDetail ? .label : .secondaryLabel
        }
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        base.resolvedColor(with: UITraitCollection(userInterfaceStyle: theme))
            .getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        let opacity = alpha * (enabled ? 1 : 0.4)
        let background: CGFloat = theme == .dark ? 0 : 1
        return [red, green, blue].map { ($0 * opacity + background * (1 - opacity)) * 255 }
    }

    private func dominantRGB(_ image: UIImage, theme: UIUserInterfaceStyle) throws -> [CGFloat] {
        let cgImage = try XCTUnwrap(image.cgImage)
        let width = cgImage.width, height = cgImage.height
        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        let context = try XCTUnwrap(CGContext(data: &pixels, width: width, height: height,
                                            bitsPerComponent: 8, bytesPerRow: width * 4,
                                            space: CGColorSpaceCreateDeviceRGB(),
                                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue |
                                                CGBitmapInfo.byteOrder32Big.rawValue))
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        let background = theme == .dark ? 0 : 255
        var counts: [Int: Int] = [:]
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            let rgb = [Int(pixels[offset]), Int(pixels[offset + 1]), Int(pixels[offset + 2])]
            guard rgb.contains(where: { abs($0 - background) > 3 }) else { continue }
            counts[(rgb[0] << 16) | (rgb[1] << 8) | rgb[2], default: 0] += 1
        }
        let color = try XCTUnwrap(counts.max(by: { $0.value < $1.value })?.key)
        return [CGFloat((color >> 16) & 255), CGFloat((color >> 8) & 255), CGFloat(color & 255)]
    }
}
