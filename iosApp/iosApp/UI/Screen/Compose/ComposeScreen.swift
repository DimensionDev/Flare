import SwiftUI
import shared
import PhotosUI
import NetworkImage

struct ComposeScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State var viewModel: ComposeViewModel
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    let onBack: () -> Void
    init(onBack: @escaping () -> Void, status: ComposeStatus? = nil) {
        self.onBack = onBack
        _viewModel = State(initialValue: ComposeViewModel(status: status))
    }
    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading) {
                ScrollView(.vertical) {
                    VStack(alignment: .leading) {
                        if viewModel.enableCW {
                            TextField(text: $viewModel.contentWarning) {
                                Text("Content Warning")
                            }
                            .focused($cwKeyboardFocused)
                        }
                        TextField(text: $viewModel.text) {
                            Text("What's happening?")
                        }
                        .focused($keyboardFocused)
                        .onAppear {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                keyboardFocused = true
                            }
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
                        if let replyState = viewModel.model.replyState,
                           case .success(let reply) = onEnum(of: replyState),
                           reply.data.itemCount > 0,
                           let replyStatus = reply.data.get(index: 0) {
                            QuotedStatus(data: replyStatus, onMediaClick: { _ in })
                        }
                    }
                }
                Spacer()
                HStack {
                    if !viewModel.pollViewModel.enabled {
                        PhotosPicker(selection: Binding(get: {
                            viewModel.mediaViewModel.selectedItems
                        }, set: { value in
                            viewModel.mediaViewModel.selectedItems = value
                            viewModel.mediaViewModel.update()
                        }), matching: .any(of: [.images, .videos, .livePhotos])) {
                            Image(systemName: "photo")
                        }
                    }
                    if viewModel.mediaViewModel.selectedItems.count == 0,
                        case .success(let canPoll) = onEnum(of: viewModel.model.canPoll),
                       canPoll.data == KotlinBoolean(bool: true) {
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
                                        MisskeyVisibilityIcon(visibility: visibility)
                                        Text(visibility.name)
                                    }
                                }
                            } label: {
                                MisskeyVisibilityIcon(visibility: misskeyVisibility.visibility)
                            }
                        case .mastodonVisibilityState(let mastodonVisibility):
                            Menu {
                                ForEach(mastodonVisibility.allVisibilities, id: \.self) { visibility in
                                    Button {
                                        mastodonVisibility.setVisibility(value: visibility)
                                    } label: {
                                        MastodonVisibilityIcon(visibility: visibility)
                                        Text(visibility.name)
                                    }
                                }
                            } label: {
                                MastodonVisibilityIcon(visibility: mastodonVisibility.visibility)
                            }
                        }
                    }
                    if case .success(let canCW) = onEnum(of: viewModel.model.canCW),
                       canCW.data == KotlinBoolean(bool: true) {
                        Button(action: {
                            withAnimation {
                                viewModel.toggleCW()
                                if viewModel.enableCW {
                                    cwKeyboardFocused = true
                                } else {
                                    keyboardFocused = true
                                }
                            }
                        }, label: {
                            Image(systemName: "exclamationmark.triangle")
                        })
                    }
                    if case .success = onEnum(of: viewModel.model.emojiState) {
                        Button(action: {
                            viewModel.showEmojiPanel()
                        }, label: {
                            Image(systemName: "face.smiling")
                        })
                        .popover(isPresented: $viewModel.showEmoji) {
                            if case .success(let emojis) = onEnum(of: viewModel.model.emojiState) {
                                ScrollView {
                                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                                        ForEach(1...emojis.data.count, id: \.self) { index in
                                            if let item = emojis.data[index - 1] as? UiEmoji {
                                                Button(action: {
                                                    viewModel.addEmoji(emoji: item)
                                                }, label: {
                                                    NetworkImage(url: URL(string: item.url)) { image in
                                                        image.resizable().scaledToFit()
                                                    }
                                                })
                                            }
                                        }
                                    }
                                    .padding()
                                }
                                .if(horizontalSizeClass != .compact, transform: { view in
                                    view
                                        .frame(maxWidth: 300, maxHeight: 200)
                                })
                            }
                        }
                    }
                }
            }
        }
        .activateViewModel(viewModel: viewModel)
        .padding()
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {
                    viewModel.send()
                    onBack()
                }, label: {
                    Image(systemName: "paperplane")
                })
            }
            ToolbarItem(placement: .cancellationAction) {
                Button(role: .cancel, action: {
                    onBack()
                }, label: {
                    Image(systemName: "xmark")
                })
            }
            ToolbarItem(placement: .principal) {
                Text("Compose")
            }
        }
    }
}

#Preview {
    NavigationStack {
        ComposeScreen(onBack: {})
    }
}
