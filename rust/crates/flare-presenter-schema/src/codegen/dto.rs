use heck::{ToSnakeCase, ToUpperCamelCase};
use proc_macro2::TokenStream;
use quote::{format_ident, quote};
use syn::{Attribute, Fields, GenericArgument, Item, PathArguments, Type, Visibility};

use super::{concrete, exported_attrs};

pub(crate) fn is_marked(attrs: &[Attribute]) -> bool {
    attrs.iter().any(|attr| {
        attr.path()
            .segments
            .last()
            .is_some_and(|part| part.ident == "presenter_dto")
    })
}

pub(crate) fn expand(item: &Item) -> syn::Result<TokenStream> {
    let mut dependencies = Vec::new();
    let (name, declaration) = match item {
        Item::Struct(item) => {
            concrete(item)?;
            if !matches!(item.fields, Fields::Named(_)) {
                return Err(syn::Error::new_spanned(
                    item,
                    "DTO records must have named public fields",
                ));
            }
            let fields = export_fields(&item.fields, true, &mut dependencies)?;
            let name = &item.ident;
            let docs = exported_attrs(&item.attrs)?;
            (
                name,
                quote! { #(#docs)* #[uniffi::remote(Record)] struct #name #fields },
            )
        }
        Item::Enum(item) => {
            if !matches!(item.vis, Visibility::Public(_))
                || !item.generics.params.is_empty()
                || item.generics.where_clause.is_some()
            {
                return Err(syn::Error::new_spanned(
                    item,
                    "DTO enums must be public and concrete",
                ));
            }
            let name = &item.ident;
            let docs = exported_attrs(&item.attrs)?;
            let variants = item
                .variants
                .iter()
                .map(|variant| {
                    if variant.discriminant.is_some() {
                        return Err(syn::Error::new_spanned(
                            variant,
                            "DTO enum discriminants are unsupported",
                        ));
                    }
                    let name = &variant.ident;
                    let docs = exported_attrs(&variant.attrs)?;
                    let fields = export_fields(&variant.fields, false, &mut dependencies)?;
                    Ok(quote! { #(#docs)* #name #fields })
                })
                .collect::<syn::Result<Vec<_>>>()?;
            (
                name,
                quote! { #(#docs)* #[uniffi::remote(Enum)] enum #name { #(#variants),* } },
            )
        }
        _ => {
            return Err(syn::Error::new_spanned(
                item,
                "#[presenter_dto] requires a public record or enum",
            ));
        }
    };
    let core_alias = format_ident!("__{}_dto_core", name.to_string().to_snake_case());
    Ok(super::exports::schema(
        name,
        &dependencies,
        std::slice::from_ref(name),
        &quote! {
            use $core as #core_alias;
            pub use #core_alias::#name;
            #declaration
        },
    ))
}

pub(crate) fn export_fields(
    fields: &Fields,
    public: bool,
    dependencies: &mut Vec<TokenStream>,
) -> syn::Result<Fields> {
    let mut fields = fields.clone();
    for field in &mut fields {
        if public && !matches!(field.vis, Visibility::Public(_)) {
            return Err(syn::Error::new_spanned(field, "DTO fields must be public"));
        }
        field.attrs = exported_attrs(&field.attrs)?.into_iter().cloned().collect();
        field.ty = export_type(&field.ty, dependencies)?;
    }
    Ok(fields)
}

/// Containers and `UiState` are specialized; DTOs resolve through the public contract module.
pub(crate) fn export_type(ty: &Type, dependencies: &mut Vec<TokenStream>) -> syn::Result<Type> {
    if matches!(ty, Type::Tuple(tuple) if tuple.elems.is_empty()) {
        return Ok(ty.clone());
    }
    let Type::Path(path) = ty else {
        return Err(syn::Error::new_spanned(
            ty,
            "export owned DTOs or UniFFI value types",
        ));
    };
    if path.qself.is_some() {
        return Err(syn::Error::new_spanned(
            ty,
            "associated DTO types are unsupported",
        ));
    }
    let name = path
        .path
        .segments
        .iter()
        .map(|part| part.ident.to_string())
        .collect::<Vec<_>>()
        .join("::");
    if name == "UiState" {
        return ui_state(ty, &path.path.segments[0].arguments, dependencies);
    }
    let mut result = if let Some((canonical, arity)) = builtin(&name) {
        let segment = path.path.segments.last().unwrap();
        let arguments: Vec<_> = match &segment.arguments {
            PathArguments::None => Vec::new(),
            PathArguments::AngleBracketed(args) => args.args.iter().collect(),
            PathArguments::Parenthesized(_) => {
                return Err(syn::Error::new_spanned(
                    ty,
                    "function types are unsupported",
                ));
            }
        };
        if arguments.len() != arity
            || path
                .path
                .segments
                .iter()
                .rev()
                .skip(1)
                .any(|part| !matches!(part.arguments, PathArguments::None))
        {
            return Err(syn::Error::new_spanned(
                ty,
                "invalid type arguments for a UniFFI value type",
            ));
        }
        let arguments = arguments
            .into_iter()
            .map(|argument| {
                let GenericArgument::Type(ty) = argument else {
                    return Err(syn::Error::new_spanned(
                        argument,
                        "DTO containers require concrete types",
                    ));
                };
                export_type(ty, dependencies)
            })
            .collect::<syn::Result<Vec<_>>>()?;
        let mut path: syn::TypePath = syn::parse_str(canonical)?;
        if !arguments.is_empty() {
            path.path.segments.last_mut().unwrap().arguments =
                PathArguments::AngleBracketed(syn::parse_quote!(<#(#arguments),*>));
        }
        path
    } else {
        let ident = path.path.get_ident().filter(|ident| !matches!(ident.to_string().as_str(), "Self" | "usize" | "isize" | "char" | "str" | "i128" | "u128")).ok_or_else(|| {
                syn::Error::new_spanned(ty, "use a concrete, unqualified DTO name, apply #[presenter_dto] and re-export it from the contract module")
            })?;
        dependencies.push(quote!(#ident::__schema));
        path.clone()
    };
    // Canonical standard-library paths do not depend on imports in the FFI crate.
    if builtin(&name).is_some() && result.path.segments.len() > 1 {
        result.path.leading_colon = Some(syn::token::PathSep::default());
    }
    Ok(Type::Path(result))
}

fn ui_state(
    ty: &Type,
    arguments: &PathArguments,
    dependencies: &mut Vec<TokenStream>,
) -> syn::Result<Type> {
    let PathArguments::AngleBracketed(arguments) = arguments else {
        return Err(syn::Error::new_spanned(
            ty,
            "UiState requires one owned payload type",
        ));
    };
    let arguments: Vec<_> = arguments.args.iter().collect();
    let [GenericArgument::Type(payload)] = arguments.as_slice() else {
        return Err(syn::Error::new_spanned(
            ty,
            "UiState requires one owned payload type",
        ));
    };
    let mut nested = Vec::new();
    let payload = export_type(payload, &mut nested)?;
    let name = format_ident!("UiStateOf{}", type_label(&payload)?);
    nested.push(quote!(::flare_core::presenter::UiError::__schema));
    let value = super::exports::schema_value(
        &name,
        &nested,
        std::slice::from_ref(&name),
        &quote! {
            pub type #name = ::flare_core::presenter::UiState<#payload>;
            #[uniffi::remote(Enum)]
            enum #name {
                Loading,
                Success { data: #payload },
                Error { error: UiError },
            }
        },
    );
    dependencies.push(quote!(|| #value));
    Ok(syn::parse_quote!(#name))
}

fn type_label(ty: &Type) -> syn::Result<String> {
    if matches!(ty, Type::Tuple(tuple) if tuple.elems.is_empty()) {
        return Err(syn::Error::new_spanned(
            ty,
            "UiState needs a payload that UniFFI can encode",
        ));
    }
    let Type::Path(path) = ty else { unreachable!() };
    let segment = path.path.segments.last().unwrap();
    let mut label = segment.ident.to_string().to_upper_camel_case();
    if let PathArguments::AngleBracketed(arguments) = &segment.arguments {
        for argument in &arguments.args {
            let GenericArgument::Type(ty) = argument else {
                unreachable!()
            };
            label.push_str("Of");
            label.push_str(&type_label(ty)?);
        }
    }
    Ok(label)
}

fn builtin(name: &str) -> Option<(&str, usize)> {
    match name {
        "bool" | "i8" | "u8" | "i16" | "u16" | "i32" | "u32" | "i64" | "u64" | "f32" | "f64" => {
            Some((name, 0))
        }
        "String" | "std::string::String" | "alloc::string::String" => {
            Some(("std::string::String", 0))
        }
        "Vec" | "std::vec::Vec" | "alloc::vec::Vec" => Some(("std::vec::Vec", 1)),
        "Option" | "std::option::Option" | "core::option::Option" => {
            Some(("std::option::Option", 1))
        }
        "HashMap" | "std::collections::HashMap" => Some(("std::collections::HashMap", 2)),
        "Duration" | "std::time::Duration" | "core::time::Duration" => {
            Some(("std::time::Duration", 0))
        }
        "SystemTime" | "std::time::SystemTime" => Some(("std::time::SystemTime", 0)),
        _ => None,
    }
}
