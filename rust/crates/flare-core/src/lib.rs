//! Flare's shared business and application state, independent of UI frameworks.

pub mod counter;
mod domain;
#[cfg(feature = "native-ffi")]
mod native_ffi;
pub mod ui;
#[cfg(feature = "codegen")]
pub mod view_model_registry;

pub(crate) use flare_view_model_macros::view_model;

#[cfg(feature = "native-ffi")]
boltffi::scaffolding!();
