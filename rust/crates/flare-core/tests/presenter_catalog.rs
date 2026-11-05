use std::fs;

use flare_presenter_schema::codegen::generate_index;

const PROFILE: &str = include_str!("fixtures/catalog/profile.rs");
const SHARED: &str = include_str!("fixtures/catalog/shared.rs");

fn names(index: &str) -> Vec<String> {
    let file = syn::parse_file(index).unwrap();
    let syn::Item::Const(registry) = file.items.last().unwrap() else {
        panic!("generated index must end with the catalog");
    };
    let syn::Expr::Reference(reference) = &*registry.expr else {
        panic!("catalog must be borrowed")
    };
    let syn::Expr::Array(array) = &*reference.expr else {
        panic!("catalog must be an array")
    };
    array
        .elems
        .iter()
        .map(|entry| {
            let syn::Expr::Path(path) = entry else {
                panic!("expected schema factory")
            };
            path.path.segments.first().unwrap().ident.to_string()
        })
        .collect()
}

#[test]
fn adding_renaming_and_removing_business_files_updates_the_catalog() {
    let source = tempfile::tempdir().unwrap();
    assert!(names(&generate_index(source.path()).unwrap()).is_empty());
    fs::write(source.path().join("profile.rs"), PROFILE).unwrap();
    fs::write(source.path().join("shared.rs"), SHARED).unwrap();
    let index = generate_index(source.path()).unwrap();
    assert_eq!(names(&index), ["CatalogProbePresenter"]);
    assert!(index.contains("pub use profile :: *"));
    assert!(index.contains("pub use shared :: *"));
    assert_eq!(generate_index(source.path()).unwrap(), index);

    fs::rename(
        source.path().join("profile.rs"),
        source.path().join("renamed.rs"),
    )
    .unwrap();
    let renamed = generate_index(source.path()).unwrap();
    assert_eq!(names(&renamed), ["CatalogProbePresenter"]);
    assert!(renamed.contains("pub use renamed :: *"));
    assert!(!renamed.contains("pub use profile :: *"));

    fs::remove_file(source.path().join("renamed.rs")).unwrap();
    assert!(names(&generate_index(source.path()).unwrap()).is_empty());
}

#[test]
fn only_explicit_top_level_contracts_are_registered_in_stable_order() {
    let source = tempfile::tempdir().unwrap();
    fs::write(source.path().join("z_last.rs"), PROFILE).unwrap();
    fs::write(
        source.path().join("a_first.rs"),
        PROFILE.replace("CatalogProbe", "Second"),
    )
    .unwrap();
    fs::write(
        source.path().join("helpers.rs"),
        r"
        pub struct OrdinaryPresenter;
        // #[presenter] mod comment { pub struct CommentPresenter { session: () } }
        #[cfg(test)] mod tests {
            #[presenter] mod contract { pub struct TestPresenter { session: () } }
        }
    ",
    )
    .unwrap();
    assert_eq!(
        names(&generate_index(source.path()).unwrap()),
        ["CatalogProbePresenter", "SecondPresenter"]
    );
}

#[test]
fn duplicate_conditional_and_malformed_contracts_fail_with_the_source_file() {
    let source = tempfile::tempdir().unwrap();
    let path = source.path().join("profile.rs");
    fs::write(&path, PROFILE).unwrap();
    fs::write(source.path().join("duplicate.rs"), PROFILE).unwrap();
    assert!(
        generate_index(source.path())
            .unwrap_err()
            .to_string()
            .contains("duplicate presenter CatalogProbePresenter")
    );
    fs::remove_file(source.path().join("duplicate.rs")).unwrap();
    for (from, to, expected) in [
        (
            "#[flare_presenter_macros::presenter]",
            "#[cfg(test)] #[flare_presenter_macros::presenter]",
            "conditional",
        ),
        (
            "pub struct CatalogProbePresenter",
            "#[cfg(test)] pub struct CatalogProbePresenter",
            "conditional",
        ),
        (
            "pub struct CatalogProbePresenter",
            "struct CatalogProbePresenter",
            "must be public",
        ),
        (
            "session: PresenterSession",
            "other: PresenterSession",
            "exactly one public presenter",
        ),
        ("pub struct CatalogProbeState", "pub struct 123", "expected"),
    ] {
        fs::write(&path, PROFILE.replace(from, to)).unwrap();
        let error = generate_index(source.path()).unwrap_err().to_string();
        assert!(
            error.contains("profile.rs") && error.contains(expected),
            "{error}"
        );
    }
}
