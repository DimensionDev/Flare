import Foundation
import shared
import SwiftUI

@Observable
class MoleculeViewModelBase<Model, Presenter: PresenterBase<Model>>: MoleculeViewModelProto {
    typealias Model = Model
    typealias Presenter = Presenter
    let presenter = Presenter()
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

protocol ModelChangedProto {
    associatedtype Model: AnyObject
    func onModelChanged(value: Model)
    func callOnModelChangedIfConformed(with model: AnyObject)
}

extension ModelChangedProto {
    func callOnModelChangedIfConformed(with model: AnyObject) {
        if let model = model as? Model {
            onModelChanged(value: model)
        }
    }
}

extension MoleculeViewModelProto {
    @MainActor
    func activate() async {
        for await model in presenter.models {
            self.model = model
            if let notify = self as? any ModelChangedProto {
                notify.callOnModelChangedIfConformed(with: model)
            }
        }
    }
}

extension View {
    func activateViewModel(viewModel: some MoleculeViewModelProto) -> some View {
        task {
            await viewModel.activate()
        }
    }
}
