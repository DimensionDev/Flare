import SwiftUI
import UIKit

struct AboutScreen: View {
    @Environment(\.openURL) private var openURL

    private var version: String {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""

        switch (versionName.isEmpty, buildNumber.isEmpty) {
        case (false, false):
            return "\(versionName) (\(buildNumber))"
        case (false, true):
            return versionName
        case (true, false):
            return buildNumber
        case (true, true):
            return ""
        }
    }

    private var iconName: String {
        AppIconOption.previewImageName(for: UIApplication.shared.alternateIconName)
    }

    var body: some View {
        List {
            Section {
                AboutHeader(iconName: iconName, version: version)
            }
            .listRowInsets(EdgeInsets(top: 28, leading: 20, bottom: 28, trailing: 20))
            .listRowBackground(Color.clear)

            Section {
                ForEach(AboutLink.all) { item in
                    Button {
                        UIApplication.shared.open(item.url)
                    } label: {
                        AboutLinkRow(item: item)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .navigationTitle("about_title")
    }
}

private struct AboutHeader: View {
    let iconName: String
    let version: String

    var body: some View {
        VStack(spacing: 14) {
            Image(iconName)
                .resizable()
                .aspectRatio(1, contentMode: .fit)
                .frame(width: 104, height: 104)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                .shadow(color: .black.opacity(0.12), radius: 16, y: 8)

            VStack(spacing: 6) {
                Text("Flare")
                    .font(.largeTitle.weight(.semibold))

                Text("settings_about_description")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)

                if !version.isEmpty {
                    Text(verbatim: version)
                        .font(.footnote.monospacedDigit())
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}

private struct AboutLinkRow: View {
    let item: AboutLink

    var body: some View {
        HStack(spacing: 14) {
            Image(item.iconName)
                .resizable()
                .scaledToFit()
                .foregroundStyle(.tint)
                .frame(width: 20, height: 20)
                .frame(width: 28, height: 28)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.titleKey)
                    .foregroundStyle(.primary)

                Text(item.subtitleKey)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            Spacer(minLength: 12)

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(Rectangle())
    }
}

private struct AboutLink: Identifiable {
    let id: String
    let titleKey: LocalizedStringKey
    let subtitleKey: LocalizedStringKey
    let iconName: String
    let url: URL
}

private extension AboutLink {
    static let all: [AboutLink] = [
        AboutLink(
            id: "source-code",
            titleKey: "settings_about_source_code",
            subtitleKey: "https://github.com/DimensionDev/Flare",
            iconName: "fa-github",
            url: URL(string: "https://github.com/DimensionDev/Flare")!
        ),
        AboutLink(
            id: "telegram",
            titleKey: "settings_about_telegram",
            subtitleKey: "settings_about_telegram_description",
            iconName: "fa-telegram",
            url: URL(string: "https://t.me/+VZ63fqNQXIA0MzVl")!
        ),
        AboutLink(
            id: "discord",
            titleKey: "settings_about_discord",
            subtitleKey: "settings_about_discord_description",
            iconName: "fa-discord",
            url: URL(string: "https://discord.gg/De9NhXBryT")!
        ),
        AboutLink(
            id: "localization",
            titleKey: "settings_about_localization",
            subtitleKey: "settings_about_localization_description",
            iconName: "fa-language",
            url: URL(string: "https://crowdin.com/project/flareapp")!
        ),
        AboutLink(
            id: "privacy-policy",
            titleKey: "settings_privacy_policy",
            subtitleKey: "https://legal.mask.io/maskbook",
            iconName: "fa-lock",
            url: URL(string: "https://legal.mask.io/maskbook/")!
        )
    ]
}
