use std::{fmt::Write, fs, path::Path, process::Command};

fn copy_sources(source: &Path, destination: &Path) {
    fs::create_dir_all(destination).unwrap();
    for entry in fs::read_dir(source).unwrap() {
        let entry = entry.unwrap();
        let name = entry.file_name();
        if ["target", "bindings", ".git"]
            .iter()
            .any(|skip| name == *skip)
        {
            continue;
        }
        if entry.file_type().unwrap().is_dir() {
            copy_sources(&entry.path(), &destination.join(name));
        } else {
            fs::copy(entry.path(), destination.join(name)).unwrap();
        }
    }
}

fn check(root: &Path, target: &Path) -> std::process::Output {
    Command::new(env!("CARGO"))
        .current_dir(root)
        .args(["check", "--locked", "--offline", "-p", "flare-ffi", "--lib"])
        .arg("--target-dir")
        .arg(target)
        .output()
        .unwrap()
}

#[test]
fn real_compiler_rejects_conditional_dtos_and_exports_large_flat_graphs() {
    let workspace = Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .unwrap()
        .parent()
        .unwrap();
    let temporary = tempfile::tempdir().unwrap();
    let root = temporary.path();
    copy_sources(workspace, root);
    let target = workspace.join("target/contract-checks");
    let features = root.join("crates/flare-core/src/presenter/features");
    let probe = features.join("audit.rs");
    for source in [
        "#[flare_presenter_macros::presenter_dto] pub struct Audit { #[cfg(any())] pub value: u32 }",
        "#[flare_presenter_macros::presenter_dto] pub struct Audit { #[cfg_attr(not(target_os = \"ios\"), cfg(any()))] pub value: u32 }",
        "#[derive(Clone)] #[flare_presenter_macros::presenter_dto] pub struct Audit { #[cfg_attr(all(), cfg(any()))] pub value: u32 }",
        "#[flare_presenter_macros::presenter_dto] pub enum Audit { #[cfg(any())] Missing, Present }",
        "#[flare_presenter_macros::presenter_dto] pub enum Audit { Value { #[cfg(any())] value: u32 } }",
        "#[flare_presenter_macros::presenter_error] pub enum Audit { Closed, #[cfg(any())] Failed }",
        "#[flare_presenter_macros::presenter_error] pub enum Audit { Closed, Failed { #[cfg_attr(all(), cfg(any()))] value: u32 } }",
    ] {
        fs::write(&probe, source).unwrap();
        let result = check(root, &target);
        let stderr = String::from_utf8_lossy(&result.stderr);
        assert!(
            !result.status.success(),
            "conditional contract compiled: {source}"
        );
        assert!(
            stderr.contains("conditional presenter contracts"),
            "{stderr}"
        );
    }

    let derives = "#[flare_presenter_macros::presenter_dto]\n#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]\n";
    let mut source = "#[flare_presenter_macros::presenter_dto]\n#[derive(Clone, Debug, Default, PartialEq, Eq)]\npub struct CatalogProfile { pub name: String,\n".to_owned();
    for index in 0..128 {
        writeln!(source, "pub field_{index}: AuditDto{index},").unwrap();
    }
    source.push_str("}\n");
    for index in 0..128 {
        writeln!(
            source,
            "{derives}pub struct AuditDto{index} {{ pub value: u32 }}"
        )
        .unwrap();
    }
    fs::write(&probe, source).unwrap();
    fs::copy(
        root.join("crates/flare-core/tests/fixtures/catalog/profile.rs"),
        features.join("audit_presenter.rs"),
    )
    .unwrap();
    let result = check(root, &target);
    assert!(
        result.status.success(),
        "{}",
        String::from_utf8_lossy(&result.stderr)
    );

    let source = fs::read_to_string(&probe).unwrap().replace(
        "pub struct CatalogProfile {",
        "pub struct CatalogProfile { pub conflict: UiStateOfCounterState,",
    );
    fs::write(
        &probe,
        format!("{source}\n{derives}pub struct UiStateOfCounterState {{ pub value: u32 }}"),
    )
    .unwrap();
    let result = check(root, &target);
    assert!(!result.status.success());
    assert!(
        String::from_utf8_lossy(&result.stderr)
            .contains("duplicate contract type: UiStateOfCounterState")
    );
}
