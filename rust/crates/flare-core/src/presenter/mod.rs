//! Immutable render state, typed actions, subscriptions, and session lifetimes.

#![deny(ambiguous_glob_reexports)]

mod session;
mod state;
mod ui_state;

include!(concat!(env!("OUT_DIR"), "/presenters.rs"));

pub use session::{PresenterClosed, PresenterSession};
pub use state::{StatePublisher, StateStore, StateSubscription};
pub use ui_state::{PresenterError, UiError, UiState};

#[doc(hidden)]
pub use flare_presenter_schema::Schema as ContractSchema;
