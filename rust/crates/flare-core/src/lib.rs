//! Flare's shared business and application state, independent of UI frameworks.

extern crate self as flare_core;

mod domain;
pub mod presenter;

#[cfg(feature = "test-fixtures")]
#[doc(hidden)]
pub mod test_contracts {
    include!(concat!(env!("OUT_DIR"), "/test-presenters.rs"));
}
