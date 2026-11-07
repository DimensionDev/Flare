use facet::Facet;
use serde::Serialize;

/// Internal Rust load state. Export concrete view models through Crux typegen.
/// Action errors belong beside successful content rather than replacing it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum UiState<T> {
    Loading,
    Success { data: T },
    Error { error: UiError },
}

/// An owned presentation error with a stable code and safe fallback text.
#[derive(Clone, Debug, PartialEq, Eq, Facet, Serialize)]
pub struct UiError {
    pub code: String,
    pub message: String,
}
