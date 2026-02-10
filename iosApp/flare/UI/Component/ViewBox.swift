import SwiftUI

struct ViewBox<Content: View>: View {
    var content: Content
    
    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    var body: some View {
        GeometryReader { geo in
            content
                .fixedSize()
                .hidden()
                .overlay(
                    GeometryReader { childGeo in
                        let childSize = childGeo.size
                        let parentSize = geo.size
                        let scale = min(
                            parentSize.width / childSize.width,
                            parentSize.height / childSize.height
                        )
                        content
                            .fixedSize()
                            .scaleEffect(scale)
                            .frame(width: parentSize.width, height: parentSize.height)
                    }
                )
        }
    }
}
