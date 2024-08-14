import SwiftUI
import shared
import NetworkImage

struct MisskeyReactionSheet: View {
    @State var presenter: MisskeyReactionPresenter
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    private let onBack: () -> Void
    init(accountType: AccountType, statusKey: MicroBlogKey, onBack: @escaping () -> Void) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.onBack = onBack
    }
    var body: some View {
        Observing(presenter.models) { state in
            if case .success(let data) = onEnum(of: state.emojis) {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                        ForEach(0..<data.data.size, id: \.self) { index in
                            let item = data.data.get(index: index)
                            Button(action: {
                                state.select(emoji: item)
                                onBack()
                            }, label: {
                                NetworkImage(url: URL(string: item.url)) { image in
                                    image.resizable().scaledToFit()
                                }
                            })
                            .buttonStyle(.plain)
                        }
                    }
                    .if(horizontalSizeClass == .compact, transform: { view in
                        view
                            .padding()
                    })
                }
            }
        }
    }
}
