import SwiftUI
import shared
import PhotosUI

struct ComposeScreen: View {
    @State var viewModel: ComposeViewModel

    init(status: ComposeStatus? = nil) {
        viewModel = ComposeViewModel(status: status)
    }

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading) {
                if viewModel.enableCW {
                    TextField(text: $viewModel.cw) {
                        Text("Content Warning")
                    }
                }
                TextField(text: $viewModel.text) {
                    Text("What's happening?")
                }
                if viewModel.mediaViewModel.items.count > 0 {
                    ScrollView(.horizontal) {
                        HStack {
                            ForEach(viewModel.mediaViewModel.items, id: \.item) { item in
                                if let image = item.image {
                                    Menu {
                                        Button(action: {
                                            withAnimation {
                                                viewModel.mediaViewModel.remove(item: item)
                                            }
                                        }, label: {
                                            Text("Delete")
                                            Image(systemName: "trash")
                                        })
                                    } label: {
                                        Image(uiImage: image)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 128, height: 128)
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                    }
                    Toggle(isOn: $viewModel.mediaViewModel.sensitive, label: {
                        Text("Mark media as sensitive")
                    })
                }
                if viewModel.pollViewModel.enabled {
                    HStack {
                        Picker("NotificationType", selection: $viewModel.pollViewModel.pollType) {
                            Text("Single Choice")
                                .tag(ComposePollType.single)
                            Text("Multiple Choice")
                                .tag(ComposePollType.multiple)
                        }
                        .pickerStyle(.segmented)
                        Button {
                            withAnimation {
                                viewModel.pollViewModel.add()
                            }
                        } label: {
                            Image(systemName: "plus")
                        }.disabled(viewModel.pollViewModel.choices.count >= 4)
                    }
                    ForEach($viewModel.pollViewModel.choices) { $choice in
                        HStack {
                            TextField(text: $choice.text) {
                                Text("option")
                            }
                            Button {
                                withAnimation {
                                    viewModel.pollViewModel.remove(choice: choice)
                                }
                            } label: {
                                Image(systemName: "delete.left")
                            }
                            .disabled(viewModel.pollViewModel.choices.count <= 2)
                        }
                    }
                    HStack {
                        Spacer()
                        Menu {
                            ForEach(viewModel.pollViewModel.allExpiration, id: \.self) { expiration in
                                Button(action: {
                                    viewModel.pollViewModel.expired = expiration
                                }, label: {
                                    Text(expiration.rawValue)
                                })
                            }
                        } label: {
                            Text("expiration: ")
                            Text(viewModel.pollViewModel.expired.rawValue)
                        }
                    }

                }
                Spacer()
            }
        }
        .activateViewModel(viewModel: viewModel)
        .padding()
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {
                }, label: {
                    Image(systemName: "paperplane")
                })
            }
            ToolbarItem(placement: .cancellationAction) {
                Button(action: {}, label: {
                    Image(systemName: "xmark")
                })
            }
            ToolbarItem(placement: .principal) {
                Text("Compose")
            }
            ToolbarItem(placement: .bottomBar) {
                ScrollView(.horizontal) {
                    HStack {
                        if !viewModel.pollViewModel.enabled {
                            PhotosPicker(selection: Binding(get: {
                                viewModel.mediaViewModel.selectedItems
                            }, set: { Value in
                                viewModel.mediaViewModel.selectedItems = Value
                                viewModel.mediaViewModel.update()
                            }), matching: .any(of: [.images, .videos,.livePhotos])) {
                                Image(systemName: "photo")
                            }
                        }
                        if viewModel.mediaViewModel.selectedItems.count == 0, case .success(let canPoll) = onEnum(of: viewModel.model.canPoll), canPoll.data == KotlinBoolean(bool: true) {
                            Button(action: {
                                withAnimation {
                                    viewModel.togglePoll()
                                }
                            }, label: {
                                Image(systemName: "list.bullet")
                            })
                        }
                        if case .success(let visibilityState) = onEnum(of: viewModel.model.visibilityState) {
                            switch onEnum(of: visibilityState.data) {
                            case .misskeyVisibilityState(let misskeyVisibility):
                                Menu {
                                    ForEach(misskeyVisibility.allVisibilities, id: \.self) { visibility in
                                        Button {
                                            misskeyVisibility.setVisibility(value: visibility)
                                        } label: {
                                            Text(visibility.name)
                                        }

                                    }
                                } label: {
                                    Text(misskeyVisibility.visibility.name)
                                }
                            case .mastodonVisibilityState(let mastodonVisibility):
                                Menu {
                                    ForEach(mastodonVisibility.allVisibilities, id: \.self) { visibility in
                                        Button {
                                            mastodonVisibility.setVisibility(value: visibility)
                                        } label: {
                                            Text(visibility.name)
                                        }

                                    }
                                } label: {
                                    Text(mastodonVisibility.visibility.name)
                                }
                            }
                        }
                        if case .success(let canCW) = onEnum(of: viewModel.model.canCW), canCW.data == KotlinBoolean(bool: true) {
                            Button(action: {
                                withAnimation {
                                    viewModel.toggleCW()
                                }
                            }, label: {
                                Image(systemName: "exclamationmark.triangle")
                            })
                        }
                        if case .success(_) = onEnum(of: viewModel.model.emojiState) {
                            Button(action: {}, label: {
                                Image(systemName: "face.smiling")
                            })
                        }
                        Spacer()
                    }
                }
            }
        }

    }
}

#Preview {
    NavigationStack {
        ComposeScreen()
    }
}
