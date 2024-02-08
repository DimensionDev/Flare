import SwiftUI
import shared

struct StatusMediaScreen: View {
    @State var viewModel: StatusMediaViewModel
    let initialIndex: Int
    let dismiss: () -> Void
    
    init(statusKey: MicroBlogKey, index: Int, dismiss: @escaping () -> Void) {
        viewModel = .init(statusKey: statusKey)
        self.initialIndex = index
        self.dismiss = dismiss
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                switch onEnum(of: viewModel.model.status) {
                case .error(_):
                    Text("error")
                case .loading(_):
                    Text("loading")
                case .success(let success):
                    ScrollViewReader { reader in
                        ScrollView(.horizontal) {
                            LazyHStack(spacing: 0) {
                                ForEach(0..<success.data.medias_.count, id: \.self) { index in
                                    let item = success.data.medias_[index]
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
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class StatusMediaViewModel: MoleculeViewModelProto {
    typealias Model = StatusState
    typealias Presenter = StatusPresenter
    let presenter: StatusPresenter
    var model: Model
    init(statusKey: MicroBlogKey) {
        presenter = .init(statusKey: statusKey)
        model = presenter.models.value
    }
}
