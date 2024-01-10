#if os(macOS)
import SwiftUI
import NetworkImage

struct ImageViewWindow: View {
    let url: String?
    var body: some View {
        if let url = url {
            NetworkImage(url: URL(string: url)) { image in
                image.resizable().scaledToFit()
            }
        }
    }
}
#endif
