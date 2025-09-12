import SwiftUI
import KotlinSharedUI
import Kingfisher

struct ServiceSelectionScreen : View {
    @State private var presenter: KotlinPresenter<ServiceSelectState>
    @State private var input: String = ""
    
    init(toHome: @escaping () -> Void) {
        self._presenter = .init(initialValue: .init(presenter: ServiceSelectPresenter(toHome: toHome)))
    }
    
    var body: some View {
        List {
            Text("Login")
            TextField(text: $input) {
                Text("Instance Url")
            }
            .onChange(of: input) { oldValue, newValue in
                presenter.state.setFilter(value: newValue)
            }
            StateView(state: presenter.state.detectedPlatformType) { platformType in

            }
            Section {
                PagingView(data: presenter.state.instances) { instance in
                    VStack(
                        alignment: .center
                    ) {
                        if let banner = instance.bannerUrl {
                            KFImage.url(.init(string: banner))
                                .frame(maxHeight: 360)
                                .aspectRatio(16.0 / 9.0, contentMode: .fill)
                        }
                        Text(instance.name)
                            .font(.title)
                        Text(instance.domain)
                            .font(.caption)
                        if let desc = instance.description_ {
                            Text(desc)
                                .font(.caption2)
                        }
                    }
                    .containerShape(.capsule)
                }
            }
        }
    }
}
