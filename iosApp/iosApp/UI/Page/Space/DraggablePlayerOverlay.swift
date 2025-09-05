import shared
import SwiftUI

struct DraggablePlayerOverlay: View {
    @State private var manager = IOSPodcastManager.shared

    @State private var accumulatedOffset: CGSize = .zero
    @State private var dragOffset: CGSize = .zero

    var body: some View {
        let isReplay = manager.duration != nil

        Group {
            if isReplay {
                ReplayFloatingPlayerView(manager: manager)
            } else {
                LiveFloatingPlayerView()
            }
        }
        .offset(x: accumulatedOffset.width + dragOffset.width,
                y: accumulatedOffset.height + dragOffset.height)
        .gesture(
            DragGesture()
                .onChanged { value in
                    dragOffset = value.translation
                }
                .onEnded { value in
                    withAnimation(.spring()) {
                        accumulatedOffset.width += value.translation.width
                        accumulatedOffset.height += value.translation.height
                        dragOffset = .zero
                    }
                }
        )
        .onChange(of: manager.currentPodcast) { newValue in
            if newValue == nil {
                withAnimation(.spring()) {
                    accumulatedOffset = .zero
                    dragOffset = .zero
                }
            }
        }
        .frame(maxHeight: .infinity, alignment: .top)
        .padding(.top, 40)
    }
}
