import SwiftUI
import shared

struct ProfileMediaListScreen: View {
    @State
    var presenter: ProfileMediaPresenter
    init(accountType: AccountType, userKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, userKey: userKey)
    }
    var body: some View {
        Observing(presenter.models) { state in
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 128))], content: {
                    if case .success(let success) = onEnum(of: state.mediaState) {
                        ForEach(0..<success.itemCount, id: \.self) { index in
                            if let item = success.peek(index: index) {
                                let media = item.media
                                let image = item.media as? UiMediaImage
                                let shouldBlur = image?.sensitive ?? false
                                MediaItemComponent(media: item.media)
                                    .if(shouldBlur, transform: { view in
                                        view.blur(radius: 32)
                                    })
                                    .onAppear(perform: {
                                        success.get(index: index)
                                    })
                                    .aspectRatio(1, contentMode: .fill)
                                    .clipped()
                                    .onTapGesture {
                                        if case .status(let data) = onEnum(of: item.status.content) {
                                            _ = data.images.firstIndex { it in
                                                it === media
                                            } ?? 0
//                                            statusEvent.onMediaClick(statusKey: item.status.statusKey, index: index, preview: nil)
                                        }
                                    }
                            }
                        }
                    }
                })
                .padding()
            }
        }
    }
}
