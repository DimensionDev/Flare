import Combine
import LazyPager
import SwiftUI
import UIKit
import XCTest

@MainActor
final class MediaViewerDismissRenderingTests: XCTestCase {
    func testLandscapeImageFollowsFingerAndReturnsAfterCancellation() async throws {
        let size = CGSize(width: 400, height: 800)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let image = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 300), format: format).image { context in
            UIColor.darkGray.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 400, height: 300))
            UIColor.green.setFill()
            context.fill(CGRect(x: 96, y: 46, width: 8, height: 8))
        }
        let state = DismissState()
        let hosting = UIHostingController(rootView: TestPage(state: state, image: image, size: size))
        hosting.safeAreaRegions = []
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previousKeyWindow = scene.windows.first(where: \.isKeyWindow)
        let window = UIWindow(windowScene: scene)
        let root = UIViewController()
        window.rootViewController = root
        window.makeKeyAndVisible()
        root.addChild(hosting)
        root.view.addSubview(hosting.view)
        hosting.view.frame = CGRect(origin: .zero, size: size)
        hosting.didMove(toParent: root)
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previousKeyWindow?.makeKey()
        }
        try await settle(window)
        let before = try markerCenter(in: hosting.view)
        XCTAssertEqual(before.x, 100, accuracy: 1)
        XCTAssertEqual(before.y, 300, accuracy: 1)

        state.transform = MediaViewerDismissGesturePolicy.transform(
            translation: CGPoint(x: -30, y: 200),
            startLocation: CGPoint(x: 100, y: 300),
            containerSize: size
        )
        try await settle(window)
        let dragged = try markerCenter(in: hosting.view)
        XCTAssertEqual(dragged.x, 70, accuracy: 1)
        XCTAssertEqual(dragged.y, 500, accuracy: 1)

        await withCheckedContinuation { continuation in
            withAnimation(.spring(response: 0.25, dampingFraction: 0.9)) {
                state.transform.offset = .zero
                state.transform.scale = 1
            } completion: {
                continuation.resume()
            }
        }
        try await settle(window)
        let restored = try markerCenter(in: hosting.view)
        XCTAssertEqual(restored.x, 100, accuracy: 1)
        XCTAssertEqual(restored.y, 300, accuracy: 1)
    }

    private func settle(_ window: UIWindow) async throws {
        window.layoutIfNeeded()
        try await Task.sleep(for: .milliseconds(250))
        window.layoutIfNeeded()
    }

    private func markerCenter(in view: UIView) throws -> CGPoint {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let snapshot = UIGraphicsImageRenderer(size: view.bounds.size, format: format).image { _ in
            view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
        }
        let attachment = XCTAttachment(image: snapshot)
        add(attachment)
        let cgImage = try XCTUnwrap(snapshot.cgImage)
        let width = cgImage.width
        let height = cgImage.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        try bytes.withUnsafeMutableBytes { raw in
            let context = try XCTUnwrap(CGContext(
                data: raw.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
            ))
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        }
        var sum = CGPoint.zero
        var count: CGFloat = 0
        for y in 0..<height {
            for x in 0..<width {
                let index = (y * width + x) * 4
                if bytes[index] < 40 && bytes[index + 1] > 200 && bytes[index + 2] < 40 {
                    sum.x += CGFloat(x) + 0.5
                    sum.y += CGFloat(y) + 0.5
                    count += 1
                }
            }
        }
        let matchedPixels = try XCTUnwrap(count > 10 ? count : nil, "The rendered image marker must be visible")
        return CGPoint(x: sum.x / matchedPixels / snapshot.scale, y: sum.y / matchedPixels / snapshot.scale)
    }
}

@MainActor
private final class DismissState: ObservableObject {
    @Published var transform = MediaViewerDismissTransform()
}

private struct TestPage: View {
    @ObservedObject var state: DismissState
    let image: UIImage
    let size: CGSize

    var body: some View {
        LazyPager(data: [0]) { _ in
            Image(uiImage: image).resizable().scaledToFit()
                .modifier(MediaViewerDismissEffect(transform: $state.transform))
        }
        .zoomable(min: 1, max: 5, doubleTapGesture: .scale(2))
        .frame(width: size.width, height: size.height)
        .ignoresSafeArea()
    }
}
