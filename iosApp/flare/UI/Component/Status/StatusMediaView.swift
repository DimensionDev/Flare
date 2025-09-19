import SwiftUI
import KotlinSharedUI

struct StatusMediaView: View {
    let data: [any UiMedia]
    let sensitive: Bool
    @State private var isBlur: Bool
    @State private var showFullScreen: Bool = false
    @State private var selectedItem: (any UiMedia)?

    var body: some View {
        AdaptiveMosaic(data, spacing: 4, singleMode: .force16x9) { item in
            MediaView(data: item)
                .onTapGesture {
                    if !sensitive || !isBlur {
                        // Only allow tap if not sensitive or already unblurred
                        selectedItem = item
                        showFullScreen = true
                    }
                }
        }
        .fullScreenCover(isPresented: $showFullScreen, onDismiss: {
            showFullScreen = false
        }, content: {
            StatusMediaScreen(data: data, selectedIndex: data.firstIndex(where: { $0.url == selectedItem?.url }) ?? 0)
        })
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
                        }
                    }
                    .buttonStyle(.glassProminent)
                    .padding()
                } else {
                    Button {
                        withAnimation {
                            isBlur = true
                        }
                    } label: {
                        Image("fa-eye-slash")
                    }
                    .buttonStyle(.glass)
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
