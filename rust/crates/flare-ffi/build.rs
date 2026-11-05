use std::{env, fs, path::PathBuf};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    println!("cargo::rerun-if-changed=build.rs");
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    for (catalog, core, name) in [
        (
            flare_core::presenter::__PRESENTER_CATALOG,
            "::flare_core::presenter",
            "exports.rs",
        ),
        (
            flare_core::test_contracts::__PRESENTER_CATALOG,
            "::flare_core::test_contracts",
            "test-exports.rs",
        ),
    ] {
        let declarations = flare_presenter_schema::codegen::export(
            catalog,
            core,
            "crate::presenters::export_presenter",
            if name == "test-exports.rs" {
                flare_core::presenter::__PRESENTER_CATALOG
            } else {
                &[]
            },
        )?;
        let path = output.join(name);
        if fs::read_to_string(&path).ok().as_ref() != Some(&declarations) {
            fs::write(path, declarations)?;
        }
    }
    Ok(())
}
