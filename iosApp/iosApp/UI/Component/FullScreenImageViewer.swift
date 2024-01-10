import SwiftUI
import shared
import NetworkImage

struct FullScreenImageViewer: View {
    let media: UiMedia
    let dismiss: () -> Void
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGPoint = .zero
    @State private var lastTranslation: CGSize = .zero
    public var body: some View {
        GeometryReader { proxy in
            ZStack {
                switch onEnum(of: media) {
                case .image(let data):
                    NetworkImage(url: URL(string: data.previewUrl)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .scaleEffect(scale)
                            .offset(x: offset.x, y: offset.y)
                            .gesture(makeDragGesture(size: proxy.size))
                            .gesture(makeMagnificationGesture(size: proxy.size))
                    }
                case .video(let video):
                    NetworkImage(url: URL(string: video.thumbnailUrl)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .scaleEffect(scale)
                            .offset(x: offset.x, y: offset.y)
                            .gesture(makeDragGesture(size: proxy.size))
                            .gesture(makeMagnificationGesture(size: proxy.size))
                    }
                case .audio:
                    Text("")
                case .gif(let gif):
                    NetworkImage(url: URL(string: gif.previewUrl)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .scaleEffect(scale)
                            .offset(x: offset.x, y: offset.y)
                            .gesture(makeDragGesture(size: proxy.size))
                            .gesture(makeMagnificationGesture(size: proxy.size))
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .edgesIgnoringSafeArea(.all)
        }
    }

    private func makeMagnificationGesture(size: CGSize) -> some Gesture {
        MagnificationGesture()
            .onChanged { value in
                let delta = value / lastScale
                lastScale = value

                // To minimize jittering
                if abs(1 - delta) > 0.01 {
                    scale *= delta
                }
            }
            .onEnded { _ in
                lastScale = 1
                if scale < 1 {
                    withAnimation {
                        scale = 1
                    }
                }
                adjustMaxOffset(size: size)
            }
    }

    private func makeDragGesture(size: CGSize) -> some Gesture {
        DragGesture()
            .onChanged { value in
                let diff = CGPoint(
                    x: value.translation.width - lastTranslation.width,
                    y: value.translation.height - lastTranslation.height
                )
                offset = .init(x: offset.x + diff.x, y: offset.y + diff.y)
                lastTranslation = value.translation
            }
            .onEnded { value in
                if scale == 1, value.location.y - value.startLocation.y > 150 {
                    dismiss()
                } else {
                    adjustMaxOffset(size: size)
                }
            }
    }

    private func adjustMaxOffset(size: CGSize) {
        let maxOffsetX = (size.width * (scale - 1)) / 2
        let maxOffsetY = (size.height * (scale - 1)) / 2

        var newOffsetX = offset.x
        var newOffsetY = offset.y

        if abs(newOffsetX) > maxOffsetX {
            newOffsetX = maxOffsetX * (abs(newOffsetX) / newOffsetX)
        }
        if abs(newOffsetY) > maxOffsetY {
            newOffsetY = maxOffsetY * (abs(newOffsetY) / newOffsetY)
        }

        let newOffset = CGPoint(x: newOffsetX, y: newOffsetY)
        if newOffset != offset {
            withAnimation {
                offset = newOffset
            }
        }
        self.lastTranslation = .zero
    }
}
