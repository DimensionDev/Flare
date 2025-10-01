import SwiftUI
import KotlinSharedUI

struct AiConfigScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @StateObject private var serverPresenter = KotlinPresenter(presenter: FlareServerProviderPresenter())
    @State private var serverName: String = ""
    var body: some View {
        Form {
            Section {
                TextField("ai_config_server_provider_placeholder", text: $serverName, prompt: Text("ai_config_server_provider_placeholder"))
                    .safeAreaInset(edge: .trailing) {
                        StateView(state: serverPresenter.state.serverValidation) { _ in
                            Image("fa-circle-check").foregroundColor(.green)
                        } errorContent: { _ in
                            Image("fa-circle-exclamation").foregroundColor(.red)
                        } loadingContent: {
                            ProgressView().frame(width: 20, height: 20)
                        }
                    }
                    .onChange(of: serverName, { oldValue, newValue in
                        serverPresenter.state.checkServer(value: newValue)
                    })
                    .onChange(of: serverPresenter.state.serverValidation) { oldValue, newValue in
                        if case .success = onEnum(of: newValue) {
                            serverPresenter.state.confirm()
                        }
                    }
            } header: {
                Text("ai_config_server_provider_section_header")
            }
            StateView(state: presenter.state.appSettings) { settings in
                Section {
                    Toggle(isOn: Binding(get: {
                        settings.aiConfig.translation
                    }, set: { newValue in
                        presenter.state.updateAppSettings { current in
                            current.doCopy(version: current.version, aiConfig: current.aiConfig.doCopy(translation: newValue, tldr: current.aiConfig.tldr))
                        }
                    })) {
                        Text("ai_config_translate")
                    }
                    Toggle(isOn: Binding(get: {
                        settings.aiConfig.tldr
                    }, set: { newValue in
                        presenter.state.updateAppSettings { current in
                            current.doCopy(version: current.version, aiConfig: current.aiConfig.doCopy(translation: current.aiConfig.translation, tldr: newValue))
                        }
                    })) {
                        Text("ai_config_summarize")
                    }
                } header: {
                    Text("ai_config_options_section_header")
                }
            }
        }
        .onChange(of: serverPresenter.state.currentServer, { oldValue, newValue in
            if case .success(let success) = onEnum(of: newValue) {
                serverName = String(success.data)
            }
        })
        .navigationTitle("ai_config_title")
    }
}
