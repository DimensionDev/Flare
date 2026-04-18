import SwiftUI
import KotlinSharedUI
import FlareUI

struct DraftBoxScreen: View {
    let onEditDraft: (String) -> Void
    @StateObject private var presenter = KotlinPresenter(presenter: DraftBoxPresenter())

    var body: some View {
        Group {
            if presenter.state.items.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("No Drafts")
                    } icon: {
                        Image(.faInbox)
                    }
                }             
            } else {
                List {
                    ForEach(presenter.state.items, id: \.groupId) { draft in
                        DraftItemView(
                            draft: draft,
                            onRetry: {
                                presenter.state.retry(groupId: draft.groupId)
                            },
                            onSend: {
                                presenter.state.send(groupId: draft.groupId)
                            },
                            onDelete: {
                                presenter.state.delete(groupId: draft.groupId)
                            },
                            onEdit: {
                                onEditDraft(draft.groupId)
                            }
                        )
                    }
                }
                .animation(.snappy, value: presenter.state.items.count)
            }
        }
        .navigationTitle("Drafts")
    }
}

struct DraftItemView: View {
    let draft: UiDraft
    let onRetry: () -> Void
    let onSend: () -> Void
    let onDelete: () -> Void
    let onEdit: () -> Void

    var body: some View {
        Button(action: onEdit) {
            HStack(alignment: .top, spacing: 12) {
                if let statusIcon = statusIcon {
                    Image(systemName: statusIcon)
                        .foregroundStyle(statusColor)
                        .frame(width: 20, height: 20)
                        .padding(.top, 2)
                }

                VStack(alignment: .leading, spacing: 10) {
                    if !draft.accounts.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: 6) {
                                ForEach(Array(draft.accounts.enumerated()), id: \.offset) { _, account in
                                    if let avatar = account.avatar {
                                        NetworkImage(data: avatar)
                                            .frame(width: 22, height: 22)
                                            .clipShape(Circle())
                                    } else {
                                        Image(systemName: "person.crop.circle.fill")
                                            .foregroundStyle(.secondary)
                                            .frame(width: 22, height: 22)
                                    }
                                }
                            }
                        }
                        .scrollIndicators(.hidden)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        if let spoilerText = draft.data.spoilerText, !spoilerText.isEmpty {
                            Text(spoilerText)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }

                        if !draft.data.content.isEmpty {
                            Text(draft.data.content)
                                .foregroundStyle(.primary)
                                .multilineTextAlignment(.leading)
                                .lineLimit(2)
                        } else {
                            Text(previewText)
                                .foregroundStyle(.primary)
                                .multilineTextAlignment(.leading)
                                .lineLimit(2)
                        }
                    }

                    if !draft.medias.isEmpty {
                        ScrollView(.horizontal) {
                            HStack(spacing: 8) {
                                ForEach(Array(draft.medias.prefix(4).enumerated()), id: \.offset) { _, media in
                                    DraftMediaThumbnail(media: media)
                                }
                            }
                        }
                        .scrollIndicators(.hidden)
                    }

                    DateTimeText(data: draft.updatedAt, fullTime: true)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer(minLength: 0)

                if draft.status != .sending {
                    Menu {
                        if draft.status == .draft {
                            Button {
                                onSend()
                            } label: {
                                Label("Send", systemImage: "paperplane.fill")
                            }
                        }

                        if draft.status == .failed {
                            Button {
                                onRetry()
                            } label: {
                                Label("Retry", systemImage: "arrow.clockwise")
                            }
                        }

                        Button {
                            onEdit()
                        } label: {
                            Label("Edit", systemImage: "square.and.pencil")
                        }

                        Button(role: .destructive) {
                            onDelete()
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    } label: {
                        Image(.faEllipsisVertical)
                            .foregroundStyle(.secondary)
                            .frame(width: 28, height: 28)
                    }
                    .buttonStyle(.plain)
                }
            }
            .opacity(draft.status == .sending ? 0.6 : 1)
        }
        .buttonStyle(.plain)
        .disabled(draft.status == .sending)
    }

    private var previewText: String {
        if !draft.data.content.isEmpty {
            return draft.data.content
        }
        if let spoilerText = draft.data.spoilerText, !spoilerText.isEmpty {
            return spoilerText
        }
        if !draft.medias.isEmpty {
            return "\(draft.medias.count) attachment\(draft.medias.count == 1 ? "" : "s")"
        }
        return "Empty draft"
    }

    private var statusIcon: String? {
        switch draft.status {
        case .failed:
            "exclamationmark.triangle.fill"
        case .sending:
            "arrow.up.circle.fill"
        default:
            nil
        }
    }

    private var statusColor: Color {
        switch draft.status {
        case .failed:
            .red
        case .sending:
            .green
        default:
            .secondary
        }
    }
}

private struct DraftMediaThumbnail: View {
    let media: UiDraftMedia

    var body: some View {
        Group {
            switch media.type {
            case .image:
                if let image = UIImage(contentsOfFile: media.cachePath) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                } else {
                    placeholder(icon: "photo")
                }
            case .video:
                placeholder(icon: "video")
            case .other:
                placeholder(icon: "doc")
            default:
                placeholder(icon: "doc")
            }
        }
        .frame(width: 60, height: 60)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private func placeholder(icon: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color(.tertiarySystemFill))
            Image(systemName: icon)
                .foregroundStyle(.secondary)
        }
    }
}
