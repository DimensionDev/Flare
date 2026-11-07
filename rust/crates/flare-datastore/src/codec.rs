use buffa::Message;

/// A malformed or unencodable `DataStore` Protobuf record.
#[derive(Debug, thiserror::Error)]
pub enum ProtoError {
    #[error(transparent)]
    Decode(#[from] buffa::DecodeError),
    #[error(transparent)]
    Encode(#[from] buffa::EncodeError),
    #[error("missing required field {0}")]
    MissingRequired(&'static str),
}

/// A `DataStore` message with the required-field checks from its wire schema.
pub trait WireMessage: Message + 'static {
    #[doc(hidden)]
    fn check_required(&self) -> Result<(), ProtoError>;

    /// Decode and validate a persisted message.
    ///
    /// # Errors
    ///
    /// Returns an error for malformed input or missing required fields.
    fn parse_from_bytes(bytes: &[u8]) -> Result<Self, ProtoError> {
        let message = Self::decode_from_slice(bytes)?;
        message.check_required()?;
        Ok(message)
    }

    /// Validate and encode a message for storage.
    ///
    /// # Errors
    ///
    /// Returns an error for missing required fields or encoding failures.
    fn write_to_bytes(&self) -> Result<Vec<u8>, ProtoError> {
        self.check_required()?;
        Ok(self.try_encode_to_vec()?)
    }
}
