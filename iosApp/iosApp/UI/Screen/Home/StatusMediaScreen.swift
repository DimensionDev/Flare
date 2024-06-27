import SwiftUI
import shared

struct StatusMediaScreen: View {
    let presenter: StatusPresenter
    let initialIndex: Int
    let dismiss: () -> Void

    init(accountType: AccountType, statusKey: MicroBlogKey, index: Int, dismiss: @escaping () -> Void) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.initialIndex = index
        self.dismiss = dismiss
    }

    var body: some View {
        Observing(presenter.models) { state in
            GeometryReader { geometry in
                ZStack {
                    switch onEnum(of: state.status) {
                    case .error:
                        Text("error")
                    case .loading:
                        Text("loading")
                    case .success(let success):
                        ScrollViewReader { reader in
                            ScrollView(.horizontal) {
                                LazyHStack(spacing: 0) {
                                    ForEach(0..<success.data.medias.count, id: \.self) { index in
                                        let item = success.data.medias[index]
                                        FullScreenImageViewer(media: item)
                                            .frame(width: geometry.size.width)
                                    }
                                }
                                .scrollTargetLayout()
                            }
                            .scrollTargetBehavior(.paging)
                            .ignoresSafeArea()
                            .onAppear {
                                reader.scrollTo(initialIndex)
                            }
                        }
                    }

                    VStack {
                        HStack {
                            Button {
                                dismiss()
                            } label: {
                                Image(systemName: "xmark")
                                    .resizable()
                                    .renderingMode(.template)
                                    .frame(width: 15, height: 15)
                                    .foregroundColor(.white)
                            }
                            .padding()
                            Spacer()
                        }
                        Spacer()
                    }
                }
            }
        }
    }
}
