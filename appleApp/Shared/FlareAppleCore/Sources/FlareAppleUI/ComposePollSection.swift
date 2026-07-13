import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

@Observable
public final class ComposePollViewModel {
    public static let minimumChoiceCount = 2
    public static let defaultMaxChoiceCount = 4

    public var enabled = false
    public var pollType = ComposePollType.single
    public var choices: [ComposePollChoice] = [ComposePollChoice(), ComposePollChoice()]
    public var expired = ComposePollExpiration.minutes5
    public let allExpirations = ComposePollExpiration.allCases

    public init() {}

    public func toggleEnabled() {
        if enabled {
            reset()
        } else {
            enabled = true
        }
    }

    public func reset() {
        enabled = false
        pollType = .single
        choices = [ComposePollChoice(), ComposePollChoice()]
        expired = .minutes5
    }

    public func add(maxChoices: Int = ComposePollViewModel.defaultMaxChoiceCount) {
        if canAdd(maxChoices: maxChoices) {
            choices.append(ComposePollChoice())
        }
    }

    public func canAdd(maxChoices: Int = ComposePollViewModel.defaultMaxChoiceCount) -> Bool {
        choices.count < Self.sanitizedMaxChoices(maxChoices)
    }

    public func remove(choice: ComposePollChoice) {
        if choices.count > Self.minimumChoiceCount {
            choices.removeAll { value in
                value.id == choice.id
            }
        }
    }

    public func apply(poll: ComposeData.Poll?) {
        guard let poll else {
            reset()
            return
        }

        enabled = true
        pollType = poll.multiple ? .multiple : .single
        expired = ComposePollExpiration(milliseconds: poll.expiredAfter) ?? .minutes5
        choices = poll.options.map(ComposePollChoice.init)
        while choices.count < Self.minimumChoiceCount {
            choices.append(ComposePollChoice())
        }
    }

    public func makeComposePoll() -> ComposeData.Poll? {
        guard enabled else { return nil }
        return ComposeData.Poll(
            options: choices.map(\.text),
            expiredAfter: expired.inWholeMilliseconds,
            multiple: pollType == .multiple
        )
    }

    private static func sanitizedMaxChoices(_ maxChoices: Int) -> Int {
        max(Self.minimumChoiceCount, maxChoices)
    }
}

@Observable
public final class ComposePollChoice: Identifiable {
    public let id = UUID()
    public var text = ""

    public init(text: String = "") {
        self.text = text
    }
}

public enum ComposePollType: Hashable {
    case single
    case multiple
}

public enum ComposePollExpiration: String, CaseIterable, Hashable, Identifiable {
    case minutes5
    case minutes30
    case hours1
    case hours6
    case hours12
    case days1
    case days3
    case days7

    public init?(milliseconds: Int64) {
        switch milliseconds {
        case 5 * 60 * 1000:
            self = .minutes5
        case 30 * 60 * 1000:
            self = .minutes30
        case 1 * 60 * 60 * 1000:
            self = .hours1
        case 6 * 60 * 60 * 1000:
            self = .hours6
        case 12 * 60 * 60 * 1000:
            self = .hours12
        case 24 * 60 * 60 * 1000:
            self = .days1
        case 3 * 24 * 60 * 60 * 1000:
            self = .days3
        case 7 * 24 * 60 * 60 * 1000:
            self = .days7
        default:
            return nil
        }
    }

    public var id: String { rawValue }

    public var titleKey: LocalizedStringKey {
        switch self {
        case .minutes5:
            "compose_poll_expiration_5_minutes"
        case .minutes30:
            "compose_poll_expiration_30_minutes"
        case .hours1:
            "compose_poll_expiration_1_hour"
        case .hours6:
            "compose_poll_expiration_6_hours"
        case .hours12:
            "compose_poll_expiration_12_hours"
        case .days1:
            "compose_poll_expiration_1_day"
        case .days3:
            "compose_poll_expiration_3_days"
        case .days7:
            "compose_poll_expiration_7_days"
        }
    }

    public var inWholeMilliseconds: Int64 {
        switch self {
        case .minutes5:
            5 * 60 * 1000
        case .minutes30:
            30 * 60 * 1000
        case .hours1:
            1 * 60 * 60 * 1000
        case .hours6:
            6 * 60 * 60 * 1000
        case .hours12:
            12 * 60 * 60 * 1000
        case .days1:
            24 * 60 * 60 * 1000
        case .days3:
            3 * 24 * 60 * 60 * 1000
        case .days7:
            7 * 24 * 60 * 60 * 1000
        }
    }
}

public struct ComposePollSection: View {
    @Bindable private var viewModel: ComposePollViewModel
    private let maxChoices: Int

    public init(
        viewModel: ComposePollViewModel,
        maxChoices: Int = ComposePollViewModel.defaultMaxChoiceCount
    ) {
        self.viewModel = viewModel
        self.maxChoices = max(ComposePollViewModel.minimumChoiceCount, maxChoices)
    }

    public var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Picker(selection: $viewModel.pollType) {
                    Text("compose_poll_type_single", bundle: FlareAppleUILocalization.bundle)
                        .tag(ComposePollType.single)
                    Text("compose_poll_type_multiple", bundle: FlareAppleUILocalization.bundle)
                        .tag(ComposePollType.multiple)
                } label: {
                    Text("compose_poll_type", bundle: FlareAppleUILocalization.bundle)
                }
                .pickerStyle(.segmented)

                Button {
                    withAnimation {
                        viewModel.add(maxChoices: maxChoices)
                    }
                } label: {
                    Image(fontAwesome: .plus)
                }
                .disabled(!viewModel.canAdd(maxChoices: maxChoices))
            }

            ForEach($viewModel.choices) { $choice in
                HStack(spacing: 8) {
                    TextField(text: $choice.text) {
                        Text("compose_poll_choice_placeholder", bundle: FlareAppleUILocalization.bundle)
                    }
                    .textFieldStyle(.roundedBorder)
                    .composeTextInputConfiguration()

                    Button {
                        withAnimation {
                            viewModel.remove(choice: choice)
                        }
                    } label: {
                        Image(fontAwesome: .deleteLeft)
                    }
                    .disabled(viewModel.choices.count <= ComposePollViewModel.minimumChoiceCount)
                }
            }

            HStack {
                Spacer()

                Menu {
                    ForEach(viewModel.allExpirations) { expiration in
                        Button {
                            viewModel.expired = expiration
                        } label: {
                            Text(expiration.titleKey, bundle: FlareAppleUILocalization.bundle)
                        }
                    }
                } label: {
                    Text("compose_poll_expiration", bundle: FlareAppleUILocalization.bundle)
                    Text(viewModel.expired.titleKey, bundle: FlareAppleUILocalization.bundle)
                }
            }
        }
    }
}

private extension View {
    @ViewBuilder
    func composeTextInputConfiguration() -> some View {
        #if os(iOS)
            self
                .textInputAutocapitalization(.sentences)
                .keyboardType(.twitter)
        #else
            self
        #endif
    }
}
