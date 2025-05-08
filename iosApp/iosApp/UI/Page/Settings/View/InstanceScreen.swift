import Kingfisher
import shared
import SwiftUI

struct InstanceScreen: View {
    private let expectedHost: String
 
    init(host: String) {
        expectedHost = host
    }

    var body: some View {
        contentView
            .navigationTitle(expectedHost)
    }

    @ViewBuilder
    private var contentView: some View {
        if UserManager.shared.instanceMetadata == nil {
            ProgressView()
        
        } else if let metadata = UserManager.shared.instanceMetadata {
            InstanceDetailView(metadata: metadata)
        } else {
            VStack {
                Text("Instance information is not available.", bundle: .main)
                    .font(.headline)
                Text("This might be because the current user is not set, the instance host is not configured, or data is still loading.")
                    .font(.caption)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .padding()
            }
        }
    }
}

private struct InstanceDetailView: View {
    let metadata: UiInstanceMetadata

    var body: some View {
        Form {
            Section {
                if let bannerUrlString = metadata.instance.bannerUrl, let bannerUrl = URL(string: bannerUrlString) {
                    KFImage(bannerUrl)
                        .placeholder {
                            Color.gray.frame(height: 100)
                        }
                        .fade(duration: 0.25)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .listRowInsets(EdgeInsets())
                }

                HStack {
                    if let iconUrlString = metadata.instance.iconUrl, let iconUrl = URL(string: iconUrlString) {
                        KFImage(iconUrl)
                            .placeholder {
                                RoundedRectangle(cornerRadius: 8).fill(Color.gray).frame(width: 50, height: 50)
                            }
                            .fade(duration: 0.25)
                            .resizable()
                            .frame(width: 50, height: 50)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    VStack(alignment: .leading) {
                        Text(metadata.instance.name)
                            .font(.title2)
                            .fontWeight(.bold)
                        Text(metadata.instance.domain)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

                if let description = metadata.instance.description_ {
                    Text(description)
                        .font(.body)
                }
                LabeledContent("Users", value: "\(metadata.instance.usersCount)")
            } header: {
                Text("Instance Information", bundle: .main)
            }

            Section {
                LabeledContent("Max Post Characters", value: "\(metadata.configuration.statuses.maxCharacters)")
                LabeledContent("Max Media Attachments", value: "\(metadata.configuration.statuses.maxMediaAttachments)")
                LabeledContent("Image Size Limit", value: formatBytes(metadata.configuration.mediaAttachment.imageSizeLimit))
                LabeledContent("Media Description Limit", value: "\(metadata.configuration.mediaAttachment.descriptionLimit)")
                if !metadata.configuration.mediaAttachment.supportedMimeTypes.isEmpty {
                    VStack(alignment: .leading) {
                        Text("Supported MIME Types:", bundle: .main)
                        Text(metadata.configuration.mediaAttachment.supportedMimeTypes.joined(separator: ", "))
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                LabeledContent("Poll: Max Options", value: "\(metadata.configuration.poll.maxOptions)")
                LabeledContent("Poll: Max Chars/Option", value: "\(metadata.configuration.poll.maxCharactersPerOption)")
                LabeledContent("Poll: Min Expiration", value: formatDuration(metadata.configuration.poll.minExpiration))
                LabeledContent("Poll: Max Expiration", value: formatDuration(metadata.configuration.poll.maxExpiration))
                LabeledContent("Registration Enabled", value: metadata.configuration.registration.enabled ? "Yes" : "No")
            } header: {
                Text("Server Configuration", bundle: .main)
            }

            if let rules = metadata.rules as? [String: String], !rules.isEmpty {
                Section {
                    ForEach(rules.sorted(by: { $0.key < $1.key }), id: \.key) { key, value in
                        VStack(alignment: .leading) {
                            Text("Rule \(key): \(value)")
                                .font(.headline)
                        }
                    }
                } header: {
                    Text("Instance Rules", bundle: .main)
                }
            }
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }

    private func formatDuration(_ seconds: Int64) -> String {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.day, .hour, .minute, .second]
        formatter.unitsStyle = .abbreviated
        return formatter.string(from: TimeInterval(seconds)) ?? "\(seconds)s"
    }
}
