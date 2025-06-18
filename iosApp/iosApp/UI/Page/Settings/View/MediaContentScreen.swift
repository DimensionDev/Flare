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

            // 内容过滤设置
            Section("Content Filtering") {
                AIContentAnalysisToggle()
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

// 打包不了
private struct AIContentAnalysisToggle: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    // 使用SensitiveContentAnalysis框架
    @available(iOS 17.0, *)
    private let analyzer = SCSensitivityAnalyzer()

    private var isSystemAnalysisEnabled: Bool {
        if #available(iOS 17.0, *) {
            analyzer.analysisPolicy.rawValue != 0
        } else {
            false
        }
    }

    var body: some View {
        Toggle(isOn: Binding(
            get: {
                isSystemAnalysisEnabled && appSettings.otherSettings.sensitiveContentAnalysisEnabled
            },
            set: { newValue in
                if isSystemAnalysisEnabled {
                    appSettings.updateOther(newValue: appSettings.otherSettings.also { settings in
                        settings.sensitiveContentAnalysisEnabled = newValue
                    })
                }
            }
        )) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Sensitive Content Analysis")
                    .font(.body)
                Text(isSystemAnalysisEnabled ?
                    "Automatically detect and blur sensitive images using Apple's Sensitive Content Analysis framework runs locally on your device" :
                    "No feature enabled that is requiring Sensitive Analysis on device, analysis will be disabled. Please enable it in System Settings > Privacy & Security > Sensitive Content Warning.")
                    .font(.caption)
                    .foregroundColor(isSystemAnalysisEnabled ? .secondary : .orange)
            }
        }
        .disabled(true) //! isSystemAnalysisEnabled
    }
}
