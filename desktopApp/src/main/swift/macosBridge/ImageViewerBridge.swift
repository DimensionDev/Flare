import Cocoa
import Foundation
import Kingfisher
import SwiftUI
import AVKit

@_cdecl("open_img_viewer")
public func open_img_viewer(urlCString: UnsafePointer<CChar>?) {
    let urlStr = urlCString.flatMap { String(cString: $0) } ?? "about:blank"
    let targetURL = URL(string: urlStr)!
    DispatchQueue.main.async {
        if NSApp == nil { _ = NSApplication.shared }

        let win = NSWindow(contentRect: NSRect(x: 200, y: 200, width: 1000, height: 700),
                           styleMask: [.resizable, .titled, .closable, .miniaturizable, .fullSizeContentView],
                           backing: .buffered, defer: false)
        win.titlebarAppearsTransparent = true
        win.titleVisibility = .hidden
        win.isMovableByWindowBackground = true
        win.appearance = .init(named: .darkAqua)

        win.contentView = makeZoomingScrollView(targetURL: targetURL)
        win.isReleasedWhenClosed = false

        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
}


@_cdecl("open_video_viewer")
public func open_video_viewer(urlCString: UnsafePointer<CChar>?) {
    let urlStr = urlCString.flatMap { String(cString: $0) } ?? "about:blank"
    let targetURL = URL(string: urlStr)!
    DispatchQueue.main.async {
        if NSApp == nil { _ = NSApplication.shared }

        let win = NSWindow(contentRect: NSRect(x: 200, y: 200, width: 1000, height: 700),
                           styleMask: [.resizable, .titled, .closable, .miniaturizable, .fullSizeContentView],
                           backing: .buffered, defer: false)
        win.titlebarAppearsTransparent = true
        win.titleVisibility = .hidden
        win.isMovableByWindowBackground = true
        win.appearance = .init(named: .darkAqua)
        let playerView = AVPlayerView()
        let videoPlayer = AVPlayer(url: targetURL)
        playerView.player = videoPlayer
        videoPlayer.play()
        win.contentView = playerView
        win.isReleasedWhenClosed = false

        win.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
}



//struct OpenStatusImageModel: Decodable {
//    let index: Int
//    let medias: [StatusMediaItem]
//}
//
//struct StatusMediaItem: Decodable {
//    let url: String
//    let type: String
//    let placeholder: String?
//}
//
//@_cdecl("open_status_image_viewer")
//public func open_status_image_viewer(_ json: UnsafePointer<CChar>?) {
//    guard let json = json else { return }
//    let s = String(cString: json)
//    if let data = s.data(using: .utf8),
//       let model = try? JSONDecoder().decode(OpenStatusImageModel.self, from: data) {
//        DispatchQueue.main.sync {
//            if NSApp == nil { _ = NSApplication.shared }
//            let win = NSWindow(contentRect: NSRect(x: 200, y: 200, width: 1000, height: 700),
//                               styleMask: [.resizable, .titled, .closable, .miniaturizable, .fullSizeContentView],
//                               backing: .buffered, defer: false)
////            let win = NSWindow(contentViewController: OpenStatusPagerViewController(model: model))
////            win.contentMinSize = .init(width: 100, height: 100)
////            win.styleMask = [.resizable, .titled, .closable, .miniaturizable, .fullSizeContentView]
//            win.titlebarAppearsTransparent = true
//            win.titleVisibility = .hidden
//            win.isMovableByWindowBackground = true
//            win.appearance = .init(named: .darkAqua)
//            
////            let pagerVC = OpenStatusPagerViewController(model: model)
////            win.contentViewController = pagerVC
//            win.contentView = NSHostingView(rootView: MediaPagerView(model: model))
//            win.isReleasedWhenClosed = false
//            win.delegate = windowDelegate
//            openedWindows.insert(win)
//            swiftLog(2, "Window opened. Total windows: \(openedWindows.count)") // 调试日志
//
//            win.makeKeyAndOrderFront(nil)
//            NSApp.activate(ignoringOtherApps: true)
//            swiftLog(2, "8. Window should be visible. Window count: \(openedWindows.count)")
//            swiftLog(2, "9. Window is visible: \(win.isVisible), is key: \(win.isKeyWindow)")
//
//        }
//    }
//}




@MainActor
func makeZoomingScrollView(targetURL: URL) -> NSScrollView {
    let imageView = NSImageView()
    imageView.imageScaling = .scaleNone
    imageView.imageAlignment = .alignCenter
    imageView.animates = true

    let scrollView = NSScrollView()
    scrollView.contentView = CenteringClipView()
    scrollView.documentView = imageView
    scrollView.drawsBackground = false
    scrollView.hasVerticalScroller = true
    scrollView.hasHorizontalScroller = true
    scrollView.allowsMagnification = true
    scrollView.minMagnification = 0.1
    scrollView.maxMagnification = 8
    scrollView.automaticallyAdjustsContentInsets = false
    scrollView.contentInsets = .init()
    
    imageView.kf
        .setImage(with: targetURL) { result in
        switch result {
        case .success(let value):
            let size = value.image.size
            guard size.width > 0, size.height > 0 else { return }

            imageView.frame = CGRect(origin: .zero, size: size)

            scrollView.layoutSubtreeIfNeeded()
            scrollView.magnify(toFit: imageView.bounds)
            (scrollView.contentView as? NSClipView).map {
                $0.scroll(to: $0.bounds.origin)
                scrollView.reflectScrolledClipView($0)
            }
        case .failure(let error):
            NSLog("Kingfisher load failed: \(error)")
        }
    }

    return scrollView
}

final class CenteringClipView: NSClipView {
    override func constrainBoundsRect(_ proposedBounds: NSRect) -> NSRect {
        var rect = super.constrainBoundsRect(proposedBounds)
        guard let doc = documentView else { return rect }

        let docSize = doc.frame.size
        let clipSize = bounds.size

        if docSize.width < clipSize.width {
            rect.origin.x = -(clipSize.width - docSize.width) / 2
        }
        if docSize.height < clipSize.height {
            rect.origin.y = -(clipSize.height - docSize.height) / 2
        }
        return rect
    }
}
