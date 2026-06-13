import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import AppleFontAwesome

private let statusMediaMaxVisibleMediaCount = 9

struct StatusMediaView: View {
    let data: [any UiMedia]
    let sensitive: Bool
    let onMediaClicked: (any UiMedia, Int) -> Void
    let cornerRadius: CGFloat
    @Environment(\.timelineAppearance.expandMediaSize) private var expandMediaSize
    @State private var isBlur: Bool
//    @State private var selectedIndex: Int? = nil

    init(
        data: [any UiMedia],
        sensitive: Bool,
        cornerRadius: CGFloat,
        onMediaClicked: @escaping (any UiMedia, Int) -> Void
    ) {
        self.data = data
        self.sensitive = sensitive
        self.onMediaClicked = onMediaClicked
        self.cornerRadius = cornerRadius
        self._isBlur = State(initialValue: sensitive)
    }

    var body: some View {
        let visibleData = Array(data.prefix(statusMediaMaxVisibleMediaCount))
        let overflowCount = data.count - visibleData.count
        AdaptiveGrid(
            singleFollowsImageAspect: expandMediaSize,
            singleViewAspectRatio: data.first?.aspectRatio,
            spacing: 4,
            maxColumns: 3,
        ) {
            ForEach(0..<visibleData.count, id: \.self) { index in
                let item = visibleData[index]
                MediaView(data: item)
                    .onTapGesture {
                        if !sensitive || !isBlur {
                            onMediaClicked(item, index)
//                            selectedIndex = index
                        }
                    }
                    .overlay {
                        if overflowCount > 0, index == visibleData.count - 1 {
                            MediaOverflowOverlay(count: overflowCount)
                                .allowsHitTesting(false)
                        }
                    }
                    .overlay(alignment: .bottomTrailing) {
                        if let alt = item.description_, !alt.isEmpty {
                            AltTextOverlay(altText: alt)
                        }
                    }
            }
        }
//        .fullScreenCover(item: $selectedIndex) { index in
//            NavigationStack {
//                StatusMediaScreen(data: data, selectedIndex: index)
//            }
//            .background(ClearFullScreenBackground())
//            .colorScheme(.dark)
//        }
        .blur(radius: isBlur ? 20 : 0)
        .overlay(
            alignment: isBlur ? .center : .topLeading
        ) {
            if sensitive {
                if isBlur {
                    Button {
                        withAnimation {
                            isBlur = false
                        }
                    } label: {
                        Label {
                            Text("sensitive_button_show", comment: "Button to show sensitive media")
                        } icon: {
                            Image(fontAwesome: .eye)
                                .foregroundStyle(.white)
                        }
                    }
                    .backport
                    .glassProminentButtonStyle()
                    .padding()
                } else {
                    Button {
                        withAnimation {
                            isBlur = true
                        }
                    } label: {
                        Image(fontAwesome: .eyeSlash)
                    }
                    .backport
                    .glassButtonStyle(fallbackStyle: .bordered)
                    .padding()
                }
            } else {
                EmptyView()
            }
        }
        .clipShape(.rect(cornerRadius: cornerRadius))
    }
}

private struct MediaOverflowOverlay: View {
    let count: Int

    var body: some View {
        ZStack {
            Color.black.opacity(0.55)
            Text(count.mediaOverflowDisplayText)
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundStyle(.white)
        }
    }
}

private extension Int {
    var mediaOverflowDisplayText: String {
        self >= 100 ? "99+" : "+\(self)"
    }
}

struct AltTextOverlay: View {
    let altText: String
    @State private var showAltText: Bool = false

    var body: some View {
        Button {
            showAltText = true
        } label: {
            Text("ALT")
        }
        .padding()
        .backport
        .glassButtonStyle(fallbackStyle: .bordered)
        .popover(isPresented: $showAltText) {
            Text(altText)
                .padding()
                .frame(width: 280)
                .presentationCompactAdaptation(.popover)
        }
    }
}

public extension UiMedia {
    var aspectRatio: CGFloat? {
        switch onEnum(of: self) {
        case .image(let image): return CGFloat(image.aspectRatio)
        case .video(let video): return CGFloat(video.aspectRatio)
        case .gif(let gifv): return CGFloat(gifv.aspectRatio)
        case .audio: return nil
        }
    }
}
