import SwiftUI
import shared

struct ProfileMediaListScreen: View {
    @State var viewModel: ProfileMediaViewModel
    init(userKey: MicroBlogKey) {
        viewModel = ProfileMediaViewModel(userKey: userKey)
    }
    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 128))], content: {
                if case .success(let success) = onEnum(of: viewModel.model.mediaState) {
                    ForEach(0..<success.data.itemCount, id: \.self) { index in
                        if let item = success.data.peek(index: index) {
                            let image = item as? UiMediaImage
                            let shouldBlur = image?.sensitive ?? false
                            MediaItemComponent(media: item)
                                .if(shouldBlur, transform: { view in
                                    view.blur(radius: 32)
                                })
                                .onAppear(perform: {
                                    success.data.get(index: index)
                                })
                                .aspectRatio(1, contentMode: .fill)
                                .clipped()
                        }
                    }
                }
            })
            .padding()
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class ProfileMediaViewModel: MoleculeViewModelProto {
    typealias Model = ProfileMediaState
    typealias Presenter = ProfileMediaPresenter
    var model: Model
    let presenter: ProfileMediaPresenter
    init(userKey: MicroBlogKey) {
        presenter = ProfileMediaPresenter(userKey: userKey)
        model = presenter.models.value
    }
}
