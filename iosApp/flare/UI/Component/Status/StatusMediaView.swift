import SwiftUI
import KotlinSharedUI
import TipKit
import LazyPager
import SwiftUIBackports

struct StatusMediaView: View {
    let data: [any UiMedia]
    let sensitive: Bool
    @Environment(\.themeSettings) private var themeSettings
    @State private var isBlur: Bool
    @State private var selectedIndex: Int? = nil

    var body: some View {
        AdaptiveGrid(singleFollowsImageAspect: themeSettings.appearanceSettings.expandMediaSize, spacing: 4, maxColumns: 3) {
            ForEach(0..<data.count, id: \.self) { index in
                let item = data[index]
                MediaView(data: item, expandToFullSize: themeSettings.appearanceSettings.expandMediaSize && data.count == 1)
                    .onTapGesture {
                        if !sensitive || !isBlur {
                            selectedIndex = index
                        }
                    }
                    .overlay(alignment: .bottomTrailing) {
                        if let alt = item.description_, !alt.isEmpty {
                            AltTextOverlay(altText: alt)
                        }
                    }
            }
        }
        .fullScreenCover(item: $selectedIndex) { index in
            NavigationStack {
                StatusMediaScreen(data: data, selectedIndex: index)
            }
            .background(ClearFullScreenBackground())
            .colorScheme(.dark)
        }
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
                            Image("fa-eye")
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
                        Image("fa-eye-slash")
                    }
                    .backport
                    .glassButtonStyle(fallbackStyle: .bordered)
                    .padding()
                }
            } else {
                EmptyView()
            }
        }
        .clipShape(.rect(cornerRadius: 16))
    }
}

extension StatusMediaView {
    init(data: [any UiMedia], sensitive: Bool) {
        self.data = data
        self.sensitive = sensitive
        self._isBlur = State(initialValue: sensitive)
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
