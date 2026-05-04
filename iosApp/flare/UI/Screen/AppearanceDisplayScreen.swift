import SwiftUI
import KotlinSharedUI

struct AppearanceDisplayScreen: View {
    @StateObject private var statusPresenter = KotlinPresenter(presenter: AppearancePresenter())
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    var body: some View {
        List {
            Section {
                StateView(state: statusPresenter.state.sampleStatus) { status in
                    TimelineView(data: status)
                }
                StateView(state: presenter.state.appearance) { appearance in
                    Toggle(isOn: Binding(get: {
                        appearance.absoluteTimestamp
                    }, set: { newValue in
                        presenter.state.updateAbsoluteTimestamp(value: newValue)
                    })) {
                        Text("appearance_absolute_timestamp")
                        Text("appearance_absolute_timestamp_description")
                    }
                    Toggle(isOn: Binding(get: {
                        appearance.showPlatformLogo
                    }, set: { newValue in
                        presenter.state.updateShowPlatformLogo(value: newValue)
                    })) {
                        Text("appearance_show_platform_logo")
                        Text("appearance_show_platform_logo_description")
                    }
                    Toggle(isOn: Binding(get: {
                        appearance.showLinkPreview
                    }, set: { newValue in
                        presenter.state.updateShowLinkPreview(value: newValue)
                    })) {
                        Text("appearance_show_link_preview")
                        Text("appearance_show_link_preview_description")
                    }
                    if appearance.showLinkPreview {
                        Toggle(isOn: Binding(get: {
                            appearance.compatLinkPreview
                        }, set: { newValue in
                            presenter.state.updateCompatLinkPreview(value: newValue)
                        })) {
                            Text("appearance_compat_link_preview")
                            Text("appearance_compat_link_preview_description")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appearance.inAppBrowser
                    }, set: { newValue in
                        presenter.state.updateInAppBrowser(value: newValue)
                    })) {
                        Text("appearance_in_app_browser")
                        Text("appearance_in_app_browser_description")
                    }
                }
            }
        }
        .navigationTitle("appearance_display_group_title")
    }
}
