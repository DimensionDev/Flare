use std::{
    env,
    ffi::OsStr,
    fs::{self, File},
    io::{ErrorKind, Write},
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, bail};

const KOTLIN_ADAPTER: &str =
    "app/src/main/java/dev/dimension/flare/common/RustPresenterBindings.kt";
const SWIFT_ADAPTER: &str = "appleApp/ios/Common/RustPresenterBindings.swift";
const APPLE_SDK_ENV: &[&str] = &[
    "SDKROOT",
    "IPHONEOS_DEPLOYMENT_TARGET",
    "MACOSX_DEPLOYMENT_TARGET",
    "TVOS_DEPLOYMENT_TARGET",
    "WATCHOS_DEPLOYMENT_TARGET",
    "XROS_DEPLOYMENT_TARGET",
];

fn main() -> Result<()> {
    let args: Vec<_> = env::args_os().skip(1).collect();
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).parent().unwrap();
    let cargo = env::var_os("CARGO").unwrap_or_else(|| "cargo".into());
    match args.as_slice() {
        [task] if task == "bindings" => bindings(root, &cargo),
        [task] if task == "fmt" => format_sources(root, false),
        [task, flag] if task == "fmt" && flag == "--check" => format_sources(root, true),
        [flag] if flag == "--help" || flag == "-h" => {
            println!(
                "usage: cargo xtask bindings | fmt [--check]\nGenerate bindings or format all Rust sources, including discovered modules."
            );
            Ok(())
        }
        _ => bail!("usage: cargo xtask bindings | fmt [--check]"),
    }
}

fn format_sources(root: &Path, check: bool) -> Result<()> {
    let mut files = Vec::new();
    for directory in ["crates", "xtask"] {
        collect_rust_sources(&root.join(directory), &mut files)?;
    }
    files.sort();
    let mut command = Command::new("rustfmt");
    command.current_dir(root).args(["--edition", "2024"]);
    if check {
        command.arg("--check");
    }
    run(command.args(files))
}

fn collect_rust_sources(directory: &Path, files: &mut Vec<PathBuf>) -> Result<()> {
    for entry in fs::read_dir(directory)? {
        let entry = entry?;
        if entry.file_type()?.is_dir() {
            collect_rust_sources(&entry.path(), files)?;
        } else if entry.path().extension().is_some_and(|ext| ext == "rs") {
            files.push(entry.path());
        }
    }
    Ok(())
}

fn run(command: &mut Command) -> Result<()> {
    let status = command
        .status()
        .with_context(|| format!("could not run {command:?}"))?;
    if !status.success() {
        bail!("{command:?} failed with {status}");
    }
    Ok(())
}

fn host_command(program: &OsStr) -> Command {
    let mut command = Command::new(program);
    // Xcode's target SDK must not leak into executable host tools.
    for key in APPLE_SDK_ENV {
        command.env_remove(key);
    }
    command
}

fn host_build_command(root: &Path, cargo: &OsStr, target: &str) -> Command {
    let mut command = host_command(cargo);
    command
        .current_dir(root)
        .args(["build", "--locked", "-p", "flare-ffi", "--target", target])
        .args(["--features", "bindgen"])
        .arg("--target-dir")
        .arg(root.join("target"));
    command
}

fn bindings(root: &Path, cargo: &OsStr) -> Result<()> {
    let changed = publish_bindings(root, |staging| {
        let version = host_command(cargo).arg("-vV").output()?;
        if !version.status.success() {
            bail!(
                "cargo -vV failed: {}",
                String::from_utf8_lossy(&version.stderr)
            );
        }
        let version = String::from_utf8(version.stdout)?;
        let host = version
            .lines()
            .find_map(|line| line.strip_prefix("host: "))
            .context("cargo -vV did not report a host target")?;
        run(&mut host_build_command(root, cargo, host))?;
        let tools = root.join("target").join(host).join("debug");
        let library = tools.join(format!(
            "{}flare_ffi{}",
            env::consts::DLL_PREFIX,
            env::consts::DLL_SUFFIX
        ));
        for language in ["kotlin", "swift"] {
            run(host_command(tools.join("uniffi-bindgen").as_os_str())
                .current_dir(root)
                .args(["generate", "--library"])
                .arg(&library)
                .args(["--language", language, "--out-dir"])
                .arg(staging.join(language))
                .arg("--no-format"))?;
        }
        run(host_command(tools.join("presenter-bindings").as_os_str())
            .current_dir(root)
            .arg(staging.join("RustPresenterBindings.kt"))
            .arg(staging.join("RustPresenterBindings.swift")))
    })?;
    println!("Rust bindings: {changed} file(s) updated");
    Ok(())
}

fn publish_bindings(root: &Path, generate: impl FnOnce(&Path) -> Result<()>) -> Result<usize> {
    let target = root.join("target");
    fs::create_dir_all(&target)?;
    let lock = File::options()
        .create(true)
        .append(true)
        .open(target.join("bindings.lock"))?;
    // Android and Xcode share generated sources; serialize compilation and publication.
    lock.lock()?;
    let staging = tempfile::Builder::new()
        .prefix("bindings-")
        .tempdir_in(&target)?;
    generate(staging.path())?;
    let mut outputs = Vec::new();
    for language in ["kotlin", "swift"] {
        collect_outputs(
            &staging.path().join(language),
            &root.join("bindings").join(language),
            &mut outputs,
        )?;
    }
    for destination in [KOTLIN_ADAPTER, SWIFT_ADAPTER] {
        let destination = root.parent().unwrap().join(destination);
        let content = fs::read(staging.path().join(destination.file_name().unwrap()))?;
        outputs.push((destination, content));
    }
    let mut changed = 0;
    for (destination, content) in outputs {
        changed += usize::from(update_file(&destination, &content)?);
    }
    Ok(changed)
}

fn collect_outputs(
    source: &Path,
    destination: &Path,
    outputs: &mut Vec<(PathBuf, Vec<u8>)>,
) -> Result<()> {
    for entry in fs::read_dir(source)? {
        let entry = entry?;
        let output = destination.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            collect_outputs(&entry.path(), &output, outputs)?;
        } else {
            outputs.push((output, fs::read(entry.path())?));
        }
    }
    Ok(())
}

fn update_file(destination: &Path, content: &[u8]) -> Result<bool> {
    match fs::read(destination) {
        Ok(previous) if previous == content => return Ok(false),
        Err(error) if error.kind() != ErrorKind::NotFound => return Err(error.into()),
        _ => {}
    }
    let parent = destination
        .parent()
        .context("output must have a parent directory")?;
    fs::create_dir_all(parent)?;
    // Replace complete files so a concurrent compiler cannot read partial output.
    let mut temporary = tempfile::NamedTempFile::new_in(parent)?;
    temporary.write_all(content)?;
    temporary.persist(destination)?;
    Ok(true)
}

#[cfg(test)]
mod tests;
