import Generated
import shared
import SwiftUI

struct ListDetailView: View {
    let list: UiList
    @State private var showMembers: Bool = false
    @Environment(\.presentationMode) var presentationMode

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // 列表头部信息
            VStack(alignment: .leading, spacing: 8) {
                Text(list.title)
                    .font(.title)
                    .bold()

                if let description = list.description_, !description.isEmpty {
                    Text(description)
                        .font(.body)
                        .foregroundColor(.secondary)
                }

                HStack {
                    if let creator = list.creator {
                        Text("由 \(creator.name.raw) 创建")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    } else {
                        Text("未知创建者")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    Spacer()
                }
            }
            .padding()

            Divider()

            // 操作按钮
            VStack(spacing: 12) {
                Button(action: {
                    showMembers = true
                }) {
                    HStack {
                        Image(systemName: "person.2")
                        Text("查看成员")
                        Spacer()
                        Image(systemName: "chevron.right")
                    }
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
                }
            }
            .padding(.horizontal)

            Spacer()
        }
        .navigationBarTitle("", displayMode: .inline)
        .navigationBarBackButtonHidden(true)
        .navigationBarItems(leading: Button(action: {
            presentationMode.wrappedValue.dismiss()
        }) {
            Image(systemName: "chevron.left")
            Text("返回")
        })
        .background(
            NavigationLink(
                destination: ListMembersView(
                    accountType: AccountTypeSpecific(accountKey: list.creator?.key ?? MicroBlogKey(id: "", host: "")),
                    listId: list.id
                ),
                isActive: $showMembers
            ) {
                EmptyView()
            }
        )
    }
}
