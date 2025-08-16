import SensitiveContentAnalysis
import shared
import SwiftUI

struct MediaContentScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        List {
            // 媒体显示设置
            Section("Media Display") {
                // 显示链接预览
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.showLinkPreview
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                })) {
                    Text("settings_appearance_show_link_previews")
                    Text("settings_appearance_show_link_previews_description")
                }

                // 显示媒体内容
                Toggle(isOn: Binding(get: {
                    appSettings.appearanceSettings.showMedia
                }, set: { value in
                    appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                })) {
                    Text("settings_appearance_show_media")
                    Text("settings_appearance_show_media_description")
                }

                /*
                 if appSettings.appearanceSettings.showMedia {
                     Toggle(isOn: Binding(get: {
                         appSettings.appearanceSettings.showSensitiveContent
                     }, set: { value in
                         appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                     })) {
                         Text("settings_appearance_show_cw_img")
                         Text("settings_appearance_show_cw_img_description")
                     }
                 }
                 */
            }.listRowBackground(theme.primaryBackgroundColor)

            // 敏感内容定时器设置
            SensitiveContentSection()
        }
        .sheet(isPresented: Binding(
            get: {
                appSettings.appearanceSettings.sensitiveContentSettings.isShowingTimePicker ?? false
            },
            set: { newValue in
                let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.isShowingTimePicker, to: newValue)
                appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
            }
        )) {
            TimeRangePickerSheet(
                timeRange: Binding(
                    get: { appSettings.appearanceSettings.sensitiveContentSettings.timeRange },
                    set: { newTimeRange in
                        let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.timeRange, to: newTimeRange)
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
                    }
                ),
                isPresented: Binding(
                    get: { appSettings.appearanceSettings.sensitiveContentSettings.isShowingTimePicker ?? false },
                    set: { newValue in
                        let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.isShowingTimePicker, to: newValue)
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
                    }
                )
            )
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .navigationTitle("Media & Content")
        .navigationBarTitleDisplayMode(.inline)
        #if os(macOS)
            .toggleStyle(.switch)
        #endif
    }
}
