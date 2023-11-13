import Foundation
import SwiftUI
import shared

@Observable
class MoleculeViewModelBase<Model, Presenter: PresenterBase<Model>>: MoleculeViewModelProto {
    typealias Model = Model
    typealias Presenter = Presenter
    
    internal let presenter = Presenter()
    var model: Model
    
    init() {
        model = presenter.models.value
    }
}

protocol MoleculeViewModelProto: AnyObject {
    associatedtype Model: AnyObject
    associatedtype Presenter: PresenterBase<Model>
    var presenter: Presenter { get }
    var model: Model { get set }
}

extension MoleculeViewModelProto {
    @MainActor
    func activate() async {
        for await model in presenter.models {
            self.model = model
        }
    }
}

extension View {
    func activateViewModel(viewModel: some MoleculeViewModelProto) -> some View {
        return task {
            await viewModel.activate()
        }
    }
}
