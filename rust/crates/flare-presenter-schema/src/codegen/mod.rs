//! Compile one presenter contract into core methods and a native export declaration.

use heck::{ToLowerCamelCase, ToShoutySnakeCase, ToSnakeCase, ToUpperCamelCase};
use proc_macro2::TokenStream as Tokens;
use quote::{format_ident, quote};
use syn::{
    Attribute, Fields, FnArg, GenericArgument, Ident, ImplItem, Item, ItemStruct, Pat,
    PathArguments, ReturnType, Signature, Type, Visibility, spanned::Spanned,
};

mod dto;
mod exports;
mod index;

pub use exports::export;
pub use index::generate_index;

/// Expand a module while keeping ordinary Rust syntax visible to rustfmt.
///
/// # Errors
/// Rejects conditional, generic, or otherwise unsupported presenter contracts.
pub fn presenter(module: &syn::ItemMod) -> syn::Result<Tokens> {
    let input = module_contract(module)?;
    let expanded = expand_presenter(&input)?;
    let name = &module.ident;
    let attrs = &module.attrs;
    let visibility = &module.vis;
    // Keep helper visibility inside the module and publish its contract types once.
    Ok(quote! {
        #(#attrs)* #visibility mod #name { #expanded }
        pub use #name::*;
    })
}

