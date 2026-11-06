use proc_macro::TokenStream;
use quote::{format_ident, quote};
use syn::{ItemImpl, LitStr, Type, parse_macro_input};

/// Exports a concrete Crux App under a stable native identifier.
///
/// Its event enum must have a Rust-only `BridgeFailed(String)` variant.
#[proc_macro_attribute]
pub fn view_model(args: TokenStream, input: TokenStream) -> TokenStream {
    let id = parse_macro_input!(args as LitStr);
    let item = parse_macro_input!(input as ItemImpl);
    let app = &item.self_ty;
    if item.trait_.is_none() || !item.generics.params.is_empty() || !matches!(**app, Type::Path(_))
    {
        return syn::Error::new_spanned(&item, "expected a concrete Crux App implementation")
            .into_compile_error()
            .into();
    }
    let name = match export_name(&id) {
        Ok(name) => name,
        Err(error) => return error.into_compile_error().into(),
    };
    let ffi = format_ident!("{name}ViewModelFfi");
    let swift_module = format!("Flare{name}Types");
    let kotlin_package = format!("dev.dimension.flare.{}", id.value());
    let cfgs: Vec<_> = item
        .attrs
        .iter()
        .filter(|attr| attr.path().is_ident("cfg"))
        .collect();

    quote! {
        #item

        #(#cfgs)*
        #[cfg(feature = "native-ffi")]
        crate::native_ffi::export_view_model!(#app => #ffi);

        #(#cfgs)*
        #[cfg(feature = "codegen")]
        inventory::submit! {
            crate::view_model_registry::ViewModelDescriptor::new::<#app>(
                #id, stringify!(#ffi), #swift_module, #kotlin_package
            )
        }
    }
    .into()
}

fn export_name(id: &LitStr) -> syn::Result<String> {
    let mut name = String::new();
    for part in id.value().split('_') {
        let bytes = part.as_bytes();
        if !matches!(bytes.first(), Some(b'a'..=b'z'))
            || !bytes[1..]
                .iter()
                .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit())
        {
            return Err(syn::Error::new(
                id.span(),
                "expected a lowercase snake_case ViewModel ID",
            ));
        }
        name.push(char::from(bytes[0].to_ascii_uppercase()));
        name.push_str(&part[1..]);
    }
    Ok(name)
}
