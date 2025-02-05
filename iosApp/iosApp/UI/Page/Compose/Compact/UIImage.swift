import Foundation

#if os(macOS)
    import Cocoa

    typealias UIImage = NSImage

    extension NSImage {
        var cgImage: CGImage? {
            var proposedRect = CGRect(origin: .zero, size: size)

            return cgImage(forProposedRect: &proposedRect,
                           context: nil,
                           hints: nil)
        }
    }
#endif
