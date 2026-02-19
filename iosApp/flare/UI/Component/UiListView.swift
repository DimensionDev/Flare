import SwiftUI
import KotlinSharedUI

struct UiListView: View {
    let data: UiList
    var body: some View {
        switch data {
        case let list as UiList.List:
            UiListRow(data: list)
        case let feed as UiList.Feed:
            UiFeedRow(data: feed)
        case let antenna as UiList.Antenna:
            UiAntennaRow(data: antenna)
        case let channel as UiList.Channel:
            UiChannelRow(data: channel)
        default:
            EmptyView()
        }
    }
}

private struct UiListRow: View {
    let data: UiList.List
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                Text(data.title)
            } icon: {
                if let image = data.avatar {
                    NetworkImage(data: image)
                        .frame(width: 24, height: 24)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                } else {
                    Image(systemName: "list.bullet")
                        .frame(width: 24, height: 24)
                }
            }
            if let desc = data.description_, !desc.isEmpty {
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct UiFeedRow: View {
    let data: UiList.Feed
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                Text(data.title)
            } icon: {
                if let image = data.avatar {
                    NetworkImage(data: image)
                        .frame(width: 24, height: 24)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                } else {
                    Image(systemName: "rss")
                        .frame(width: 24, height: 24)
                }
            }
            if let desc = data.description_, !desc.isEmpty {
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct UiAntennaRow: View {
    let data: UiList.Antenna
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                Text(data.title)
            } icon: {
                Image(systemName: "antenna.radiowaves.left.and.right")
                    .frame(width: 24, height: 24)
            }
        }
    }
}

private struct UiChannelRow: View {
    let data: UiList.Channel
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            Label {
                Text(data.title)
            } icon: {
                if let image = data.banner {
                    NetworkImage(data: image)
                        .frame(width: 24, height: 24)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                } else {
                    Image(systemName: "tv")
                        .frame(width: 24, height: 24)
                }
            }
            if let desc = data.description_, !desc.isEmpty {
                RichText(text: desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct UiListPlaceholder: View {
    var body: some View {
        VStack(
            alignment: .leading,
            spacing: 8
        ) {
            HStack {
                Rectangle()
                    .fill(.placeholder)
                    .frame(width: 24, height: 24)
                    .clipShape(.circle)
                Text("#loading")
            }
            Text("#loading")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .redacted(reason: .placeholder)
    }
}
