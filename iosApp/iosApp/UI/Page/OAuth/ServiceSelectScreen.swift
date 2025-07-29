import AuthenticationServices
import Combine
import Kingfisher
import shared
import SwiftUI

struct ServiceSelectScreen: View {
    @State private var presenter: ServiceSelectPresenter
    @State private var showXQT: Bool = false
    @State private var showVVo: Bool = false
    @State private var instanceURL = ""
    @State private var selectedInstance: UiInstance? = nil
    @State private var blueskyInputViewModel = BlueskyInputViewModel()
    @Environment(\.webAuthenticationSession) private var webAuthenticationSession
    let toHome: () -> Void
    @Environment(FlareTheme.self) private var theme

    init(toHome: @escaping () -> Void) {
        presenter = .init(toHome: toHome)
        self.toHome = toHome
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            VStack {
                VStack {
                    Text("service_select_welcome_title")
                        .frame(maxWidth: .infinity, alignment: .center)
                        .font(.title)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)
                        .frame(alignment: .center)
                        .padding(.top)
                    Text(verbatim: NSLocalizedString("service_select_welcome_message", comment: "")
                        .replacingOccurrences(of: "\\n", with: "\n")
                        .replacingOccurrences(of: "Android", with: "iOS"))
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity, alignment: .center)

                    HStack {
                        TextField("service_select_instance_input_placeholder", text: $instanceURL)
                            .disableAutocorrection(true)
                        #if os(iOS)
                            .textInputAutocapitalization(.never)
                            .keyboardType(.URL)
                        #endif
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .disabled(state.loading)
                        switch onEnum(of: state.detectedPlatformType) {
                        case let .success(success):
                            KFImage(URL(string: success.data.logoUrl))
                                .resizable()
                                .frame(width: 24, height: 24)
                        case .error:
                            EmptyView()
                        case .loading:
                            EmptyView().padding(.trailing, 24)
                        }
                    }
                    .onChange(of: instanceURL) {
                        state.setFilter(value: instanceURL)
                    }
                }
                .padding()
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if state.canNext,
                           case let .success(success) = onEnum(of: state.detectedPlatformType)
                        {
                            switch success.data.toSwiftEnum() {
                            case .bluesky:
                                blueskyLoginView(state: state)
                            case .mastodon:
                                mastodonLoginView(state: state)
                            case .misskey:
                                misskeyLoginView(state: state)
                            case .xQt:
                                xqtLoginView(state: state)
                            case .vvo:
                                vvoLoginView(state: state)
                            }
                            Spacer()
                            // 显示选中的实例卡片
                            if let instance = selectedInstance {
                                instanceCardView(instance: instance)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 24)
                            }
                        } else if case let .success(success) = onEnum(of: state.instances) {
                            ForEach(0 ..< success.itemCount, id: \.self) { index in
                                let item = success.peek(index: index)
                                if let instance = item {
                                    instanceCardView(instance: instance)
                                        .contentShape(Rectangle())
                                        .onTapGesture {
                                            selectedInstance = instance
                                            instanceURL = instance.domain
                                            state.setFilter(value: instance.domain)
                                        }
                                        .clipShape(RoundedRectangle(cornerRadius: 12))
                                        .shadow(radius: 2)
                                        .padding(.vertical, 4)
                                } else {
                                    InstancePlaceHolder()
                                }
                            }.scrollContentBackground(.hidden)
                                .listRowBackground(theme.primaryBackgroundColor)
                        } else if state.instances.isLoading {
                            ForEach(0 ... 5, id: \.self) { _ in // 减少占位符数量
                                InstancePlaceHolder()
                            }
                        } else {
                            Text("service_select_no_instance")
                        }
                    }
                    .padding(.horizontal)
                }
            }
            .background(theme.secondaryBackgroundColor)
            // .foregroundColor(theme.labelColor)
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
        }
    }

    // - Platform Login Views

    @ViewBuilder
    private func blueskyLoginView(state: ServiceSelectState) -> some View {
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
        .frame(maxWidth: .infinity, alignment: .center)
        .disabled(state.loading)
    }

    @ViewBuilder
    private func mastodonLoginView(state: ServiceSelectState) -> some View {
        Button {
            state.mastodonLoginState.login(host: instanceURL, launchUrl: { url in handleUrl(url: url) })
        } label: {
            Text("next")
                .frame(width: 200)
        }
        .buttonStyle(.borderedProminent)
        .listRowSeparator(.hidden)
        .frame(maxWidth: .infinity, alignment: .center)
        .disabled(state.loading)
    }

    @ViewBuilder
    private func misskeyLoginView(state: ServiceSelectState) -> some View {
        Button {
            state.misskeyLoginState.login(host: instanceURL, launchUrl: { url in handleUrl(url: url) })
        } label: {
            Text("next")
                .frame(width: 200)
        }
        .buttonStyle(.borderedProminent)
        .listRowSeparator(.hidden)
        .frame(maxWidth: .infinity, alignment: .center)
        .disabled(state.loading)
    }

    @ViewBuilder
    private func xqtLoginView(state: ServiceSelectState) -> some View {
        Button {
            showXQT = true
        } label: {
            Text("next")
                .frame(width: 200)
        }
        .buttonStyle(.borderedProminent)
        .listRowSeparator(.hidden)
        .frame(maxWidth: .infinity, alignment: .center)
        .disabled(state.loading)
    }

    @ViewBuilder
    private func vvoLoginView(state: ServiceSelectState) -> some View {
        Button {
            showVVo = true
        } label: {
            Text("next")
                .frame(width: 200)
        }
        .buttonStyle(.borderedProminent)
        .listRowSeparator(.hidden)
        .frame(maxWidth: .infinity, alignment: .center)
        .disabled(state.loading)
    }

    @ViewBuilder
    private func instanceCardView(instance: UiInstance) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // 第一部分：头像和基本信息
            ZStack {
                // 背景层
                if let bannerUrl = instance.bannerUrl {
                    GeometryReader { _ in
                        KFImage(URL(string: bannerUrl))
                            .flareServiceIcon(size: CGSize(width: UIScreen.main.bounds.width * 2, height: 160))
                            .placeholder { // 添加占位符
                                ServiceIconBackground(url: instance.iconUrl ?? "", domain: instance.domain)
                            }
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            // .frame(width: geometry.size.width)
                            .frame(height: 80, alignment: .center)
                            .clipped()
                            // .blur(radius: 3)
                            // .overlay(Rectangle().fill(theme.primaryBackgroundColor.opacity(0.2)))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .frame(height: 80)
                } else {
                    // 如果没有banner，使用icon作为背景
                    ServiceIconBackground(url: instance.iconUrl ?? "", domain: instance.domain)
                }
            }
            .frame(height: 80)

            // 第二部分：简介
            if let description = instance.description_ {
                Text(parseHTML(description))
                    .lineLimit(3)
                    .font(.footnote)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .allowsHitTesting(false)
                    .foregroundStyle(Color.secondary)
            }

            HStack(alignment: .top) {
                ServiceIcon(
                    url: instance.iconUrl ?? "",
                    domain: instance.domain,
                    size: 15,
                    clipShape: AnyShape(Circle())
                ).padding(.vertical, 8).clipShape(RoundedRectangle(cornerRadius: 6))

                Text(instance.domain)
                    .font(.subheadline)
                    .foregroundColor(theme.labelColor)
                    .padding(.horizontal, 0)
                    .padding(.vertical, 4)
                    //  .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                Spacer()

                if instance.usersCount > 0 {
                    Text("\(instance.usersCount) users")
                        .font(.subheadline)
                        .foregroundColor(theme.tintColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }
            }.padding(.horizontal)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
        .background(theme.primaryBackgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .frame(maxWidth: min(UIScreen.main.bounds.width - 32, 600))
        .frame(maxWidth: .infinity)
    }

    private func parseHTML(_ html: String) -> String {
        // 移除常见的HTML标签
        var result = html
        let patterns = [
            "<[^>]+>", // 移除所有HTML标签
            "&nbsp;", // 替换特殊字符
            "&amp;",
            "&lt;",
            "&gt;",
            "&quot;"
        ]

        let replacements = [
            "", // 对应标签的替换
            " ",
            "&",
            "<",
            ">",
            "\""
        ]

        for (pattern, replacement) in zip(patterns, replacements) {
            if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) {
                result = regex.stringByReplacingMatches(
                    in: result,
                    options: [],
                    range: NSRange(result.startIndex..., in: result),
                    withTemplate: replacement
                )
            }
        }

        // 处理连续的空白字符
        if let regex = try? NSRegularExpression(pattern: "\\s+", options: .caseInsensitive) {
            result = regex.stringByReplacingMatches(
                in: result,
                options: [],
                range: NSRange(result.startIndex..., in: result),
                withTemplate: " "
            )
        }

        return result.trimmingCharacters(in: .whitespacesAndNewlines)
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
            } catch {}
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
