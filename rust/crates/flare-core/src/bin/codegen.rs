use std::{error::Error, fs, path::Path};

use crux_core::type_generation::facet::{BoltFfi, Config};
use flare_core::view_model_registry::{ViewModelDescriptor, registered_view_models};

#[derive(Clone, Copy)]
enum Language {
    Kotlin,
    Swift,
}

fn main() -> Result<(), Box<dyn Error>> {
    let args: Vec<_> = std::env::args_os().skip(1).collect();
    let [language, output] = args.as_slice() else {
        return Err("usage: codegen <swift|kotlin> <output directory>".into());
    };
    let language = match language.to_str() {
        Some("kotlin") => Language::Kotlin,
        Some("swift") => Language::Swift,
        _ => return Err("language must be swift or kotlin".into()),
    };
    generate_view_models(language, Path::new(output))
}

fn generate_view_models(language: Language, output: &Path) -> Result<(), Box<dyn Error>> {
    let mut view_models: Vec<_> = registered_view_models().collect();
    if view_models.is_empty() {
        return Err("no native ViewModels registered".into());
    }
    view_models.sort_unstable_by_key(|vm| vm.id);
    for pair in view_models.windows(2) {
        if pair[0].id == pair[1].id {
            return Err(format!("duplicate ViewModel ID: {}", pair[0].id).into());
        }
    }

    let mut modules = String::new();
    for view_model in view_models {
        generate(language, output, view_model)?;
        modules.push_str(view_model.swift_module);
        modules.push('\n');
    }
    if matches!(language, Language::Swift) {
        fs::write(output.join("modules.txt"), modules)?;
    }
    Ok(())
}

fn generate(
    language: Language,
    output: &Path,
    view_model: &ViewModelDescriptor,
) -> Result<(), Box<dyn Error>> {
    let ViewModelDescriptor {
        ffi_class: ffi,
        swift_module,
        kotlin_package,
        ..
    } = *view_model;
    let types = view_model.type_registry()?.build()?.boltffi(
        BoltFfi::new()
            .class(ffi)
            .swift("FlareRust")
            .kotlin_package("dev.dimension.flare.rust"),
    );
    let name = ffi
        .strip_suffix("Ffi")
        .ok_or("ViewModel FFI names must end in Ffi")?;
    let event = &types.app().ok_or("missing Crux App metadata")?.event.name;
    let render_only = types
        .effects()
        .first()
        .is_some_and(|effect| effect.variants.iter().all(|variant| variant.render));

    match language {
        Language::Kotlin => {
            types.kotlin(&Config::builder(kotlin_package, output).build())?;
            let handler_default = if render_only {
                " = object : EffectHandler {}"
            } else {
                ""
            };
            fs::write(
                output
                    .join(kotlin_package.replace('.', "/"))
                    .join("ViewModelAccess.kt"),
                format!(
                    r#"package {kotlin_package}

import androidx.compose.runtime.Composable
import dev.dimension.flare.common.rememberCruxViewModel

internal typealias {name} = Core

@Composable
internal fun remember{name}(
    initialEvent: {event}? = null,
    handler: EffectHandler{handler_default},
): {name} =
    rememberCruxViewModel(key = "{kotlin_package}", createBridge = {{ FfiBridge() }}) {{ bridge, scope ->
        Core(bridge, handler, scope).apply {{ initialEvent?.let(::update) }}
    }}
"#
                ),
            )?;
        }
        Language::Swift => {
            types.swift(&Config::builder(swift_module, output).build())?;
            let (parameters, handler, declaration) = if render_only {
                (
                    " = nil",
                    "RenderOnlyHandler()",
                    "private struct RenderOnlyHandler: EffectHandler {}\n\n",
                )
            } else {
                (", handler: any EffectHandler", "handler", "")
            };
            fs::write(
                output
                    .join(swift_module)
                    .join("Sources")
                    .join(swift_module)
                    .join("ViewModelAccess.swift"),
                format!(
                    r"public typealias {name} = Core

{declaration}extension Core {{
    public convenience init(_ initialEvent: {event}?{parameters}) {{
        self.init(handler: {handler})
        if let initialEvent {{
            update(initialEvent)
        }}
    }}
}}
"
                ),
            )?;
        }
    }
    Ok(())
}
