import SwiftUI
import shared
import PhotosUI
import Kingfisher
import Awesome

// 添加 PollExpiration 枚举
enum PollExpiration: String, CaseIterable {
    case minutes5
    case minutes30
    case hours1
    case hours6
    case hours12
    case days1
    case days3
    case days7
    
    var localizedKey: String {
        switch self {
        case .minutes5: return "compose_poll_expiration_5_minutes"
        case .minutes30: return "compose_poll_expiration_30_minutes"
        case .hours1: return "compose_poll_expiration_1_hour"
        case .hours6: return "compose_poll_expiration_6_hours"
        case .hours12: return "compose_poll_expiration_12_hours"
        case .days1: return "compose_poll_expiration_1_day"
        case .days3: return "compose_poll_expiration_3_days"
        case .days7: return "compose_poll_expiration_7_days"
        }
    }
}

struct ComposeScreen: View {
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @State var viewModel: ComposeViewModel
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    let onBack: () -> Void
    init(onBack: @escaping () -> Void, accountType: AccountType, status: ComposeStatus? = nil) {
        self.onBack = onBack
        _viewModel = .init(initialValue: .init(accountType: accountType, status: status))
    }
    var body: some View {
        FlareTheme {
            HStack(alignment: .top) {
                VStack(alignment: .leading) {
                    ScrollView(.vertical) {
                        VStack(alignment: .leading) {
                            if viewModel.enableCW {
                                TextField(text: $viewModel.contentWarning) {
                                    Text("compose_content_warning_hint")
                                }
                                .textFieldStyle(.plain)
                                .focused($cwKeyboardFocused)
                                Divider()
                            }
                            TextField(text: $viewModel.text) {
                                Text("compose_hint")
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
                                                            Label {
                                                                Text("delete")
                                                            } icon: {
                                                                Awesome.Classic.Solid.trash.image
                                                            }
                                                        })
                                                    }
#else
                                                Menu {
                                                    Button(action: {
                                                        withAnimation {
                                                            viewModel.mediaViewModel.remove(item: item)
                                                        }
                                                    }, label: {
                                                        Label {
                                                            Text("delete")
                                                        } icon: {
                                                            Awesome.Classic.Solid.trash.image
                                                        }
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
                                    Text("compose_media_sensitive")
                                })
                            }
                            if viewModel.pollViewModel.enabled {
                                HStack {
                                    Picker("compose_poll_type", selection: $viewModel.pollViewModel.pollType) {
                                        Text("compose_poll_single_choice")
                                            .tag(ComposePollType.single)
                                        Text("compose_poll_multiple_choice")
                                            .tag(ComposePollType.multiple)
                                    }
                                    .pickerStyle(.segmented)
                                    Button {
                                        withAnimation {
                                            viewModel.pollViewModel.add()
                                        }
                                    } label: {
                                        Awesome.Classic.Solid.plus.image
                                    }.disabled(viewModel.pollViewModel.choices.count >= 4)
                                }
#if os(iOS)
                                .padding(.vertical)
#endif
                                ForEach(Array(viewModel.pollViewModel.choices.enumerated()), id: \.element.id) { index, choice in
                                    HStack {
                                        TextField(text: $viewModel.pollViewModel.choices[index].text) {
                                            Text(String(format: NSLocalizedString("compose_poll_option_hint", comment: ""), String(index + 1)))
                                        }
                                        .textFieldStyle(.roundedBorder)
                                        Button {
                                            withAnimation { 
                                                viewModel.pollViewModel.remove(choice: choice)
                                            }
                                        } label: {
                                            Awesome.Classic.Solid.deleteLeft.image
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
                                                Text(NSLocalizedString(PollExpiration(rawValue: expiration.rawValue)?.localizedKey ?? "", comment: ""))
                                            })
                                        }
                                    } label: {
                                        Text(String(
                                            format: NSLocalizedString("compose_poll_expiration_at", comment: ""),
                                            NSLocalizedString(PollExpiration(rawValue: viewModel.pollViewModel.expired.rawValue)?.localizedKey ?? "", comment: "")
                                        ))
                                    }
                                }
                            }
                            if let replyState = viewModel.model.replyState,
                               case .success(let reply) = onEnum(of: replyState),
                               let content = reply.data.content as? UiTimelineItemContentStatus {
                                Spacer()
                                    .frame(height: 8)
                                QuotedStatus(
                                    data: content,
                                    onMediaClick: { _, _ in }
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
                                    Awesome.Classic.Solid.image.image
                                        .frame(width: iconSize, height: iconSize)
                                }
                            }
                            if viewModel.mediaViewModel.selectedItems.count == 0,
                            case .success(let data) = onEnum(of: viewModel.model.composeConfig),
                               let poll = data.data.poll {
                                Button(action: {
                                    withAnimation {
                                        viewModel.togglePoll()
                                    }
                                }, label: {
                                    Awesome.Classic.Solid.squarePollHorizontal.image
                                        .frame(width: iconSize, height: iconSize)
                                })
                            }
                            if case .success(let visibilityState) = onEnum(of: viewModel.model.visibilityState) {
                                Menu {
                                    ForEach(visibilityState.data.allVisibilities, id: \.self) { visibility in
                                        Button {
                                            visibilityState.data.setVisibility(value: visibility)
                                        } label: {
                                            Text(visibility.name)
                                        }

                                    }
                                } label: {
                                    StatusVisibilityComponent(visibility: visibilityState.data.visibility)
                                        .frame(width: iconSize, height: iconSize)
                                }
                            }
                            if case .success(let data) = onEnum(of: viewModel.model.composeConfig),
                                data.data.contentWarning != nil {
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
                                    Awesome.Classic.Solid.circleExclamation.image
                                        .frame(width: iconSize, height: iconSize)
                                })
                            }
                            if case .success = onEnum(of: viewModel.model.emojiState) {
                                Button(action: {
                                    viewModel.showEmojiPanel()
                                }, label: {
                                    Awesome.Classic.Solid.faceSmile.image
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
                                                         
                                                            KFImage(URL(string: item.url))
                                                                .resizable()
                                                                .scaledToFit()
                                                         
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
                    Awesome.Classic.Solid.paperPlane.image
                        .foregroundColor(.init(.accentColor))
                })
            }
            ToolbarItem(placement: .cancellationAction) {
                Button(role: .cancel, action: {
                    onBack()
                }, label: {
                    Awesome.Classic.Solid.xmark.image
                        .foregroundColor(.init(.accentColor))
                })
            }
            ToolbarItem(placement: .principal) {
                Text("compose_title")
            }
        }
    }
}
