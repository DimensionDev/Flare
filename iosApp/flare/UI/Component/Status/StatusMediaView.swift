import SwiftUI
import KotlinSharedUI
import TipKit

struct StatusMediaView: View {
    let data: [any UiMedia]
    let sensitive: Bool
    @State private var isBlur: Bool
    @State private var showFullScreen: Bool = false
    @State private var selectedItem: (any UiMedia)?
    @State private var selectedAlt: AltTextHolder? = nil
    
    struct AltTextHolder: Identifiable {
        let id: String
    }

    var body: some View {
        AdaptiveGrid(singleFollowsImageAspect: false, spacing: 4, maxColumns: 3) {
            ForEach(data, id: \.url) { item in
                Color.clear
                    .overlay {
                        MediaView(data: item)
                            .onTapGesture {
                                if !sensitive || !isBlur {
                                    // Only allow tap if not sensitive or already unblurred
                                    selectedItem = item
                                    showFullScreen = true
                                }
                            }
                    }
                    .overlay(alignment: .bottomTrailing) {
                        if let alt = item.description_, !alt.isEmpty {
                            Button {
                                selectedAlt = AltTextHolder(id: alt)
                            } label: {
                                Text("ALT")
                            }
                            .popover(item: $selectedAlt) { alt in
                                Text(alt.id)
                                    .padding()
                            }
                            .padding()
                            .buttonStyle(.glass)
                        }
                    }
                    .overlay(alignment: .bottomLeading) {
                        if case .video = onEnum(of: item) {
                            Image("fa-circle-play")
                                .foregroundStyle(Color(.white))
                                .padding(8)
                                .background(.black, in: .rect(cornerRadius: 16))
                                .padding()
                        }
                    }
                    .clipped()
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

struct AltTextTip: Tip {
    let altText: String
    
    var title: Text {
        Text("tip_alt_text_title")
    }


    var message: Text? {
        Text(altText)
    }

}
