import SwiftUI
import KotlinSharedUI
import LazyPager

struct MediaScreen: View {
    let url: String
    @Environment(\.dismiss) var dismiss
    @State var opacity: CGFloat = 1 // Dismiss gesture background opacity
    var body: some View {
        LazyPager(data: [url]) { item in
            NetworkImage(data: item)
                .scaledToFit()
        }
        .onDismiss(backgroundOpacity: $opacity) {
            dismiss()
        }
        .zoomable(min: 1, max: 5)
        .settings { config in
            config.preloadAmount = 99
        }
        .background(.black.opacity(opacity))
        .background(ClearFullScreenBackground())
        .ignoresSafeArea()
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image("fa-xmark")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    MediaSaver.shared.saveImage(url: url)
                } label: {
                    Image("fa-download")
                }
            }
        }
    }
}
