import SwiftUI
import shared

struct SplashScreen<Content: View>: View {
    @ViewBuilder let content: (SplashType) -> Content
    
    @State var viewModel = SplashViewModel()
    
    var body: some View {
        content(viewModel.model.toSwiftEnum())
            .activateViewModel(viewModel: viewModel)
    }
}



@Observable
class SplashViewModel : MoleculeViewModelProto {
    let presenter: SplashPresenter = SplashPresenter(toHome: {}, toLogin: {})
    var model: __SplashType
    typealias Model = __SplashType
    typealias Presenter = SplashPresenter
    
    init() {
        model = presenter.models.value!
    }
}



#Preview {
    SplashScreen { type in
        Text(type.name)
    }
}
