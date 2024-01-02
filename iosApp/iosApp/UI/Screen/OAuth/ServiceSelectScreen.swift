import SwiftUI
import shared
import NetworkImage
import Combine

struct ServiceSelectScreen: View {
    @State var viewModel: ServiceSelectViewModel
    init(toHome: @escaping () -> Void) {
        viewModel = ServiceSelectViewModel(toHome: toHome)
    }
    var body: some View {
        List {
            Spacer()
                .padding()
                .listRowSeparator(.hidden)
            Text("Welcome to Flare")
                .frame(maxWidth: .infinity, alignment: .center)
                .font(.title)
                .fontWeight(.bold)
                .listRowSeparator(.hidden)
                .multilineTextAlignment(.center)
                .frame(alignment: .center)
            Text("Flare is a social network client for iOS.\nTo get started, please select a service to connect to.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
                .listRowSeparator(.hidden)
                .frame(maxWidth: .infinity, alignment: .center)
            Section(header: HStack {
                TextField("Instance URL, e.g. mastodon.social", text: $viewModel.instanceURL)
                    .disableAutocorrection(true)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .disabled(viewModel.model.loading)
                switch onEnum(of: viewModel.model.detectedPlatformType) {
                case .success(let success):
                    switch success.data.toSwiftEnum() {
                    case .bluesky:
                        NetworkImage(
                            url: .init(string: "https://blueskyweb.xyz/images/apple-touch-icon.png"),
                            content: { image in
                                image.resizable().frame(width: 24, height: 24)
                            }
                        )
                        .frame(width: 24, height: 24)
                    case .mastodon:
                        NetworkImage(
                            url: .init(
                                string: "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d5/" +
                                "Mastodon_logotype_%28simple%29_new_hue.svg/" +
                                "65px-Mastodon_logotype_%28simple%29_new_hue.svg.png"
                            ),
                            content: { image in
                                image.resizable().frame(width: 24, height: 24)
                            }
                        ) .frame(width: 24, height: 24)
                    case .misskey:
                        NetworkImage(
                            url: .init(string: "https://raw.githubusercontent.com/misskey-dev/assets/main/favicon.png"),
                            content: { image in
                                image.resizable().frame(width: 24, height: 24)
                            }
                        )
                        .frame(width: 24, height: 24)
                    }
                case .error:
                    Image(systemName: "questionmark")
                        .frame(width: 24, height: 24)
                case .loading:
                    Image(systemName: "questionmark")
                        .frame(width: 24, height: 24)
                        .redacted(reason: .placeholder)
                }
            }
                .padding()
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            ) {
                if viewModel.model.canNext,
                   case .success(let success) = onEnum(of: viewModel.model.detectedPlatformType) {
                    switch success.data.toSwiftEnum() {
                    case .bluesky:
                        VStack {
                            TextField(
                                "Base URL, e.g. https://bsky.social",
                                text: $viewModel.blueskyInputViewModel.baseUrl
                            )
                            .disableAutocorrection(true)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .disabled(viewModel.model.loading)
                            TextField("User Name", text: $viewModel.blueskyInputViewModel.username)
                                .disableAutocorrection(true)
                                .textInputAutocapitalization(.never)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .disabled(viewModel.model.loading)
                            SecureField("Password", text: $viewModel.blueskyInputViewModel.password)
                                .disableAutocorrection(true)
                                .textInputAutocapitalization(.never)
                                .keyboardType(.URL)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .disabled(viewModel.model.loading)
                            Button(action: {
                                viewModel.model.blueskyLoginState.login(
                                    baseUrl: viewModel.blueskyInputViewModel.baseUrl,
                                    username: viewModel.blueskyInputViewModel.username,
                                    password: viewModel.blueskyInputViewModel.password
                                )
                            }, label: {
                                Text("Confirm")
                            })
                            .buttonStyle(.borderedProminent)
                        }
                        .listRowSeparator(.hidden)
                        .frame(maxWidth: .infinity, alignment: Alignment.center)
                        .disabled(viewModel.model.loading)
                    case .mastodon:
                        Button {
                            viewModel.model.mastodonLoginState.login(host: viewModel.instanceURL)
                        } label: {
                            Text("Next")
                                .frame(width: 200)
                        }
                        .buttonStyle(.borderedProminent)
                        .listRowSeparator(.hidden)
                        .frame(maxWidth: .infinity, alignment: Alignment.center)
                        .disabled(viewModel.model.loading)
                    case .misskey:
                        Button {
                            viewModel.model.misskeyLoginState.login(host: viewModel.instanceURL)
                        } label: {
                            Text("Next")
                                .frame(width: 200)
                        }
                        .buttonStyle(.borderedProminent)
                        .listRowSeparator(.hidden)
                        .frame(maxWidth: .infinity, alignment: Alignment.center)
                        .disabled(viewModel.model.loading)
                    }
                } else if viewModel.model.instances.isSuccess {
                    ForEach(1...viewModel.model.instances.itemCount, id: \.self) { index in
                        let item = viewModel.model.instances.peek(index: index - 1)
                        ZStack {
                            switch item {
                            case .some(let instance):
                                Button(action: {
                                    viewModel.instanceURL = instance.domain
                                }, label: {
                                    VStack {
                                        HStack {
                                            if instance.iconUrl != nil {
                                                NetworkImage(url: URL(string: instance.iconUrl!)) { image in
                                                    image.resizable().frame(width: 24, height: 24)
                                                }.frame(width: 24, height: 24)
                                            }
                                            Text(instance.name)
                                                .font(.title)
                                        }
                                        Text(instance.domain)
                                            .font(.caption)
                                        Text(instance.description_ ?? "")
                                            .lineLimit(3)
                                            .frame(maxWidth: .infinity)
                                    }.background {
                                        if instance.bannerUrl != nil {
                                            NetworkImage(url: URL(string: instance.bannerUrl!)) { image in
                                                image.resizable().scaledToFill()
                                            }
                                            .opacity(0.15)
                                        } else {
                                            EmptyView()
                                        }
                                    }
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .clipped()
                                })
                                .buttonStyle(.plain)
                            case .none:
                                InstancePlaceHolder()
                            }
                        }
                        .onAppear {
                            viewModel.model.instances.get(index: index - 1)
                        }
                        .frame( maxWidth: .infinity)
                        .listRowSeparator(.hidden)
                    }
                } else if viewModel.model.instances.isLoading {
                    ForEach(0...10, id: \.self) { _ in
                        InstancePlaceHolder()
                            .listRowSeparator(.hidden)
                    }
                } else {
                    Text("instance not found")
                        .listRowSeparator(.hidden)
                }
            }
        }
        .listStyle(.plain)
        .frame(maxHeight: .infinity, alignment: .top)
        .activateViewModel(viewModel: viewModel)
        .onOpenURL { url in
            if url.absoluteString.starts(with: AppDeepLink.Callback.shared.MASTODON) {
                viewModel.model.mastodonLoginState.resume(url: url.absoluteString)
            } else if url.absoluteString.starts(with: AppDeepLink.Callback.shared.MISSKEY) {
                viewModel.model.misskeyLoginState.resume(url: url.absoluteString)
            }
        }
    }
}

struct InstancePlaceHolder: View {
    var body: some View {
        VStack {
            Text("Lorem ipsum dolor sit amet")
                .redacted(reason: .placeholder)
                .font(.title)
            Text("Lorem ipsum dolor sit amet")
                .redacted(reason: .placeholder)
                .font(.caption)
            Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed euismod, nisl quis aliqua")
                .redacted(reason: .placeholder)
        }
    }
}

@Observable
class ServiceSelectViewModel: MoleculeViewModelProto {
    typealias Model = ServiceSelectState
    typealias Presenter = ServiceSelectPresenter
    var model: Model
    let presenter: Presenter
    let instanceURLPublisher = PassthroughSubject<String, Never>()
    var instanceURL = "" {
        didSet {
            instanceURLPublisher.send(instanceURL)
        }
    }
    var blueskyInputViewModel = BlueskyInputViewModel()
    private var subscriptions = Set<AnyCancellable>()
    init(toHome: @escaping () -> Void) {
        self.presenter = ServiceSelectPresenter(toHome: toHome, launchUrl: { url in
            guard let url = URL(string: url) else {
                return
            }
            DispatchQueue.main.async {
                UIApplication.shared.open(url)
            }
        })
        self.model = presenter.models.value
        instanceURLPublisher
            .debounce(for: .milliseconds(666), scheduler: DispatchQueue.main)
            .sink { [weak self] value in
                self?.model.setFilter(value: value)
            }
            .store(in: &subscriptions)
    }
}

@Observable
class BlueskyInputViewModel {
    var baseUrl: String = "https://bsky.social"
    var username: String = ""
    var password: String = ""
}
