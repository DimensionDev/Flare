//! Compiler-checked contract descriptions; code generation runs only on the build host.

/// A dependency is a function so recursive DTOs do not form recursive constants.
pub type SchemaFactory = fn() -> Schema;

/// Metadata consumed by the native build, never by presenter tasks or UI code.
pub struct Schema {
    pub name: &'static str,
    pub declaration: &'static str,
    pub dependencies: &'static [SchemaFactory],
    pub native_types: &'static [&'static str],
}

#[cfg(feature = "codegen")]
pub mod codegen;
