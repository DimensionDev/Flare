import SwiftUI
import AuthenticationServices
import shared
import NetworkImage
import Combine

struct ServiceSelectScreen: View {
    @State var viewModel: ServiceSelectViewModel
    @State var showXQT: Bool = false
    let toHome: () -> Void
    init(toHome: @escaping () -> Void) {
        viewModel = ServiceSelectViewModel(toHome: toHome)
        self.toHome = toHome
    }
    var body: some View {
        Observing(viewModel.presenter.models) { state in
            List {
                Spacer()
                    .padding()
                    .listRowSeparator(.hidden)
                Text("service_select_title")
                    .frame(maxWidth: .infinity, alignment: .center)
                    .font(.title)
                    .fontWeight(.bold)
                    .listRowSeparator(.hidden)
                    .multilineTextAlignment(.center)
                    .frame(alignment: .center)
                Text("service_select_description")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .listRowSeparator(.hidden)
                    .frame(maxWidth: .infinity, alignment: .center)
                Section(header: HStack {
                    TextField("service_select_instance_url_placeholder", text: $viewModel.instanceURL)
                        .disableAutocorrection(true)
    #if os(iOS)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
    #endif
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .disabled(state.loading)
                    switch onEnum(of: state.detectedPlatformType) {
                    case .success(let success):
                        NetworkImage(
                            url: .init(string: success.data.logoUrl),
                            content: { image in
                                image.resizable().frame(width: 24, height: 24)
                            }
                        )
                        .frame(width: 24, height: 24)
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
                    if state.canNext,
                       case .success(let success) = onEnum(of: state.detectedPlatformType) {
                        switch success.data.toSwiftEnum() {
                        case .bluesky:
                            VStack {
                                TextField(
                                    "service_select_bluesky_base_url_placeholder",
                                    text: $viewModel.blueskyInputViewModel.baseUrl
                                )
                                .disableAutocorrection(true)
    #if os(iOS)
                                .textInputAutocapitalization(.never)
                                .keyboardType(.URL)
    #endif
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .disabled(state.loading)
                                TextField("username", text: $viewModel.blueskyInputViewModel.username)
                                    .disableAutocorrection(true)
    #if os(iOS)
                                    .textInputAutocapitalization(.never)
    #endif
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                    .disabled(state.loading)
                                SecureField("password", text: $viewModel.blueskyInputViewModel.password)
                                    .disableAutocorrection(true)
    #if os(iOS)
                                    .textInputAutocapitalization(.never)
                                    .keyboardType(.URL)
    #endif
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                    .disabled(state.loading)
                                Button(action: {
                                    state.blueskyLoginState.login(
                                        baseUrl: viewModel.blueskyInputViewModel.baseUrl,
                                        username: viewModel.blueskyInputViewModel.username,
                                        password: viewModel.blueskyInputViewModel.password
                                    )
                                }, label: {
                                    Text("confirm")
                                })
                                .buttonStyle(.borderedProminent)
                            }
                            .listRowSeparator(.hidden)
                            .frame(maxWidth: .infinity, alignment: Alignment.center)
                            .disabled(state.loading)
                        case .mastodon:
                            Button {
                                state.mastodonLoginState.login(host: viewModel.instanceURL)
                            } label: {
                                Text("next")
                                    .frame(width: 200)
                            }
                            .buttonStyle(.borderedProminent)
                            .listRowSeparator(.hidden)
                            .frame(maxWidth: .infinity, alignment: Alignment.center)
                            .disabled(state.loading)
                        case .misskey:
                            Button {
                                state.misskeyLoginState.login(host: viewModel.instanceURL)
                            } label: {
                                Text("next")
                                    .frame(width: 200)
                            }
                            .buttonStyle(.borderedProminent)
                            .listRowSeparator(.hidden)
                            .frame(maxWidth: .infinity, alignment: Alignment.center)
                            .disabled(state.loading)
                        case .xQt:
                            Button {
                                showXQT = true
                            } label: {
                                Text("next")
                                    .frame(width: 200)
                            }
                            .buttonStyle(.borderedProminent)
                            .listRowSeparator(.hidden)
                            .frame(maxWidth: .infinity, alignment: Alignment.center)
                            .disabled(state.loading)
                        case .vvo:
                            Button {
                                // TODO: vvo
                            } label: {
                                Text("next")
                                    .frame(width: 200)
                            }
                            .buttonStyle(.borderedProminent)
                            .listRowSeparator(.hidden)
                            .frame(maxWidth: .infinity, alignment: Alignment.center)
                            .disabled(state.loading)
                        }
                    } else if case .success(let success) = onEnum(of: state.instances) {
                        ForEach(0..<success.itemCount, id: \.self) { index in
                            let item = success.peek(index: index)
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
                                success.get(index: index)
                            }
                            .frame( maxWidth: .infinity)
                            .listRowSeparator(.hidden)
                        }
                    } else if state.instances.isLoading {
                        ForEach(0...10, id: \.self) { _ in
                            InstancePlaceHolder()
                                .listRowSeparator(.hidden)
                        }
                    } else {
                        Text("service_select_no_instance")
                            .listRowSeparator(.hidden)
                    }
                }
            }
            .listStyle(.plain)
            .frame(maxHeight: .infinity, alignment: .top)
            .onOpenURL { url in
                if url.absoluteString.starts(with: AppDeepLink.Callback.shared.MASTODON) {
                    state.mastodonLoginState.resume(url: url.absoluteString)
                } else if url.absoluteString.starts(with: AppDeepLink.Callback.shared.MISSKEY) {
                    state.misskeyLoginState.resume(url: url.absoluteString)
                }
            }
            .sheet(isPresented: $showXQT) {
                XQTLoginScreen(toHome: {
                    showXQT = false
                    toHome()
                })
    #if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
    #endif
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
class ServiceSelectViewModel {
    let presenter: ServiceSelectPresenter
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
#if os(macOS)
                NSWorkspace.shared.open(url)
#else
                UIApplication.shared.open(url)
#endif
            }
        })
        instanceURLPublisher
            .debounce(for: .milliseconds(666), scheduler: DispatchQueue.main)
            .sink { [weak self] value in
                self?.presenter.models.value.setFilter(value: value)
            }
            .store(in: &subscriptions)
    }
    deinit {
        subscriptions.forEach { cancelable in
            cancelable.cancel()
        }
        subscriptions.removeAll()
    }
}

@Observable
class BlueskyInputViewModel {
    var baseUrl: String = "https://bsky.social"
    var username: String = ""
    var password: String = ""
}
