import SwiftUI
import UIKit
import XCTest
import FlareAppleUI

@MainActor
final class FlareTextColorTests: XCTestCase {
    func testOnlyOpaqueBlackInStandardDarkAppearanceUsesSoftPrimary() {
        for style in [UIUserInterfaceStyle.light, .dark] {
            for contrast in [UIAccessibilityContrast.normal, .high] {
                for background: UIColor? in [nil, .black, .white, .darkGray,
                                             UIColor(white: 0.001, alpha: 1), .black.withAlphaComponent(0.9)] {
                    let traits = UITraitCollection { traits in
                        traits.userInterfaceStyle = style
                        traits.accessibilityContrast = contrast
                        traits[FlareTextBackgroundTrait.self] = background
                    }
                    let expected: UIColor = style == .dark && contrast == .normal && background == .black
                        ? UIColor(white: 224.0 / 255, alpha: 1) : .label
                    assertColor(.flareLabel, equals: expected, traits: traits)
                }
            }
        }
    }

    func testSystemBackgroundResolvesAtTheCurrentPresentationLevel() {
        for level in [UIUserInterfaceLevel.base, .elevated] {
            let traits = UITraitCollection { traits in
                traits.userInterfaceStyle = .dark
                traits.userInterfaceLevel = level
                traits[FlareTextBackgroundTrait.self] = .systemBackground
            }
            let background = rgba(.systemBackground, traits: traits)
            let expected: UIColor = background == [0, 0, 0, 1] ? UIColor(white: 224.0 / 255, alpha: 1) : .label
            assertColor(.flareLabel, equals: expected, traits: traits)
        }
    }

