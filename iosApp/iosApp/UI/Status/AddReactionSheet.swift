import Kingfisher
import shared
import SwiftUI

struct AddReactionSheet: View {
    @State private var presenter: AddReactionPresenter
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    private let onBack: () -> Void

    init(accountType: AccountType, statusKey: MicroBlogKey, onBack: @escaping () -> Void) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.onBack = onBack
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ScrollView {
                if case let .success(data) = onEnum(of: state.emojis) {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                        ForEach(0 ..< data.data.size, id: \.self) { index in
                            let items = data.data.getValue(index: index)
                            ForEach(0 ..< items.count, id: \.self) { iIndex in
                                let item = items[iIndex]
                                Button(action: {
                                    state.select(emoji: item)
                                    onBack()
                                }, label: {
                                    KFImage(URL(string: item.url))
                                        .resizable()
                                        .scaledToFit()
                                })
                                .buttonStyle(.plain)
                            }
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
