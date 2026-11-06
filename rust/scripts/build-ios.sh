#!/usr/bin/env bash
set -euo pipefail

cd "$SRCROOT/../rust"
export CARGO="${CARGO:-$HOME/.cargo/bin/cargo}"
export PATH="$(dirname "$CARGO"):/opt/homebrew/bin:/usr/local/bin:$PATH"
bindings="$CONFIGURATION_TEMP_DIR/CruxBindings"
"${JUST:-just}" swift-bindings "$bindings"

profile=debug
rust_flags=(--cargo-arg=--locked)
swift_flags=(-g)
if [[ "$CONFIGURATION" == Release ]]; then
    profile=release
    rust_flags+=(--release)
    swift_flags=(-O)
fi

modules="$bindings/modules"
types="$bindings/types"
output="$BUILT_PRODUCTS_DIR/rust"
rm -rf "$modules"
mkdir -p "$modules" "$output" "$DERIVED_FILE_DIR"

compile_module() {
    local module="$1"
    shift
    local module_dir="$modules/$module.swiftmodule"
    local library="$arch_dir/lib$module.a"
    mkdir -p "$module_dir"
    xcrun swiftc -sdk "$SDKROOT" -target "$swift_target" -swift-version 6 \
        -I "$bindings/ffi" -I "$modules" "${swift_flags[@]}" \
        -parse-as-library -emit-library -static -emit-module -module-name "$module" \
        -emit-module-path "$module_dir/$arch-apple-ios$simulator.swiftmodule" \
        -o "$library" "$@"
    libraries+=("$library")
}

ios_archs=""
simulator_archs=""
rust_libraries=()
swift_libraries=()
for arch in $ARCHS; do
    simulator=""
    case "$PLATFORM_NAME/$arch" in
        iphoneos/arm64)
            rust_target=aarch64-apple-ios
            ios_archs='"arm64",'
            ;;
        iphonesimulator/arm64|iphonesimulator/x86_64)
            rust_target=aarch64-apple-ios-sim
            if [[ "$arch" == x86_64 ]]; then rust_target=x86_64-apple-ios; fi
            simulator=-simulator
            simulator_archs="$simulator_archs\"$arch\","
            ;;
        *) echo "Unsupported Rust Apple target: $PLATFORM_NAME/$arch" >&2; exit 1 ;;
    esac
    rust_libraries+=("$PWD/target/$rust_target/$profile/libflare_ffi.a")
    swift_target="$arch-apple-ios$IPHONEOS_DEPLOYMENT_TARGET$simulator"
    arch_dir="$bindings/$arch"
    mkdir -p "$arch_dir"

    libraries=()
    compile_module FlareRust "$bindings/ffi/FlareFfiBoltFFI.swift"
    # Crux emits the same Serde runtime for every registered ViewModel.
    IFS= read -r first_module < "$types/modules.txt"
    compile_module Serde "$types/$first_module/Sources/Serde/"*.swift
    while IFS= read -r module; do
        compile_module "$module" "$types/$module/Sources/$module/"*.swift
    done < "$types/modules.txt"
    swift_libraries+=("$arch_dir/libflare_viewmodels.a")
    xcrun libtool -static -o "$arch_dir/libflare_viewmodels.a" "${libraries[@]}"
done
test "${#swift_libraries[@]}" -gt 0

overlay="$DERIVED_FILE_DIR/boltffi-apple.toml"
printf '[targets.apple]\ninclude_macos = false\nios_architectures = [%s]\nsimulator_architectures = [%s]\n' \
    "$ios_archs" "$simulator_archs" > "$overlay"
(
    cd crates/flare-ffi
    env -u SDKROOT -u IPHONEOS_DEPLOYMENT_TARGET -u MACOSX_DEPLOYMENT_TARGET \
        -u TVOS_DEPLOYMENT_TARGET -u WATCHOS_DEPLOYMENT_TARGET -u XROS_DEPLOYMENT_TARGET \
        ../../target/tools/bin/boltffi --overlay "$overlay" build apple "${rust_flags[@]}"
)

combine_archs() {
    local library="$1"
    shift
    if [[ "$#" == 1 ]]; then
        cp "$1" "$output/$library"
    else
        xcrun lipo -create "$@" -output "$output/$library"
    fi
}
combine_archs libflare_viewmodels.a "${swift_libraries[@]}"
combine_archs libflare_ffi.a "${rust_libraries[@]}"
