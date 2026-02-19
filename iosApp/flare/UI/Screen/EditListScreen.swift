import SwiftUI
@preconcurrency import KotlinSharedUI
import PhotosUI

struct EditListScreen: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var presenter: KotlinPresenter<ListEditPresenterState>
    private let accountType: AccountType
    private let listId: String
    @State private var title: String = ""
    @State private var desc: String = ""
    @State private var avatar: PhotosPickerItem? = nil
    @State private var selectedImage: Image?
    @State private var isLoading: Bool = false
    @State private var showEditMember: Bool = false
    
    init(accountType: AccountType, listId: String) {
        self.accountType = accountType
        self.listId = listId
        self._presenter = .init(wrappedValue: .init(presenter: ListEditPresenter(accountType: accountType, listId: listId)))
    }
    
    var body: some View {
        List {
            StateView(state: presenter.state.supportedMetaData) { supportedMetadata in
                if supportedMetadata.contains(ListMetaDataType.avatar) {
                    Section {
                        PhotosPicker(selection: $avatar, matching: .any(of: [.images, .videos, .livePhotos])) {
                            if let selectedImage {
                                selectedImage
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 96, height: 96)
                            } else {
                                StateView(state: presenter.state.listInfo) { info in
                                    let avatar: String? = switch onEnum(of: info) {
                                    case .list(let data): data.avatar
                                    case .feed(let data): data.avatar
                                    default: nil
                                    }
                                    if let remote = avatar {
                                        NetworkImage(data: remote)
                                            .frame(width: 96, height: 96)
                                    } else {
                                        Image(.faSquareRss)
                                            .resizable()
                                            .scaledToFit()
                                            .frame(width: 96, height: 96)
                                    }
                                }
                            }
                        }
                        .onChange(of: avatar) { oldItem, newItem in
                              Task {
                                  if let data = try? await newItem?.loadTransferable(type: Data.self) {
                                      if let uiImage = UIImage(data: data) {
                                          selectedImage = Image(uiImage: uiImage)
                                      }
                                  }
                              }
                          }
                    } header: {
                        Text("list_edit_avatar")
                    }
                }
                Section {
                    TextField(text: $title) {
                        Text("list_name")
                    }
                } header: {
                    Text("list_edit_name")
                }
                if supportedMetadata.contains(ListMetaDataType.theDescription) {
                    Section {
                        TextField(text: $desc) {
                            Text("list_description")
                        }
                    } header: {
                        Text("list_edit_description")
                    }
                }
            }
            Section {
                PagingView(data: presenter.state.memberInfo) { user in
                    UserCompatView(data: user) {
                        EmptyView()
                    } onClicked: {
                        
                    }
                    .contextMenu {
                        Button(role: .destructive) {
                            presenter.state.removeMember(userKey: user.key)
                        } label: {
                            Label {
                                Text("delete")
                            } icon: {
                                Image(.faTrash)
                            }
                        }
                    }
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) {
                            presenter.state.removeMember(userKey: user.key)
                        } label: {
                            Label {
                                Text("delete")
                            } icon: {
                                Image(.faTrash)
                            }
                        }
                    }
                } loadingContent: {
                    UserLoadingView()
                }
            } header: {
                Text("list_member_title")
            }
        }
        .onSuccessOf(of: presenter.state.listInfo, data: { listInfo in
            self.title = listInfo.title
            switch onEnum(of: listInfo) {
            case .list(let data): self.desc = data.description_ ?? ""
            case .feed(let data): self.desc = data.description_ ?? ""
                default: self.desc = ""
            }
        })
        .navigationTitle("list_edit_title")
        .sheet(isPresented: $showEditMember, content: {
            NavigationStack {
                EditListMemberScreen(accountType: accountType, listId: listId)
            }
        })
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image("fa-xmark")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showEditMember = true
                } label: {
                    Label {
                        Text("add")
                    } icon: {
                        Image(.faPlus)
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    Task {
                        isLoading = true
                        let imageData = try? await avatar?.loadTransferable(type: Data.self)
                        let imageByteArray = imageData != nil ? KotlinByteArray.from(data: imageData!) : nil
                        if let imageByteArray, let avatar {
                            try? await presenter.state.updateList(
                                listMetaData: .init(
                                    title: self.title,
                                    description: self.desc.isEmpty ? nil : self.desc,
                                    avatar: .init(name: avatar.itemIdentifier, data: imageByteArray, type: .image)
                                )
                            )
                        } else {
                            try? await presenter.state.updateList(
                                listMetaData: .init(
                                    title: self.title,
                                    description: self.desc.isEmpty ? nil : self.desc,
                                )
                            )
                        }
                        isLoading = false
                        dismiss()
                    }
                } label: {
                    Label {
                        Text("done")
                    } icon: {
                        Image(.faCheck)
                    }
                }
            }
        }
        .disabled(isLoading)
    }
}