    func testUIKitInheritsSurfaceAndRestoresPrimaryWhenReusedOnGray() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        let label = UILabel(frame: root.view.bounds)
        label.text = "MMMM"
        label.font = .systemFont(ofSize: 36, weight: .black)
        label.textColor = .flareLabel
        root.view.addSubview(label)
        for background: UIColor in [.black, .darkGray, .black] {
            label.backgroundColor = background
            root.view.backgroundColor = background
            root.view.traitOverrides[FlareTextBackgroundTrait.self] = background
            await settle(root.view)
            let expected: UIColor = background == .black ? UIColor(white: 224.0 / 255, alpha: 1) : .label
            assertColor(label.textColor, equals: expected, traits: label.traitCollection)
            try assertRenderedText(label, background: background, equals: expected)
        }
    }

    func testSwiftUIPrimaryChangesWhileSecondaryAndExplicitColorsStayTheSame() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        for background: UIColor in [.black, .darkGray] {
            let expected: UIColor = background == .black ? UIColor(white: 224.0 / 255, alpha: 1) : .label
            let primary = Text("MMMM").flarePrimaryText().flareTextBackground(Color(uiColor: background))
            try await assertSwiftUIText(primary, in: root, background: background, equals: expected)
            try await assertSwiftUIText(
                Text("MMMM").foregroundStyle(Color(uiColor: .flareLabel)).flareTextBackground(Color(uiColor: background)),
                in: root, background: background, equals: expected
            )
            for color in [Color.secondary, .red] {
                let plain = try await snapshot(Text("MMMM").foregroundStyle(color), in: root, background: background)
                let softened = try await snapshot(
                    Text("MMMM").foregroundStyle(color).flareTextBackground(Color(uiColor: background)),
                    in: root, background: background
                )
                XCTAssertEqual(try dominantRGB(plain, background: background), try dominantRGB(softened, background: background))
            }
            let plainSecondary = try await snapshot(Text("MMMM").foregroundStyle(.secondary), in: root, background: background)
            let softenedSecondary = try await snapshot(
                Text("MMMM").flarePrimaryText().flareSecondaryText().flareTextBackground(Color(uiColor: background)),
                in: root, background: background
            )
            XCTAssertEqual(try dominantRGB(plainSecondary, background: background),
                           try dominantRGB(softenedSecondary, background: background))
        }
    }

    func testSwiftUICardOverridesBlackParent() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        try await assertSwiftUIText(
            Text("MMMM").flarePrimaryText().flareTextBackground(.gray).flareTextBackground(.black),
            in: root, background: .gray, equals: .label
        )
        try await assertSwiftUIText(
            Text("MMMM").flarePrimaryText().flareTextBackground(nil).flareTextBackground(.black),
            in: root, background: .darkGray, equals: .label
        )
    }

    func testSwiftUITertiaryAndLightModeKeepSystemColors() async throws {
        let (window, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        for style in [UIUserInterfaceStyle.light, .dark] {
            window.overrideUserInterfaceStyle = style
            let original = try await snapshot(Text("MMMM").foregroundStyle(.tertiary), in: root, background: .black)
            let adjusted = try await snapshot(
                Text("MMMM").flarePrimaryText().flareTertiaryText().flareTextBackground(.black),
                in: root, background: .black
            )
            XCTAssertEqual(try dominantRGB(original, background: .black), try dominantRGB(adjusted, background: .black))
            if style == .light {
                try await assertSwiftUIText(Text("MMMM").flarePrimaryText().flareTextBackground(.black), in: root, background: .white, equals: .label)
            }
        }
    }

    func testSurfaceBridgesInBothDirections() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        try await assertSwiftUIText(
            NativeLabel().flareTextBackground(.black), in: root, background: .black,
            equals: UIColor(white: 224.0 / 255, alpha: 1)
        )
        root.view.traitOverrides[FlareTextBackgroundTrait.self] = .black
        try await assertSwiftUIText(
            Text("MMMM").flarePrimaryText(), in: root, background: .black,
            equals: UIColor(white: 224.0 / 255, alpha: 1)
        )
    }

    func testSwiftUIRespectsElevatedBackgroundAndIncreasedContrast() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        root.view.traitOverrides.userInterfaceLevel = .elevated
        try await assertSwiftUIText(
            Text("MMMM").flarePrimaryText().flareTextBackground(Color(uiColor: .systemBackground)),
            in: root, background: .darkGray, equals: .label
        )
        root.view.traitOverrides.userInterfaceLevel = .base
        root.view.traitOverrides.accessibilityContrast = .high
        try await assertSwiftUIText(
            Text("MMMM").flarePrimaryText().flareTextBackground(.black), in: root, background: .black, equals: .label
        )
    }

    func testNativeControlsAndInheritedColorsKeepTheirForegroundOnEverySurface() async throws {
        let (window, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        for style in [UIUserInterfaceStyle.light, .dark] {
            window.overrideUserInterfaceStyle = style
            for background: UIColor in [.black, .darkGray] {
                let button = Button("MMMM", action: {})
                let before = try await snapshot(button, in: root, background: background)
                let after = try await snapshot(button.flareTextBackground(Color(uiColor: background)), in: root, background: background)
                XCTAssertEqual(try dominantRGB(before, background: background), try dominantRGB(after, background: background))
                let prominent = button.buttonStyle(.borderedProminent)
                let original = try await snapshot(prominent, in: root, background: background)
                let adjusted = try await snapshot(prominent.flareTextBackground(Color(uiColor: background)), in: root, background: background)
                XCTAssertGreaterThan(try whitePixels(original), 100)
                XCTAssertEqual(try whitePixels(original), try whitePixels(adjusted), accuracy: 3)
                for inherited in [
                    AnyView(Text("MMMM").flareTextBackground(Color(uiColor: background)).foregroundStyle(.red)),
                    AnyView(Text("MMMM").flareTextBackground(Color(uiColor: background)).foregroundColor(.red))
                ] {
                    try await assertSwiftUIText(inherited, in: root, background: background, equals: .systemRed)
                }
            }
        }
    }

    func testOnlyPrimaryTextChangesBesideAnUnstyledSymbol() async throws {
        let (_, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        let view = HStack(spacing: 0) {
            Image(systemName: "square.fill").resizable().frame(width: 32, height: 32).frame(width: 60)
            Text("MMMM").flarePrimaryText().frame(width: 160)
        }
        .flareTextBackground(.black)
        let image = try await snapshot(view, in: root, background: .black)
        XCTAssertEqual(try dominantRGB(image, background: .black, columns: 0..<60), [255, 255, 255])
        XCTAssertEqual(try dominantRGB(image, background: .black, columns: 60..<220), [224, 224, 224])
    }

    func testThemeChangesPreserveDraftIdentityAndFocus() async throws {
        let (window, root, restoreWindow) = try makeWindow()
        defer { restoreWindow() }
        var identities: [UUID] = []
        let host = UIHostingController(rootView: DraftIdentityProbe(onAppear: { identities.append($0) })
            .flareTextBackground(Color(uiColor: .systemBackground)))
        root.addChild(host)
        host.view.frame = CGRect(x: 0, y: 0, width: 220, height: 60)
        root.view.addSubview(host.view)
        host.didMove(toParent: root)
        await settle(host.view)
        let field = try XCTUnwrap(descendants(host.view).compactMap { $0 as? UITextField }.first)
        XCTAssertTrue(field.becomeFirstResponder())
        defer { field.resignFirstResponder() }
        field.text = "Keep this draft"
        field.sendActions(for: .editingChanged)
        for style in [UIUserInterfaceStyle.light, .dark, .light] {
            window.overrideUserInterfaceStyle = style
            await settle(host.view)
            XCTAssertTrue(descendants(host.view).contains { $0 === field })
            XCTAssertTrue(field.isFirstResponder)
            XCTAssertEqual(field.text, "Keep this draft")
        }
        XCTAssertEqual(Set(identities).count, 1)
    }

    private func descendants(_ view: UIView) -> [UIView] {
        [view] + view.subviews.flatMap { descendants($0) }
    }

    private func whitePixels(_ image: UIImage) throws -> Int {
        let cg = try XCTUnwrap(image.cgImage)
        var pixels = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let context = try XCTUnwrap(CGContext(data: &pixels, width: cg.width, height: cg.height,
                                            bitsPerComponent: 8, bytesPerRow: cg.width * 4,
                                            space: CGColorSpaceCreateDeviceRGB(),
                                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue |
                                                CGBitmapInfo.byteOrder32Big.rawValue))
        context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        return stride(from: 0, to: pixels.count, by: 4).filter {
            pixels[$0] == 255 && pixels[$0 + 1] == 255 && pixels[$0 + 2] == 255
        }.count
    }

    private func makeWindow() throws -> (UIWindow, UIViewController, () -> Void) {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        root.view.frame = CGRect(x: 0, y: 0, width: 220, height: 60)
        window.rootViewController = root
        window.overrideUserInterfaceStyle = .dark
        window.makeKeyAndVisible()
        return (window, root, {
            window.isHidden = true
            window.rootViewController = nil
            previousWindow?.makeKeyAndVisible()
        })
    }

    private func assertColor(_ actual: UIColor, equals expected: UIColor, traits: UITraitCollection,
                             file: StaticString = #filePath, line: UInt = #line) {
        let actual = rgba(actual, traits: traits), expected = rgba(expected, traits: traits)
        for channel in 0..<4 {
            XCTAssertEqual(actual[channel], expected[channel], accuracy: 0.0001, file: file, line: line)
        }
    }

    private func rgba(_ color: UIColor, traits: UITraitCollection) -> [CGFloat] {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        XCTAssertTrue(color.resolvedColor(with: traits).getRed(&r, green: &g, blue: &b, alpha: &a))
        return [r, g, b, a]
    }

    private func assertSwiftUIText(_ view: some View, in root: UIViewController,
                                   background: UIColor, equals expected: UIColor) async throws {
        let image = try await snapshot(view, in: root, background: background)
        let actual = try dominantRGB(image, background: background)
        let expected = rgba(expected, traits: root.traitCollection).prefix(3).map { Int(($0 * 255).rounded()) }
        for channel in 0..<3 { XCTAssertEqual(actual[channel], expected[channel], accuracy: 2) }
    }

    private func snapshot(_ view: some View, in root: UIViewController, background: UIColor) async throws -> UIImage {
        let host = UIHostingController(rootView: view.font(.system(size: 36, weight: .black)))
        host.safeAreaRegions = []
        root.addChild(host)
        host.view.frame = CGRect(x: 0, y: 0, width: 220, height: 60)
        host.view.backgroundColor = background
        root.view.addSubview(host.view)
        host.didMove(toParent: root)
        await settle(host.view)
        let image = render(host.view)
        host.willMove(toParent: nil)
        host.view.removeFromSuperview()
        host.removeFromParent()
        return image
    }

    private func settle(_ view: UIView) async {
        view.layoutIfNeeded()
        try? await Task.sleep(for: .milliseconds(50))
        view.layoutIfNeeded()
    }

    private func assertRenderedText(_ view: UIView, background: UIColor, equals expected: UIColor) throws {
        let actual = try dominantRGB(render(view), background: background)
        let expected = rgba(expected, traits: view.traitCollection).prefix(3).map { Int(($0 * 255).rounded()) }
        for channel in 0..<3 { XCTAssertEqual(actual[channel], expected[channel], accuracy: 2) }
    }

    private func render(_ view: UIView) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
    }

    private func dominantRGB(_ image: UIImage, background: UIColor, columns: Range<Int>? = nil) throws -> [Int] {
        let cg = try XCTUnwrap(image.cgImage)
        var pixels = [UInt8](repeating: 0, count: cg.width * cg.height * 4)
        let context = try XCTUnwrap(CGContext(data: &pixels, width: cg.width, height: cg.height,
                                            bitsPerComponent: 8, bytesPerRow: cg.width * 4,
                                            space: CGColorSpaceCreateDeviceRGB(),
                                            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue |
                                                CGBitmapInfo.byteOrder32Big.rawValue))
        context.draw(cg, in: CGRect(x: 0, y: 0, width: cg.width, height: cg.height))
        let bg = rgba(background, traits: UITraitCollection(userInterfaceStyle: .dark)).prefix(3).map { Int(($0 * 255).rounded()) }
        var counts: [Int: Int] = [:]
        for offset in stride(from: 0, to: pixels.count, by: 4) {
            if let columns, !columns.contains((offset / 4) % cg.width) { continue }
            let rgb = [Int(pixels[offset]), Int(pixels[offset + 1]), Int(pixels[offset + 2])]
            guard (0..<3).contains(where: { abs(rgb[$0] - bg[$0]) > 4 }) else { continue }
            counts[(rgb[0] << 16) | (rgb[1] << 8) | rgb[2], default: 0] += 1
        }
        let rgb = try XCTUnwrap(counts.max(by: { $0.value < $1.value })?.key)
        return [(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255]
    }
}

private struct NativeLabel: UIViewRepresentable {
    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.text = "MMMM"
        label.font = .systemFont(ofSize: 36, weight: .black)
        label.textColor = .flareLabel
        return label
    }
    func updateUIView(_ uiView: UILabel, context: Context) {}
}

private struct DraftIdentityProbe: View {
    @State private var identity = UUID()
    @State private var draft = "Initial draft"
    let onAppear: (UUID) -> Void

    var body: some View {
        TextField("", text: $draft)
            .foregroundStyle(FlarePrimaryTextStyle())
            .onAppear { onAppear(identity) }
    }
}
