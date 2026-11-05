import Serde

func serializeArray<T, S: Serializer>(
    value: [T],
    serializer: S,
    serializeElement: (T, S) throws -> Void
) throws {
    try serializer.serialize_len(value: value.count)
    for item in value {
        try serializeElement(item, serializer)
    }
}

func deserializeArray<T, D: Deserializer>(
    deserializer: D,
    deserializeElement: (D) throws -> T
) throws -> [T] {
    let length = try deserializer.deserialize_len()
    var obj: [T] = []
    for _ in 0..<length {
        obj.append(try deserializeElement(deserializer))
    }
    return obj
}

func serializeOption<T, S: Serializer>(
    value: T?,
    serializer: S,
    serializeElement: (T, S) throws -> Void
) throws {
    if let value = value {
        try serializer.serialize_option_tag(value: true)
        try serializeElement(value, serializer)
    } else {
        try serializer.serialize_option_tag(value: false)
    }
}

func deserializeOption<T, D: Deserializer>(
    deserializer: D,
    deserializeElement: (D) throws -> T
) throws -> T? {
    let tag = try deserializer.deserialize_option_tag()
    if tag {
        return try deserializeElement(deserializer)
    } else {
        return nil
    }
}

indirect public enum CounterEffect: Hashable, Equatable {
    case render(RenderOperation)

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        switch self {
        case .render(let x):
            try serializer.serialize_variant_index(value: 0)
            try x.serialize(serializer: serializer)
        }
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> CounterEffect {
        let index = try deserializer.deserialize_variant_index()
        try deserializer.increase_container_depth()
        switch index {
        case 0:
            let x = try RenderOperation.deserialize(deserializer: deserializer)
            try deserializer.decrease_container_depth()
            return .render(x)
        default: throw DeserializationError.invalidInput(issue: "Unknown variant index for CounterEffect: \(index)")
        }
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> CounterEffect {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

indirect public enum CounterEvent: Hashable, Equatable {
    case increment
    case decrement
    case reset

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        switch self {
        case .increment:
            try serializer.serialize_variant_index(value: 0)
        case .decrement:
            try serializer.serialize_variant_index(value: 1)
        case .reset:
            try serializer.serialize_variant_index(value: 2)
        }
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> CounterEvent {
        let index = try deserializer.deserialize_variant_index()
        try deserializer.increase_container_depth()
        switch index {
        case 0:
            try deserializer.decrease_container_depth()
            return .increment
        case 1:
            try deserializer.decrease_container_depth()
            return .decrement
        case 2:
            try deserializer.decrease_container_depth()
            return .reset
        default: throw DeserializationError.invalidInput(issue: "Unknown variant index for CounterEvent: \(index)")
        }
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> CounterEvent {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

public struct CounterState: Hashable, Equatable {
    public var value: Int64
    public var revision: UInt64
    public var error: UiError?

    public init(value: Int64, revision: UInt64, error: UiError?) {
        self.value = value
        self.revision = revision
        self.error = error
    }

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        try serializer.serialize_i64(value: self.value)
        try serializer.serialize_u64(value: self.revision)
        try serializeOption(value: self.error, serializer: serializer) { value, serializer in
            try value.serialize(serializer: serializer)
        }
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> CounterState {
        try deserializer.increase_container_depth()
        let value = try deserializer.deserialize_i64()
        let revision = try deserializer.deserialize_u64()
        let error = try deserializeOption(deserializer: deserializer) { deserializer in
            try UiError.deserialize(deserializer: deserializer)
        }
        try deserializer.decrease_container_depth()
        return CounterState(value: value, revision: revision, error: error)
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> CounterState {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

/// The single operation `Render` implements.
public struct RenderOperation: Hashable, Equatable {
    public init() {
    }

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> RenderOperation {
        try deserializer.increase_container_depth()
        try deserializer.decrease_container_depth()
        return RenderOperation()
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> RenderOperation {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

/// Request for a side-effect passed from the Core to the Shell.
/// 
/// The `EffectId` links the `Request` with the corresponding call to [`Core::resolve`] to pass the data back
/// to the [`App::update`] function (wrapped in the event provided to the capability originating the effect).
public struct Request: Hashable, Equatable {
    public var id: UInt32
    public var effect: CounterEffect

    public init(id: UInt32, effect: CounterEffect) {
        self.id = id
        self.effect = effect
    }

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        try serializer.serialize_u32(value: self.id)
        try self.effect.serialize(serializer: serializer)
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> Request {
        try deserializer.increase_container_depth()
        let id = try deserializer.deserialize_u32()
        let effect = try CounterEffect.deserialize(deserializer: deserializer)
        try deserializer.decrease_container_depth()
        return Request(id: id, effect: effect)
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> Request {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

/// A batch of effect requests from the Core to the Shell, as serialised by
/// [`Bridge::update`] and [`Bridge::resolve`].
/// 
/// The wire format is identical to `Vec<Request<Eff>>` (the newtype is
/// `serde(transparent)`), so existing shell code that already deserialises
/// a `Vec<Request>` remains binary-compatible.
/// 
/// Registering this type with the type-generation system causes the code
/// generators to emit a `Requests` type (with a `value` field containing the
/// list) together with a top-level `bincodeDeserialize` / `BincodeDeserialize`
/// helper, replacing the hand-written extension files that were previously
/// appended by `add_extensions()`.
public struct Requests: Hashable, Equatable {
    public var value: [Request]

    public init(value: [Request]) {
        self.value = value
    }

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        try serializeArray(value: self.value, serializer: serializer) { item, serializer in
            try item.serialize(serializer: serializer)
        }
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> Requests {
        try deserializer.increase_container_depth()
        let value = try deserializeArray(deserializer: deserializer) { deserializer in
            try Request.deserialize(deserializer: deserializer)
        }
        try deserializer.decrease_container_depth()
        return Requests(value: value)
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> Requests {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}

/// An owned presentation error with a stable code and safe fallback text.
public struct UiError: Hashable, Equatable {
    public var code: String
    public var message: String

    public init(code: String, message: String) {
        self.code = code
        self.message = message
    }

    public func serialize<S: Serializer>(serializer: S) throws {
        try serializer.increase_container_depth()
        try serializer.serialize_str(value: self.code)
        try serializer.serialize_str(value: self.message)
        try serializer.decrease_container_depth()
    }

    public func bincodeSerialize() throws -> [UInt8] {
        let serializer = BincodeSerializer.init();
        try self.serialize(serializer: serializer)
        return serializer.get_bytes()
    }

    public static func deserialize<D: Deserializer>(deserializer: D) throws -> UiError {
        try deserializer.increase_container_depth()
        let code = try deserializer.deserialize_str()
        let message = try deserializer.deserialize_str()
        try deserializer.decrease_container_depth()
        return UiError(code: code, message: message)
    }

    public static func bincodeDeserialize(input: [UInt8]) throws -> UiError {
        let deserializer = BincodeDeserializer.init(input: input);
        let obj = try deserialize(deserializer: deserializer)
        if deserializer.get_buffer_offset() < input.count {
            throw DeserializationError.invalidInput(issue: "Some input bytes were not read")
        }
        return obj
    }
}
