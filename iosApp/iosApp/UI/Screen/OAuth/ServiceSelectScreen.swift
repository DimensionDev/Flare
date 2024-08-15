import SwiftUI
import AuthenticationServices
import shared
import NetworkImage
import Combine

struct ServiceSelectScreen: View {
    @State private var presenter: ServiceSelectPresenter
    @State private var showXQT: Bool = false
    @State private var showVVo: Bool = false
    @State private var instanceURL = ""
    @State private var blueskyInputViewModel = BlueskyInputViewModel()
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    let toHome: () -> Void
    init(toHome: @escaping () -> Void) {
        presenter = .init(toHome: toHome)
        self.toHome = toHome
    }
    var body: some View {
//        Observing(presenter.models) { state in
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
                    TextField("service_select_instance_url_placeholder", text: $instanceURL)
                        .disableAutocorrection(true)
#if os(iOS)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
#endif
                        .textFieldStyle(RoundedBorderTextFieldStyle())
//                        .disabled(state.loading)
                    Observing(presenter.models) { state in
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
                    .onChange(of: instanceURL) {
                        presenter.models.value.setFilter(value: instanceURL)
                    }
                }
                    .padding()
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                ) {
                    Observing(presenter.models) { state in
                        if state.canNext,
                           case .success(let success) = onEnum(of: state.detectedPlatformType) {
                            switch success.data.toSwiftEnum() {
                            case .bluesky:
                                VStack {
                                    TextField(
                                        "service_select_bluesky_base_url_placeholder",
                                        text: $blueskyInputViewModel.baseUrl
                                    )
                                    .disableAutocorrection(true)
    #if os(iOS)
                                    .textInputAutocapitalization(.never)
                                    .keyboardType(.URL)
    #endif
                                    .textFieldStyle(RoundedBorderTextFieldStyle())
                                    .disabled(state.loading)
                                    TextField("username", text: $blueskyInputViewModel.username)
                                        .disableAutocorrection(true)
    #if os(iOS)
                                        .textInputAutocapitalization(.never)
    #endif
                                        .textFieldStyle(RoundedBorderTextFieldStyle())
                                        .disabled(state.loading)
                                    SecureField("password", text: $blueskyInputViewModel.password)
                                        .disableAutocorrection(true)
    #if os(iOS)
                                        .textInputAutocapitalization(.never)
                                        .keyboardType(.URL)
    #endif
                                        .textFieldStyle(RoundedBorderTextFieldStyle())
                                        .disabled(state.loading)
                                    Button(action: {
                                        state.blueskyLoginState.login(
                                            baseUrl: blueskyInputViewModel.baseUrl,
                                            username: blueskyInputViewModel.username,
                                            password: blueskyInputViewModel.password
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
                                    state.mastodonLoginState.login(host: instanceURL, launchUrl: { url in handleUrl(url: url)})
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
                                    state.misskeyLoginState.login(host: instanceURL, launchUrl: { url in handleUrl(url: url)})
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
                                    showVVo = true
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
                                            instanceURL = instance.domain
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
            }
            .listStyle(.plain)
            .frame(maxHeight: .infinity, alignment: .top)
            .sheet(isPresented: $showXQT) {
                XQTLoginScreen(toHome: {
                    showXQT = false
                    toHome()
                })
#if os(macOS)
                .frame(minWidth: 600, minHeight: 400)
#endif
            }
            .sheet(isPresented: $showVVo, content: {
                VVOLoginScreen(toHome: {
                    showVVo = false
                    toHome()
                })
            })
//        }
    }
    private func handleUrl(url: String) {
        Task {
            guard let url = URL(string: url) else {
                return
            }
            do {
                let urlWithToken = try await webAuthenticationSession.authenticate(
                    using: url,
                    callbackURLScheme: APPSCHEMA
                )
                if urlWithToken.absoluteString.starts(with: AppDeepLink.Callback.shared.MASTODON) {
                    presenter.models.value.mastodonLoginState.resume(url: urlWithToken.absoluteString)
                } else if urlWithToken.absoluteString.starts(with: AppDeepLink.Callback.shared.MISSKEY) {
                    presenter.models.value.misskeyLoginState.resume(url: urlWithToken.absoluteString)
                }
            } catch {
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
class BlueskyInputViewModel {
    var baseUrl: String = "https://bsky.social"
    var username: String = ""
    var password: String = ""
}
