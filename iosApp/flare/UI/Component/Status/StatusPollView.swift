import SwiftUI
import KotlinSharedUI

struct StatusPollView: View {
    let data: UiPoll
    @State private var selectedOption: [Int] = []
    var body: some View {
        VStack(
            alignment: .trailing
        ) {
            ForEach(0..<data.options.count, id: \.self) { index in
                let option = data.options[index]
                if data.canVote {
                    Button {
                        if data.multiple {
                            if selectedOption.contains(index) {
                                selectedOption.removeAll(where: { $0 == index })
                            } else {
                                selectedOption.append(index)
                            }
                        } else {
                            selectedOption = [index]
                        }
                    } label: {
                        HStack {
                            Text(option.title)
                                .font(.caption)
                            Spacer()
                            if selectedOption.contains(index) {
                                Image(systemName: "checkmark.circle")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            } else {
                                EmptyView()
                            }
                        }
                        .padding(8)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .fill(selectedOption.contains(index) ? Color.accentColor.opacity(0.2) : Color(.systemGroupedBackground))
                        )
                    }
                    .buttonStyle(.plain)
                } else {
                    VStack {
                        HStack {
                            Text(option.title)
                                .font(.caption)
                            Spacer()
                            if data.ownVotes.contains(KotlinInt(value: Int32(index))) {
                                Image(systemName: "checkmark.circle")
                                    .font(.caption)
                            } else {
                                EmptyView()
                            }
                            Text(option.humanizedPercentage)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: option.percentage)
                            .progressViewStyle(.linear)
                            .tint(.accentColor)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            
            if data.expired {
                Text("poll_expired")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if let expiredAt = data.expiredAt {
                Text("poll_expires_at")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                DateTimeText(data: expiredAt, fullTime: true)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            
            if data.canVote {
                Button {
                    data.onVote(selectedOption.map { KotlinInt(value: Int32($0)) } )
                } label: {
                    Text("poll_vote")
                }
                .buttonStyle(.glassProminent)
            }
        }
    }
}

