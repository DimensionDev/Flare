use std::collections::{BTreeMap, BTreeSet};

use heck::ToUpperCamelCase;
use proc_macro2::TokenStream;
use quote::quote;
use syn::{Ident, Path};

use crate::SchemaFactory;

/// Collect the complete type graph iteratively, then emit sibling declarations.
///
/// # Errors
/// Rejects collisions in the native type namespace or malformed schema tokens.
fn collect(presenters: &[SchemaFactory]) -> syn::Result<BTreeMap<&'static str, crate::Schema>> {
    let mut pending = presenters.to_vec();
    let mut schemas = BTreeMap::new();
    let mut types = BTreeMap::new();
    while let Some(describe) = pending.pop() {
        let schema = describe();
        if let Some(previous) = schemas.get(schema.name) {
            let previous: &crate::Schema = previous;
            if previous.declaration != schema.declaration {
                return Err(error(format!("duplicate contract type: {}", schema.name)));
            }
            continue;
        }
        for name in schema.native_types {
            let native = name.to_upper_camel_case();
            if let Some(previous) = types.insert(native.clone(), name) {
                return Err(error(format!(
                    "native type name collision: {previous} and {name} both export as {native}"
                )));
            }
        }
        pending.extend_from_slice(schema.dependencies);
        schemas.insert(schema.name, schema);
    }
    Ok(schemas)
}

/// Generate native declarations, reusing contracts already exported at the crate root.
///
/// # Errors
/// Rejects duplicate contracts, normalized name collisions, or malformed schema tokens.
pub fn export(
    presenters: &[SchemaFactory],
    core: &str,
    exporter: &str,
    shared: &[SchemaFactory],
) -> syn::Result<String> {
    let core: Path = syn::parse_str(core)?;
    let exporter: Path = syn::parse_str(exporter)?;
    let mut all = presenters.to_vec();
    all.extend_from_slice(shared);
    let mut schemas = collect(&all)?;
    let mut imports = Vec::new();
    for (name, schema) in collect(shared)? {
        schemas.remove(name);
        imports.extend(
            schema
                .native_types
                .iter()
                .map(|name| syn::parse_str::<Ident>(name)),
        );
    }
    let imports = imports.into_iter().collect::<syn::Result<Vec<_>>>()?;
    let imports = (!imports.is_empty()).then(|| {
        quote! {
            #[allow(unused_imports)]
            use crate::{#(#imports),*};
        }
    });
    let declarations = schemas
        .values()
        .map(|schema| syn::parse_str::<TokenStream>(schema.declaration))
        .collect::<syn::Result<Vec<_>>>()?;
    let mut names = BTreeSet::new();
    for describe in presenters {
        names.insert(describe().name);
    }
    let names = names
        .into_iter()
        .map(syn::parse_str::<Ident>)
        .collect::<syn::Result<Vec<_>>>()?;
    Ok(quote! {
        #imports
        macro_rules! __export_contracts {
            ($core:path; $export:path) => { #(#declarations)* };
        }
        __export_contracts!(#core; #exporter);
        #[cfg(feature = "bindgen")]
        pub const PRESENTER_BINDINGS: &[crate::presenters::PresenterBinding] = &[
            #(#names::BINDING),*
        ];
    }
    .to_string())
}

fn error(message: String) -> syn::Error {
    syn::Error::new(proc_macro2::Span::call_site(), message)
}

pub(crate) fn schema(
    name: &Ident,
    dependencies: &[TokenStream],
    native_types: &[Ident],
    declaration: &TokenStream,
) -> TokenStream {
    let value = schema_value(name, dependencies, native_types, declaration);
    quote! {
        impl #name {
            #[doc(hidden)]
            pub fn __schema() -> ::flare_core::presenter::ContractSchema { #value }
        }
    }
}

pub(crate) fn schema_value(
    name: &Ident,
    dependencies: &[TokenStream],
    native_types: &[Ident],
    declaration: &TokenStream,
) -> TokenStream {
    let label = name.to_string();
    let declaration = declaration.to_string();
    let native_types = native_types.iter().map(ToString::to_string);
    quote! {
        ::flare_core::presenter::ContractSchema {
            name: #label,
            declaration: #declaration,
            dependencies: &[#(#dependencies),*],
            native_types: &[#(#native_types),*],
        }
    }
}
