//! Attributes that keep business declarations as ordinary Rust items.

use proc_macro::TokenStream;
use syn::parse_macro_input;

/// Generates the lifecycle and native contract for one presenter module.
#[proc_macro_attribute]
pub fn presenter(args: TokenStream, input: TokenStream) -> TokenStream {
    if !args.is_empty() {
        return syn::Error::new(
            proc_macro::Span::call_site().into(),
            "#[presenter] takes no arguments",
        )
        .into_compile_error()
        .into();
    }
    flare_presenter_schema::codegen::presenter(&parse_macro_input!(input as syn::ItemMod))
        .unwrap_or_else(syn::Error::into_compile_error)
        .into()
}

/// Describes a shared DTO before conditional compilation can change its shape.
#[proc_macro_attribute]
pub fn presenter_dto(args: TokenStream, input: TokenStream) -> TokenStream {
    if !args.is_empty() {
        return syn::Error::new(
            proc_macro::Span::call_site().into(),
            "#[presenter_dto] takes no arguments",
        )
        .into_compile_error()
        .into();
    }
    flare_presenter_schema::codegen::presenter_dto(&parse_macro_input!(input as syn::Item))
        .unwrap_or_else(syn::Error::into_compile_error)
        .into()
}

/// Describes a shared presenter error and supplies the session-closed conversion.
#[proc_macro_attribute]
pub fn presenter_error(args: TokenStream, input: TokenStream) -> TokenStream {
    if !args.is_empty() {
        return syn::Error::new(
            proc_macro::Span::call_site().into(),
            "#[presenter_error] takes no arguments",
        )
        .into_compile_error()
        .into();
    }
    flare_presenter_schema::codegen::presenter_error(&parse_macro_input!(input as syn::ItemEnum))
        .unwrap_or_else(syn::Error::into_compile_error)
        .into()
}
