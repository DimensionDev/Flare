import SwiftUI
@preconcurrency import KotlinSharedUI
import PhotosUI

struct CreateListScreen: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var presenter: KotlinPresenter<CreateListState>
    @State private var avatar: PhotosPickerItem? = nil
    @State private var listName: String = ""
    @State private var listDescription: String = ""
    @State private var selectedImage: Image?
    @State private var loading = false
    
    init(accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: CreateListPresenter(accountType: accountType)))
    }
    
    var body: some View {
        Form {
            StateView(state: presenter.state.supportedMetaData) { metadata in
                if metadata.contains(ListMetaDataType.avatar) {
                    Section("list.create.avatar") {
                        PhotosPicker(selection: $avatar, matching: .images) {
                            if let selectedImage {
                                selectedImage
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 96, height: 96)
                            } else {
                                Image(.faSquareRss)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 96, height: 96)
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
                    }
                }
            }
            Section("list.create.name") {
                TextField("list.create.name.placeholder", text: $listName)
            }
            StateView(state: presenter.state.supportedMetaData) { metadata in
                if metadata.contains(ListMetaDataType.theDescription) {
                    Section("list.create.description") {
                        TextField("list.create.description.placeholder", text: $listDescription)
                    }
                }
            }
        }
        .navigationTitle("list.create.title")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    Task {
                        loading = true
                        let imageData = try? await avatar?.loadTransferable(type: Data.self)
                        let imageByteArray = imageData != nil ? KotlinByteArray.from(data: imageData!) : nil
                        if let imageByteArray, let avatar {
                            try? await presenter.state.createList(
                                listMetaData: .init(
                                    title: self.listName,
                                    description: self.listDescription.isEmpty ? nil : self.listDescription,
                                    avatar: .init(name: avatar.itemIdentifier, data: imageByteArray, type: .image)
                                )
                            )
                        } else {
                            try? await presenter.state.createList(
                                listMetaData: .init(
                                    title: self.listName,
                                    description: self.listDescription.isEmpty ? nil : self.listDescription,
                                )
                            )
                        }
                        loading = false
                        dismiss()
                    }
                } label: {
                    Image(.faCheck)
                }
                .disabled(listName.isEmpty)
            }
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Image(.faXmark)
                }
            }
        }
        .disabled(loading)
    }
}
