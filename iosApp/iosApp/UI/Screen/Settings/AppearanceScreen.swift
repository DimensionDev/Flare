import SwiftUI
import shared

struct AppearanceScreen: View {
    @Environment(\.appSettings) private var appSettings
    @State var viewModel = AppearanceViewModel()
    var body: some View {
        List {
            if case .success(let success) = onEnum(of: viewModel.model.sampleStatus) {
                StatusItemView(
                    status: success.data,
                    mastodonEvent: EmptyStatusEvent.shared,
                    misskeyEvent: EmptyStatusEvent.shared,
                    blueskyEvent: EmptyStatusEvent.shared,
                    xqtEvent: EmptyStatusEvent.shared
                )
            }
            Section("Generic") {
                Picker(selection: Binding(get: {
                    appSettings.appearanceSettings.theme
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: value))
                }), content: {
                    Text("Auto")
                        .tag(Theme.auto)
                    Text("Dark")
                        .tag(Theme.dark)
                    Text("Light")
                        .tag(Theme.light)
                }, label: {
                    Text("Theme")
                    Text("Change the theme of the app")
                })
                Picker(selection: Binding(get: {
                    appSettings.appearanceSettings.avatarShape
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.avatarShape, to: value))
                }), content: {
                    Text("Circle")
                        .tag(AvatarShape.circle)
                    Text("Square")
                        .tag(AvatarShape.square)
                }, label: {
                    Text("Avatar shape")
                    Text("Change the shape of the avatar")
                })
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.showActions
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showActions, to: value))
                })) {
                    Text("Show actions")
                    Text("Show actions on the bottom of the status")
                }
                if appSettings.appearanceSettings.showActions {
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showNumbers
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showNumbers, to: value))
                    })) {
                        Text("Show numbers")
                        Text("Show numbers on the bottom of the status")
                    }
                }
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.showLinkPreview
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                })) {
                    Text("Show link previews")
                    Text("Show link previews in the status")
                }
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.showMedia
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                })) {
                    Text("Show media")
                    Text("Show media in the status")
                }
                if appSettings.appearanceSettings.showMedia {
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showSensitiveContent
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                    })) {
                        Text("Show sensitive content")
                        Text("Always show sensitive content in the status")
                    }
                }
            }
            .buttonStyle(.plain)
            .navigationTitle("Appearance")
            .activateViewModel(viewModel: viewModel)
        }
        #if os(macOS)
        .toggleStyle(.switch)
        .pickerStyle(.segmented)
        #endif
    }
}
@Observable
class AppearanceViewModel: MoleculeViewModelBase<AppearanceState, AppearancePresenter> {
}
