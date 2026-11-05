use std::{env, fs, path::PathBuf};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let root = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap());
    for (source, name) in [
        ("src/presenter/features", "presenters.rs"),
        ("tests/fixtures/native", "test-presenters.rs"),
    ] {
        let source = root.join(source);
        // Directory tracking includes additions, removals and renames.
        println!("cargo::rerun-if-changed={}", source.display());
        let index = flare_presenter_schema::codegen::generate_index(&source)?;
        let path = output.join(name);
        if fs::read_to_string(&path).ok().as_ref() != Some(&index) {
            fs::write(path, index)?;
        }
    }
    Ok(())
}
