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
        FlareTheme {
            HStack(alignment: .top) {
                VStack(alignment: .leading) {
                    ScrollView(.vertical) {
                        VStack(alignment: .leading) {
                            if viewModel.enableCW {
                                TextField(text: $viewModel.contentWarning) {
                                    Text("compose_cw_placeholder")
                                }
                                .textFieldStyle(.plain)
                                .focused($cwKeyboardFocused)
                                Divider()
                            }
                            TextField(text: $viewModel.text) {
                                Text("compose_placeholder")
                            }
                            .textFieldStyle(.plain)
                            .focused($keyboardFocused)
                            .onAppear {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                    keyboardFocused = true
                                }
                            }
                            if viewModel.mediaViewModel.items.count > 0 {
                                ScrollView(.horizontal) {
                                    HStack {
                                        ForEach(viewModel.mediaViewModel.items.indices, id: \.self) { index in
                                            let item = viewModel.mediaViewModel.items[index]
                                            if let image = item.image {
#if os(macOS)
                                                Image(nsImage: image)
                                                    .resizable()
                                                    .scaledToFill()
                                                    .frame(width: 128, height: 128)
                                                    .cornerRadius(8)
                                                    .contextMenu {
                                                        Button(action: {
                                                            withAnimation {
                                                                viewModel.mediaViewModel.remove(item: item)
                                                            }
                                                        }, label: {
                                                            Text("delete")
                                                            Image(systemName: "trash")
                                                        })
                                                    }
#else
                                                Menu {
                                                    Button(action: {
                                                        withAnimation {
                                                            viewModel.mediaViewModel.remove(item: item)
                                                        }
                                                    }, label: {
                                                        Text("delete")
                                                        Image(systemName: "trash")
                                                    })
                                                } label: {
                                                    Image(uiImage: image)
                                                        .resizable()
                                                        .scaledToFill()
                                                        .frame(width: 128, height: 128)
                                                        .cornerRadius(8)
                                                }
#endif
                                            }
                                        }
                                    }
                                }
                                Toggle(isOn: $viewModel.mediaViewModel.sensitive, label: {
                                    Text("compose_media_mark_sensitive")
                                })
                            }
                            if viewModel.pollViewModel.enabled {
                                HStack {
                                    Picker("compose_poll_type", selection: $viewModel.pollViewModel.pollType) {
                                        Text("compose_poll_type_single")
                                            .tag(ComposePollType.single)
                                        Text("compose_poll_type_multiple")
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
#if os(iOS)
                                .padding(.vertical)
#endif
                                ForEach($viewModel.pollViewModel.choices) { $choice in
                                    HStack {
                                        TextField(text: $choice.text) {
                                            Text("compose_poll_choice_placeholder")
                                        }
                                        .textFieldStyle(.roundedBorder)
                                        Button {
                                            withAnimation {
                                                viewModel.pollViewModel.remove(choice: choice)
                                            }
                                        } label: {
                                            Image(systemName: "delete.left")
                                        }
                                        .disabled(viewModel.pollViewModel.choices.count <= 2)
                                    }
#if os(iOS)
                                    .padding(.bottom)
#endif
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
                                        Text("compose_poll_expiration")
                                        Text(viewModel.pollViewModel.expired.rawValue)
                                    }
                                }
                            }
                            if let replyState = viewModel.model.replyState,
                               case .success(let reply) = onEnum(of: replyState) {
                                Spacer()
                                    .frame(height: 8)
                                QuotedStatus(
                                    data: reply.data,
                                    onMediaClick: { _, _ in },
                                    onUserClick: { _ in },
                                    onStatusClick: { _ in }
                                )
                            }
                        }
                        .padding()
                    }
                    let iconSize: CGFloat = 24
                    Divider()
                    ScrollView(.horizontal, content: {
                        HStack {
                            if !viewModel.pollViewModel.enabled {
                                PhotosPicker(selection: Binding(get: {
                                    viewModel.mediaViewModel.selectedItems
                                }, set: { value in
                                    viewModel.mediaViewModel.selectedItems = value
                                    viewModel.mediaViewModel.update()
                                }), matching: .any(of: [.images, .videos, .livePhotos])) {
                                    Image(systemName: "photo")
                                        .frame(width: iconSize, height: iconSize)
                                }
                            }
                            if viewModel.mediaViewModel.selectedItems.count == 0,
                            case .success(let data) = onEnum(of: viewModel.model.supportedComposeEvent),
                               data.data.contains(element: SupportedComposeEvent.poll.toKotlinEnum()) {
                                Button(action: {
                                    withAnimation {
                                        viewModel.togglePoll()
                                    }
                                }, label: {
                                    Image(systemName: "list.bullet")
                                        .frame(width: iconSize, height: iconSize)
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
                                            .frame(width: iconSize, height: iconSize)
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
                                            .frame(width: iconSize, height: iconSize)
                                    }
                                }
                            }
                            if case .success(let data) = onEnum(of: viewModel.model.supportedComposeEvent),
                               data.data.contains(element: SupportedComposeEvent.contentWarning.toKotlinEnum()) {
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
                                        .frame(width: iconSize, height: iconSize)
                                })
                            }
                            if case .success = onEnum(of: viewModel.model.emojiState) {
                                Button(action: {
                                    viewModel.showEmojiPanel()
                                }, label: {
                                    Image(systemName: "face.smiling")
                                        .frame(width: iconSize, height: iconSize)
                                })
                                .popover(isPresented: $viewModel.showEmoji) {
                                    if case .success(let emojis) = onEnum(of: viewModel.model.emojiState) {
                                        ScrollView {
                                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                                                ForEach(1...emojis.data.size, id: \.self) { index in
                                                    let item = emojis.data.get(index: index - 1)
                                                    Button(action: {
                                                        viewModel.addEmoji(emoji: item)
                                                    }, label: {
                                                        NetworkImage(url: URL(string: item.url)) { image in
                                                            image.resizable().scaledToFit()
                                                        }
                                                    })
                                                    .buttonStyle(.plain)

                                                }
                                            }
                                            .if(horizontalSizeClass == .compact, transform: { view in
                                                view
                                                    .padding()
                                            })
                                        }
                                        .if(horizontalSizeClass != .compact, transform: { view in
                                            view
                                                .frame(width: 384, height: 256)
                                        })
                                    }
                                }
                            }
                        }
                        .padding([.bottom, .horizontal])
                    })
                    .buttonStyle(.bordered)
                }
            }
        }
        .activateViewModel(viewModel: viewModel)
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
                Text("compose_title")
            }
        }
    }
}

#Preview {
    NavigationStack {
        ComposeScreen(onBack: {})
    }
}
