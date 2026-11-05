use std::{error::Error, path::Path};

use crux_core::type_generation::facet::{Config, TypeRegistry};
use flare_core::counter::CounterApp;

fn main() -> Result<(), Box<dyn Error>> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    let [language, output] = args.as_slice() else {
        return Err("usage: codegen <swift|kotlin> <output directory>".into());
    };
    let types = TypeRegistry::new().register_app::<CounterApp>()?.build()?;
    match language.to_str() {
        Some("kotlin") => {
            types.kotlin(
                &Config::builder("dev.dimension.flare.counter", Path::new(output)).build(),
            )?;
        }
        Some("swift") => {
            types.swift(&Config::builder("FlareCounterTypes", Path::new(output)).build())?;
        }
        _ => return Err("language must be swift or kotlin".into()),
    }
    Ok(())
}
