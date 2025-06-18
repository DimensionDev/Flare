import SwiftUI

struct SensitiveContentSection: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme

    // 1. "Hide sensitive images and videos" - 完全独立的媒体遮罩功能
    // 2. "Hide sensitive content in timeline" - 主功能，控制timeline中敏感推文的显示
    // 3. "Set timeline sensitive time range" - 次级功能，只有开启timeline隐藏时才显示

    var body: some View {
        Section {
            DisclosureGroup(
                isExpanded: Binding(
                    get: { !appSettings.appearanceSettings.sensitiveContentSettings.isCollapsed },
                    set: { isExpanded in
                        let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.isCollapsed, to: !isExpanded)
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
                    }
                )
            ) {
                VStack(spacing: 0) {
                    HStack {
                        Image(systemName: "eye.slash")
                            .foregroundColor(theme.tintColor)
                            .frame(width: 24, height: 24)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Hide sensitive images and videos")
                                .font(.body)
                                .foregroundColor(theme.labelColor)
                            Text("Add blur overlay to sensitive media content")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }

                        Spacer()

                        Toggle("", isOn: Binding(
                            get: { !appSettings.appearanceSettings.showSensitiveContent },
                            set: { value in
                                appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: !value))
                            }
                        ))
                        .labelsHidden()
                    }
                    .padding(.vertical, 12)

                    Divider()
                        .background(theme.secondaryBackgroundColor)

                    HStack {
                        Image(systemName: "eye.slash")
                            .foregroundColor(theme.tintColor)
                            .frame(width: 24, height: 24)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("Hide sensitive content in timeline")
                                .font(.body)
                                .foregroundColor(theme.labelColor)

                            if appSettings.appearanceSettings.sensitiveContentSettings.timeRange != nil {
                                Text("Hide sensitive posts during set time range")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            } else {
                                Text("Always hide sensitive posts from timeline")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }

                        Spacer()

                        Toggle("", isOn: Binding(
                            get: { appSettings.appearanceSettings.sensitiveContentSettings.hideInTimeline },
                            set: { value in
                                let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.hideInTimeline, to: value)
                                appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
                            }
                        ))
                        .labelsHidden()
                    }
                    .padding(.vertical, 12)

                    if appSettings.appearanceSettings.sensitiveContentSettings.hideInTimeline {
                        Divider()
                            .background(theme.secondaryBackgroundColor)
                            .padding(.leading, 40)

                        HStack {
                            Image(systemName: "clock")
                                .foregroundColor(theme.tintColor)
                                .frame(width: 24, height: 24)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Set timeline sensitive time range")
                                    .font(.body)
                                    .foregroundColor(theme.labelColor)

                                if let timeRange = appSettings.appearanceSettings.sensitiveContentSettings.timeRange {
                                    Text(timeRange.displayText)
                                        .font(.caption)
                                        .foregroundColor(theme.tintColor)
                                } else {
                                    Text("Tap to set specific time range (optional)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()

                            if appSettings.appearanceSettings.sensitiveContentSettings.timeRange != nil {
                                Button(action: {
                                    removeTimeRange()
                                }) {
                                    Image(systemName: "xmark")
                                        .foregroundColor(.secondary)
                                        .frame(width: 20, height: 20)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(.vertical, 12)
                        .padding(.leading, 20) // 缩进以显示层级关系
                        .contentShape(Rectangle())
                        .onTapGesture {
                            let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.isShowingTimePicker, to: true)
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
                        }
                    }
                }
            } label: {
                HStack {
                    Image(systemName: "eye.slash.circle")
                        .foregroundColor(theme.tintColor)
                        .frame(width: 24, height: 24)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Sensitive Content")
                            .font(.body)
                            .foregroundColor(theme.labelColor)
                        Text("Manage sensitive content display settings")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()
                }
            }
        }
        .listRowBackground(theme.primaryBackgroundColor)
    }

    private func removeTimeRange() {
        let newSettings = appSettings.appearanceSettings.sensitiveContentSettings.changing(path: \.timeRange, to: nil)
        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.sensitiveContentSettings, to: newSettings))
    }
}
