use super::*;
use std::time::{Duration, UNIX_EPOCH};

fn generate(staging: &Path, content: &[u8]) -> Result<()> {
    for name in [
        "kotlin/dev/dimension/flare/rust/flare_ffi.kt",
        "swift/FlareRust.swift",
        "swift/FlareRustFFI.h",
        "swift/FlareRustFFI.modulemap",
        "RustPresenterBindings.kt",
        "RustPresenterBindings.swift",
    ] {
        let path = staging.join(name);
        fs::create_dir_all(path.parent().unwrap())?;
        fs::write(path, content)?;
    }
    Ok(())
}

fn outputs(root: &Path) -> Vec<PathBuf> {
    [
        "bindings/kotlin/dev/dimension/flare/rust/flare_ffi.kt",
        "bindings/swift/FlareRust.swift",
        "bindings/swift/FlareRustFFI.h",
        "bindings/swift/FlareRustFFI.modulemap",
    ]
    .into_iter()
    .map(|path| root.join(path))
    .chain([KOTLIN_ADAPTER, SWIFT_ADAPTER].map(|path| root.parent().unwrap().join(path)))
    .collect()
}

#[test]
fn unchanged_outputs_keep_mtimes_and_missing_outputs_are_restored() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join("rust");
    assert_eq!(
        publish_bindings(&root, |staging| generate(staging, b"first"))?,
        6
    );
    let paths = outputs(&root);
    let modified = UNIX_EPOCH + Duration::from_secs(1_000_000);
    for path in &paths {
        File::options()
            .write(true)
            .open(path)?
            .set_modified(modified)?;
    }
    assert_eq!(
        publish_bindings(&root, |staging| generate(staging, b"first"))?,
        0
    );
    assert!(
        paths
            .iter()
            .all(|path| path.metadata().unwrap().modified().unwrap() == modified)
    );
    fs::remove_file(&paths[2])?;
    assert_eq!(
        publish_bindings(&root, |staging| generate(staging, b"first"))?,
        1
    );
    assert_eq!(fs::read(&paths[2])?, b"first");
    assert_eq!(
        publish_bindings(&root, |staging| generate(staging, b"second"))?,
        6
    );
    assert!(
        paths
            .iter()
            .all(|path| fs::read(path).unwrap() == b"second")
    );
    Ok(())
}

#[test]
fn failed_generation_keeps_outputs_and_releases_the_shared_lock() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join("rust");
    publish_bindings(&root, |staging| generate(staging, b"first"))?;
    let result = publish_bindings(&root, |staging| {
        let lock = File::options()
            .append(true)
            .open(root.join("target/bindings.lock"))?;
        assert!(lock.try_lock().is_err());
        generate(staging, b"second")?;
        bail!("generation failed")
    });
    assert!(result.is_err());
    assert!(
        outputs(&root)
            .iter()
            .all(|path| fs::read(path).unwrap() == b"first")
    );
    let lock = File::options()
        .append(true)
        .open(root.join("target/bindings.lock"))?;
    lock.try_lock()?;
    assert_eq!(fs::read_dir(root.join("target"))?.count(), 1);
    Ok(())
}

#[test]
fn generator_build_uses_an_explicit_host_and_isolates_xcode_sdk_settings() {
    let root = Path::new("/workspace/rust");
    let host = host_build_command(root, OsStr::new("cargo"), "aarch64-apple-darwin");
    for key in APPLE_SDK_ENV {
        assert!(
            host.get_envs()
                .any(|(name, value)| name == *key && value.is_none())
        );
    }
    let args: Vec<_> = host.get_args().collect();
    assert!(
        args.windows(2)
            .any(|args| args == ["--target", "aarch64-apple-darwin"])
    );
    assert!(args.contains(&OsStr::new("--locked")));
}

#[test]
fn formatter_checks_discovered_modules_and_attribute_bodies() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let root = temporary.path();
    let features = root.join("crates/core/src/presenter/features");
    fs::create_dir_all(&features)?;
    fs::create_dir(root.join("xtask"))?;
    let path = features.join("example.rs");
    fs::write(
        &path,
        "#[presenter]\nmod contract {pub struct State{pub value:u32}}\n",
    )?;
    assert!(format_sources(root, true).is_err());
    format_sources(root, false)?;
    format_sources(root, true)?;
    assert!(fs::read_to_string(path)?.contains("pub value: u32,"));
    Ok(())
}