/// Validate a DTO before rustc evaluates `cfg`/`cfg_attr` on its fields.
///
/// # Errors
/// Rejects conditional fields and types that cannot cross the native boundary.
pub fn presenter_dto(input: &Item) -> syn::Result<Tokens> {
    let schema = dto::expand(input)?;
    Ok(quote! { #input #schema })
}

/// Describe a shared action error, including its conversion from a closed session.
///
/// # Errors
/// Rejects conditional or generic errors and reserved native-runtime variants.
pub fn presenter_error(input: &syn::ItemEnum) -> syn::Result<Tokens> {
    let generated = error_schema(input)?;
    Ok(quote! { #input #generated })
}

fn module_contract(module: &syn::ItemMod) -> syn::Result<syn::File> {
    exported_attrs(&module.attrs)?;
    let (_, items) = module
        .content
        .as_ref()
        .ok_or_else(|| syn::Error::new_spanned(module, "#[presenter] requires an inline module"))?;
    Ok(syn::File {
        shebang: None,
        attrs: Vec::new(),
        items: items.clone(),
    })
}

fn type_ident(ty: &Type) -> syn::Result<&Ident> {
    if let Type::Path(path) = ty {
        if path.qself.is_none() {
            if let Some(ident) = path.path.get_ident() {
                return Ok(ident);
            }
        }
    }
    Err(syn::Error::new_spanned(
        ty,
        "expected an unqualified concrete type name",
    ))
}

fn exported_attrs(attrs: &[Attribute]) -> syn::Result<Vec<&Attribute>> {
    for attr in attrs {
        if attr.path().is_ident("cfg") || attr.path().is_ident("cfg_attr") {
            return Err(syn::Error::new_spanned(
                attr,
                "conditional presenter contracts are not supported",
            ));
        }
    }
    Ok(attrs
        .iter()
        .filter(|attr| attr.path().is_ident("doc"))
        .collect())
}

fn concrete(item: &ItemStruct) -> syn::Result<()> {
    exported_attrs(&item.attrs)?;
    if !item.generics.params.is_empty() || item.generics.where_clause.is_some() {
        return Err(syn::Error::new_spanned(
            &item.generics,
            "exported presenter types must be concrete",
        ));
    }
    if !matches!(item.vis, Visibility::Public(_)) {
        return Err(syn::Error::new_spanned(
            &item.ident,
            "exported presenter types must be public",
        ));
    }
    Ok(())
}

fn parameters(signature: &Signature, has_receiver: bool) -> syn::Result<Vec<(&Ident, &Type)>> {
    if !signature.generics.params.is_empty()
        || signature.generics.where_clause.is_some()
        || signature.unsafety.is_some()
        || signature.abi.is_some()
        || signature.variadic.is_some()
    {
        return Err(syn::Error::new_spanned(
            signature,
            "exported methods must be safe, concrete Rust methods",
        ));
    }
    let mut inputs = signature.inputs.iter();
    if has_receiver {
        let Some(FnArg::Receiver(receiver)) = inputs.next() else {
            return Err(syn::Error::new_spanned(
                signature,
                "presenter actions require &self",
            ));
        };
        if receiver.reference.is_none()
            || receiver.mutability.is_some()
            || receiver.colon_token.is_some()
        {
            return Err(syn::Error::new_spanned(
                receiver,
                "presenter actions require &self",
            ));
        }
    }
    inputs
        .map(|arg| {
            if let FnArg::Typed(arg) = arg {
                exported_attrs(&arg.attrs)?;
                if let Pat::Ident(pattern) = &*arg.pat {
                    if pattern.by_ref.is_none() && pattern.subpat.is_none() {
                        return Ok((&pattern.ident, &*arg.ty));
                    }
                }
            }
            Err(syn::Error::new_spanned(
                arg,
                "use named, owned parameters in exported methods",
            ))
        })
        .collect()
}

fn action_result<'a>(signature: &'a Signature, error: &Ident) -> syn::Result<&'a Type> {
    if signature.asyncness.is_some() {
        if let ReturnType::Type(_, ty) = &signature.output {
            if let Type::Path(path) = &**ty {
                if let Some(segment) = path.path.segments.last() {
                    if segment.ident == "Result" {
                        if let PathArguments::AngleBracketed(arguments) = &segment.arguments {
                            let args: Vec<_> = arguments.args.iter().collect();
                            if let [GenericArgument::Type(value), GenericArgument::Type(failure)] =
                                args.as_slice()
                            {
                                if type_ident(failure)? == error {
                                    return Ok(value);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Err(syn::Error::new_spanned(
        signature,
        "public presenter actions must be async and return Result<T, the presenter error>",
    ))
}

fn presenter_types(input: &syn::File) -> syn::Result<(&Ident, &Type, Ident)> {
    let presenters: Vec<_> = input
        .items
        .iter()
        .filter_map(|item| match item {
            Item::Struct(item)
                if item.ident.to_string().ends_with("Presenter")
                    && item.fields.iter().any(|field| {
                        field.ident.as_ref().is_some_and(|name| name == "session")
                    }) =>
            {
                Some(item)
            }
            _ => None,
        })
        .collect();
    let [presenter] = presenters.as_slice() else {
        return Err(syn::Error::new(
            input.span(),
            "declare exactly one public presenter struct with a session field",
        ));
    };
    concrete(presenter)?;
    let name = &presenter.ident;
    let session = presenter
        .fields
        .iter()
        .find(|field| field.ident.as_ref().is_some_and(|name| name == "session"))
        .unwrap();
    let Type::Path(session_type) = &session.ty else {
        return Err(syn::Error::new_spanned(
            session,
            "session must be PresenterSession<State, Action, Error>",
        ));
    };
    let segment = session_type.path.segments.last().unwrap();
    let PathArguments::AngleBracketed(arguments) = &segment.arguments else {
        return Err(syn::Error::new_spanned(
            session,
            "session must be PresenterSession<State, Action, Error>",
        ));
    };
    let args: Vec<_> = arguments.args.iter().collect();
    let (state, error) = match args.as_slice() {
        [GenericArgument::Type(state), GenericArgument::Type(_)] => {
            (state, format_ident!("PresenterError"))
        }
        [
            GenericArgument::Type(state),
            GenericArgument::Type(_),
            GenericArgument::Type(error),
        ] => (state, type_ident(error)?.clone()),
        _ => {
            return Err(syn::Error::new_spanned(
                session,
                "session must be PresenterSession<State, Action> or PresenterSession<State, Action, Error>",
            ));
        }
    };
    if segment.ident != "PresenterSession" {
        return Err(syn::Error::new_spanned(
            session,
            "session must be PresenterSession<State, Action, Error>",
        ));
    }
    Ok((name, state, error))
}

fn dto_declarations(
    input: &syn::File,
    presenter: &Ident,
    error: &Ident,
) -> syn::Result<Vec<Tokens>> {
    input
        .items
        .iter()
        .filter(|item| match item {
            Item::Struct(record)
                if &record.ident != presenter && matches!(record.vis, Visibility::Public(_)) =>
            {
                true
            }
            Item::Enum(enum_)
                if &enum_.ident != error && matches!(enum_.vis, Visibility::Public(_)) =>
            {
                true
            }
            _ => false,
        })
        .map(|item| {
            let schema = dto::expand(item)?;
            let attrs = match item {
                Item::Struct(item) => &item.attrs,
                Item::Enum(item) => &item.attrs,
                _ => unreachable!(),
            };
            Ok(if dto::is_marked(attrs) {
                Tokens::new()
            } else {
                schema
            })
        })
        .collect()
}

fn error_declaration(
    error_decl: &syn::ItemEnum,
    core_alias: &Ident,
    dependencies: &mut Vec<Tokens>,
) -> syn::Result<Tokens> {
    let error = &error_decl.ident;
    if !error_decl.generics.params.is_empty()
        || error_decl.generics.where_clause.is_some()
        || !matches!(error_decl.vis, Visibility::Public(_))
    {
        return Err(syn::Error::new_spanned(
            error_decl,
            "the presenter error must be a public concrete enum",
        ));
    }
    let error_docs = exported_attrs(&error_decl.attrs)?;
    if !error_decl
        .variants
        .iter()
        .any(|variant| variant.ident == "Closed" && matches!(variant.fields, Fields::Unit))
    {
        return Err(syn::Error::new_spanned(
            error_decl,
            "the presenter error requires a unit Closed variant",
        ));
    }
    let mut variants = Vec::new();
    let mut conversions = Vec::new();
    let mut displays = Vec::new();
    for variant in &error_decl.variants {
        if variant.ident == "RuntimeUnavailable" || variant.discriminant.is_some() {
            return Err(syn::Error::new_spanned(
                variant,
                "RuntimeUnavailable is reserved for FFI; explicit discriminants are unsupported",
            ));
        }
        let docs = exported_attrs(&variant.attrs)?;
        let variant_name = &variant.ident;
        let fields = dto::export_fields(&variant.fields, false, dependencies)?;
        let bindings: Vec<_> = fields
            .iter()
            .enumerate()
            .map(|(index, field)| {
                field
                    .ident
                    .clone()
                    .unwrap_or_else(|| format_ident!("field_{index}"))
            })
            .collect();
        let pattern = match &fields {
            Fields::Unit => quote! {},
            Fields::Named(_) => quote! { { #(#bindings),* } },
            Fields::Unnamed(_) => quote! { ( #(#bindings),* ) },
        };
        let cloned = match &fields {
            Fields::Unit => quote! {},
            Fields::Named(_) => quote! { { #(#bindings: #bindings.clone()),* } },
            Fields::Unnamed(_) => quote! { ( #(#bindings.clone()),* ) },
        };
        variants.push(quote! { #(#docs)* #variant_name #fields, });
        conversions.push(
            quote! { #core_alias::#error::#variant_name #pattern => Self::#variant_name #pattern, },
        );
        displays.push(quote! { Self::#variant_name #pattern => std::fmt::Display::fmt(&#core_alias::#error::#variant_name #cloned, formatter), });
    }
    Ok(quote! {
        #(#error_docs)*
        #[derive(Debug, uniffi::Error)]
        pub enum #error { #(#variants)* RuntimeUnavailable }
        impl From<#core_alias::#error> for #error {
            fn from(error: #core_alias::#error) -> Self { match error { #(#conversions)* } }
        }
        impl std::fmt::Display for #error {
            fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                match self {
                    #(#displays)*
                    Self::RuntimeUnavailable => formatter.write_str("Rust runtime could not start"),
                }
            }
        }
        impl std::error::Error for #error {}
    })
}

fn error_schema(error_decl: &syn::ItemEnum) -> syn::Result<Tokens> {
    let error = &error_decl.ident;
    let core_alias = format_ident!("__{}_error_core", error.to_string().to_snake_case());
    let mut dependencies = Vec::new();
    let declaration = error_declaration(error_decl, &core_alias, &mut dependencies)?;
    let schema = exports::schema(
        error,
        &dependencies,
        std::slice::from_ref(error),
        &quote! {
            use $core as #core_alias;
            #declaration
        },
    );
    Ok(quote! {
        #schema
        impl From<::flare_core::presenter::PresenterClosed> for #error {
            fn from(_: ::flare_core::presenter::PresenterClosed) -> Self { Self::Closed }
        }
    })
}

fn action_declarations(
    input: &syn::File,
    name: &Ident,
    error: &Ident,
    core_alias: &Ident,
    dependencies: &mut Vec<Tokens>,
) -> syn::Result<Tokens> {
    let mut constructor = None;
    let mut actions = Vec::new();
    for item in &input.items {
        let Item::Impl(item) = item else { continue };
        if item.trait_.is_some() || type_ident(&item.self_ty).ok() != Some(name) {
            continue;
        }
        exported_attrs(&item.attrs)?;
        if !item.generics.params.is_empty() || item.generics.where_clause.is_some() {
            return Err(syn::Error::new_spanned(
                &item.generics,
                "presenter impls must be concrete",
            ));
        }
        for method in &item.items {
            let ImplItem::Fn(method) = method else {
                continue;
            };
            let method_name = &method.sig.ident;
            if [
                "snapshot",
                "subscribe",
                "shutdown",
                "request_shutdown",
                "__schema",
            ]
            .iter()
            .any(|reserved| method_name == reserved)
            {
                return Err(syn::Error::new_spanned(
                    method_name,
                    "this method is generated by #[presenter]",
                ));
            }
            if !matches!(method.vis, Visibility::Public(_)) {
                continue;
            }
            let docs = exported_attrs(&method.attrs)?;
            let params = parameters(&method.sig, method_name != "new")?;
            let declarations = params
                .iter()
                .map(|(name, ty)| {
                    let ty = dto::export_type(ty, dependencies)?;
                    Ok(quote! { #name: #ty })
                })
                .collect::<syn::Result<Vec<_>>>()?;
            if method_name == "new" {
                if constructor.is_some() || method.sig.asyncness.is_some() {
                    return Err(syn::Error::new_spanned(
                        method,
                        "provide one synchronous new returning (Self, task)",
                    ));
                }
                let arguments = params.iter().map(|(name, _)| name);
                constructor = Some(
                    quote! { constructor(#(#declarations),*) => #core_alias::#name::new(#(#arguments),*); },
                );
            } else {
                let result = dto::export_type(action_result(&method.sig, error)?, dependencies)?;
                actions.push(quote! { #(#docs)* fn #method_name(#(#declarations),*) -> #result; });
            }
        }
    }
    let constructor = constructor.ok_or_else(|| {
        syn::Error::new_spanned(name, "provide a public new returning (Self, task)")
    })?;
    Ok(quote! { #constructor #(#actions)* })
}

fn expand_presenter(input: &syn::File) -> syn::Result<Tokens> {
    let (name, state, error) = presenter_types(input)?;
    let name_string = name.to_string();
    let base = name_string
        .strip_suffix("Presenter")
        .filter(|base| !base.is_empty())
        .ok_or_else(|| syn::Error::new_spanned(name, "presenter names must end with Presenter"))?;
    let core_alias = format_ident!("__{}_core", base.to_snake_case());
    let dtos = dto_declarations(input, name, &error)?;
    let error_schema = input
        .items
        .iter()
        .find_map(|item| match item {
            Item::Enum(item) if item.ident == error => Some(item),
            _ => None,
        })
        .filter(|item| {
            !item.attrs.iter().any(|attr| {
                attr.path()
                    .segments
                    .last()
                    .is_some_and(|part| part.ident == "presenter_error")
            })
        })
        .map(error_schema)
        .transpose()?;
    let mut dependencies = vec![quote!(#error::__schema)];
    let ffi_state = dto::export_type(state, &mut dependencies)?;
    let actions = action_declarations(input, name, &error, &core_alias, &mut dependencies)?;
    let subscription = format_ident!("{base}Subscription");
    let resources = format_ident!("{}_RESOURCES", base.to_shouty_snake_case());
    let resource_type = format_ident!("{base}Resources");
    let resource_fn = format_ident!("{}_resources", base.to_snake_case());
    let binding = format!("{}Binding", name_string.to_lower_camel_case());
    let native_presenter = name_string.to_upper_camel_case();
    let native_subscription = subscription.to_string().to_upper_camel_case();
    let schema = exports::schema(
        name,
        &dependencies,
        &[name.clone(), subscription.clone(), resource_type.clone()],
        &quote! {
            $export! {
                declarations {
                    use $core as #core_alias;
                }
                #name {
                    core: #core_alias::#name,
                    state: #ffi_state,
                    subscription: #subscription,
                    error: #error,
                    resources: #resources,
                    resource_type: #resource_type,
                    resource_fn: #resource_fn,
                    binding: #binding,
                    native_presenter: #native_presenter,
                    native_subscription: #native_subscription,
                    #actions
                }
            }
        },
    );
    Ok(quote! {
        #input
        #(#dtos)*
        #error_schema
        pub type #subscription = ::flare_core::presenter::StateSubscription<#state>;
        impl #name {
            #[must_use]
            pub fn snapshot(&self) -> #state { self.session.snapshot() }
            #[must_use]
            pub fn subscribe(&self) -> #subscription { self.session.subscribe() }
            pub fn request_shutdown(&self) { self.session.request_shutdown(); }
            pub async fn shutdown(&self) { self.session.shutdown().await; }
        }
        #schema
    })
}

#[cfg(test)]
mod tests;
