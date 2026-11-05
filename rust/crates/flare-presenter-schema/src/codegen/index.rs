use std::{collections::BTreeMap, error::Error, fs, path::Path};

use quote::quote;
use syn::{Ident, Item};

use super::{module_contract, presenter_types};

/// Index modules using the same presenter parser as the attribute macro.
///
/// # Errors
/// Rejects unreadable sources and duplicate, conditional, or malformed contracts.
pub fn generate_index(directory: &Path) -> Result<String, Box<dyn Error>> {
    let mut files = Vec::new();
    for entry in fs::read_dir(directory)? {
        let entry = entry?;
        if entry.file_type()?.is_file() && entry.path().extension().is_some_and(|ext| ext == "rs") {
            files.push(entry.path());
        }
    }
    files.sort();
    let mut modules = Vec::new();
    let mut presenters = BTreeMap::new();
    for file in files {
        let stem = file
            .file_stem()
            .ok_or("source file must have a module name")?
            .to_str()
            .ok_or("module names must be UTF-8")?;
        if stem == "mod" {
            return Err("features contains business files, not a handwritten mod.rs".into());
        }
        let module: Ident = syn::parse_str(stem)
            .map_err(|error| format!("{}: invalid Rust module name: {error}", file.display()))?;
        let source = syn::parse_file(&fs::read_to_string(&file)?)
            .map_err(|error| format!("{}: {error}", file.display()))?;
        for name in
            presenter_names(&source).map_err(|error| format!("{}: {error}", file.display()))?
        {
            if let Some(previous) =
                presenters.insert(name.to_string(), (name.clone(), file.clone()))
            {
                return Err(format!(
                    "duplicate presenter {name} in {} and {}",
                    previous.1.display(),
                    file.display()
                )
                .into());
            }
        }
        let path = file.to_str().ok_or("source paths must be UTF-8")?;
        modules.push(quote! {
            #[path = #path]
            mod #module;
            pub use #module::*;
        });
    }
    let names = presenters.values().map(|(name, _)| name);
    Ok(quote! {
        #(#modules)*
        #[doc(hidden)]
        pub const __PRESENTER_CATALOG: &[fn() -> ::flare_core::presenter::ContractSchema] = &[
            #(#names::__schema),*
        ];
    }
    .to_string())
}

fn presenter_names(source: &syn::File) -> syn::Result<Vec<Ident>> {
    let mut names = Vec::new();
    for item in &source.items {
        let Item::Mod(module) = item else { continue };
        if !module.attrs.iter().any(|attr| {
            attr.path()
                .segments
                .last()
                .is_some_and(|part| part.ident == "presenter")
        }) {
            continue;
        }
        let contract = module_contract(module)?;
        let (name, _, _) = presenter_types(&contract)?;
        names.push(name.clone());
    }
    Ok(names)
}
