use std::{error::Error, fmt};

/// A content load result. Action failures must not discard successful content.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UiState<T> {
    Loading,
    Success { data: T },
    Error { error: UiError },
}

/// An owned, cross-platform presentation error; never an exception or error chain.
#[flare_presenter_macros::presenter_dto]
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UiError {
    /// Stable identifier for UI translation or recovery decisions.
    pub code: String,
    /// Safe fallback text, supplied explicitly by the application.
    pub message: String,
}

impl UiError {
    #[must_use]
    pub fn new(code: impl Into<String>, message: impl Into<String>) -> Self {
        Self {
            code: code.into(),
            message: message.into(),
        }
    }
}

impl fmt::Display for UiError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.message)
    }
}

impl Error for UiError {}

/// The default action error. Features may still declare a typed custom error.
#[flare_presenter_macros::presenter_error]
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum PresenterError {
    Closed,
    Failed { error: UiError },
}

impl From<UiError> for PresenterError {
    fn from(error: UiError) -> Self {
        Self::Failed { error }
    }
}

impl fmt::Display for PresenterError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Closed => formatter.write_str("presenter is closed"),
            Self::Failed { error } => error.fmt(formatter),
        }
    }
}

impl Error for PresenterError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        match self {
            Self::Closed => None,
            Self::Failed { error } => Some(error),
        }
    }
}
