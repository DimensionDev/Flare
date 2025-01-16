import shared
import SwiftUI

struct StatusMediaScreen: View {
    @State private var presenter: StatusPresenter
    @State private var selectedImage: Int
    private let initialIndex: Int32
    private let dismiss: () -> Void

    init(accountType: AccountType, statusKey: MicroBlogKey, index: Int32, dismiss: @escaping () -> Void) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        initialIndex = index
        self.dismiss = dismiss
        selectedImage = .init(index + 1)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ZStack {
                switch onEnum(of: state.status) {
                case .error:
                    Text("error")
                case .loading:
                    Text("loading")
                case let .success(success):
                    if case let .status(data) = onEnum(of: success.data.content) {
                        TabView(selection: $selectedImage) {
                            ForEach(0 ..< data.images.count, id: \.self) { index in
                                let item = data.images[index]
                                FullScreenImageViewer(media: item)
                                    .tag(index)
                            }
                        }
                        #if os(iOS)
                        .tabViewStyle(.page)
                        #endif
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
