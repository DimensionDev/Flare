#!/usr/bin/env bash
set -euo pipefail

icon_dir="iosApp/flare"
asset_catalog="iosApp/flare/Assets.xcassets"
swift_output="iosApp/flare/Common/AppIconOption.swift"
project_file="iosApp/project.yml"
prefix="app_icon_preview"
size="256"
platform="iOS"
rendition="Default"
overwrite="false"
generate_previews="true"
generate_appiconsets="true"
generate_swift="true"
update_project="true"
ictool="/Applications/Xcode.app/Contents/Applications/Icon Composer.app/Contents/Executables/ictool"

usage() {
  cat <<'EOF'
Generate regular image assets from Icon Composer .icon bundles.

Usage:
  iosApp/scripts/generate_icon_previews.sh [options]

Options:
  --icon-dir <path>        Directory containing AppIcon*.icon bundles. Default: iosApp/flare
  --asset-catalog <path>   Output .xcassets directory. Default: iosApp/flare/Assets.xcassets
  --swift-output <path>    Output AppIconOption.swift path. Default: iosApp/flare/Common/AppIconOption.swift
  --project-file <path>    XcodeGen project spec to update. Default: iosApp/project.yml
  --prefix <name>          Output image asset prefix. Default: app_icon_preview
  --size <px>              PNG width/height. Default: 256
  --platform <name>        ictool platform. Default: iOS
  --rendition <name>       ictool rendition. Default: Default
  --ictool <path>          Path to Icon Composer ictool.
  --overwrite              Replace existing preview .imageset directories.
  --skip-appiconsets       Do not generate alternate .appiconset assets.
  --skip-previews          Only generate the Swift icon list.
  --skip-swift             Only generate preview image assets.
  --skip-project           Do not update alternate app icon names in the XcodeGen spec.
  -h, --help               Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --icon-dir)
      icon_dir="$2"
      shift 2
      ;;
    --asset-catalog)
      asset_catalog="$2"
      shift 2
      ;;
    --swift-output)
      swift_output="$2"
      shift 2
      ;;
    --project-file)
      project_file="$2"
      shift 2
      ;;
    --prefix)
      prefix="$2"
      shift 2
      ;;
    --size)
      size="$2"
      shift 2
      ;;
    --platform)
      platform="$2"
      shift 2
      ;;
    --rendition)
      rendition="$2"
      shift 2
      ;;
    --ictool)
      ictool="$2"
      shift 2
      ;;
    --overwrite)
      overwrite="true"
      shift
      ;;
    --skip-appiconsets)
      generate_appiconsets="false"
      shift
      ;;
    --skip-previews)
      generate_previews="false"
      shift
      ;;
    --skip-swift)
      generate_swift="false"
      shift
      ;;
    --skip-project)
      update_project="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ( "$generate_previews" == "true" || "$generate_appiconsets" == "true" ) && ! -x "$ictool" ]]; then
  echo "error: ictool not found at: $ictool" >&2
  echo "Install/open Xcode with Icon Composer, or pass --ictool <path>." >&2
  exit 1
fi

if [[ ! -d "$icon_dir" ]]; then
  echo "error: icon directory does not exist: $icon_dir" >&2
  exit 1
fi

if [[ ( "$generate_previews" == "true" || "$generate_appiconsets" == "true" ) && ! -d "$asset_catalog" ]]; then
  echo "error: asset catalog does not exist: $asset_catalog" >&2
  exit 1
fi

if [[ "$generate_previews" == "true" || "$generate_appiconsets" == "true" ]] && { ! [[ "$size" =~ ^[0-9]+$ ]] || [[ "$size" -le 0 ]]; }; then
  echo "error: --size must be a positive integer" >&2
  exit 1
fi

export_size="$size"
if command -v sips >/dev/null 2>&1 && [[ "$size" -gt 1 ]]; then
  export_size="$((size + 1))"
fi

shopt -s nullglob
icon_bundles=("$icon_dir"/AppIcon*.icon)
shopt -u nullglob

if [[ "${#icon_bundles[@]}" -eq 0 ]]; then
  echo "error: no AppIcon*.icon bundles found in: $icon_dir" >&2
  exit 1
fi

IFS=$'\n' icon_bundles=($(printf '%s\n' "${icon_bundles[@]}" | sort | awk '
  /\/AppIcon\.icon$/ {
    primary = $0
    next
  }
  {
    print
  }
  END {
    if (primary != "") {
      print primary
    }
  }
'))
unset IFS

preview_suffix_for_icon_name() {
  local icon_name="$1"

  if [[ "$icon_name" == "AppIcon" ]]; then
    echo "default"
  elif [[ "$icon_name" == AppIcon_* ]]; then
    echo "${icon_name#AppIcon_}"
  else
    echo "$icon_name"
  fi
}

title_for_icon_name() {
  local icon_name="$1"
  local suffix

  suffix="$(preview_suffix_for_icon_name "$icon_name")"
  if [[ "$suffix" == "default" ]]; then
    echo "Default"
  else
    printf '%s\n' "$suffix" | tr '_' ' ' | awk '{
      for (i = 1; i <= NF; i++) {
        $i = toupper(substr($i, 1, 1)) substr($i, 2)
      }
      print
    }'
  fi
}

normalize_png() {
  local png_path="$1"

  if command -v sips >/dev/null 2>&1; then
    sips -Z "$size" "$png_path" --out "$png_path" >/dev/null
  fi
}

if [[ "$generate_swift" == "true" ]]; then
  mkdir -p "$(dirname "$swift_output")"

  {
    cat <<'EOF'
import Foundation

struct AppIconOption: Identifiable {
    let title: String
    let alternateIconName: String?
    let previewImageName: String

    var id: String {
        alternateIconName ?? "AppIcon"
    }
}

extension AppIconOption {
    static let all: [AppIconOption] = [
EOF

    for icon_bundle in "${icon_bundles[@]}"; do
      icon_name="$(basename "$icon_bundle" .icon)"
      suffix="$(preview_suffix_for_icon_name "$icon_name")"
      title="$(title_for_icon_name "$icon_name")"
      asset_name="${prefix}_${suffix}"

      if [[ "$icon_name" == "AppIcon" ]]; then
        echo "        .init(title: \"$title\", alternateIconName: nil, previewImageName: \"$asset_name\"),"
      else
        echo "        .init(title: \"$title\", alternateIconName: \"$icon_name\", previewImageName: \"$asset_name\"),"
      fi
    done

    cat <<'EOF'
    ]

    static func previewImageName(for alternateIconName: String?) -> String {
        all.first { $0.alternateIconName == alternateIconName }?.previewImageName ?? "app_icon_preview_default"
    }
}
EOF
  } > "$swift_output"

  echo "Generated $swift_output"
fi

if [[ "$update_project" == "true" ]]; then
  if [[ ! -f "$project_file" ]]; then
    echo "error: Xcode project spec does not exist: $project_file" >&2
    exit 1
  fi

  alternate_names=()
  for icon_bundle in "${icon_bundles[@]}"; do
    icon_name="$(basename "$icon_bundle" .icon)"
    if [[ "$icon_name" != "AppIcon" ]]; then
      alternate_names+=("$icon_name")
    fi
  done

  alternate_names_string="${alternate_names[*]}"
  if grep -q "ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES:" "$project_file"; then
    perl -0pi -e "s/ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES: \"[^\"]*\"/ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES: \"$alternate_names_string\"/g" "$project_file"
    echo "Updated alternate app icon names in $project_file"
  elif grep -q "ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES" "$project_file"; then
    perl -0pi -e "s/ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES = \"[^\"]*\";/ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES = \"$alternate_names_string\";/g" "$project_file"
    echo "Updated alternate app icon names in $project_file"
  else
    echo "warning: ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES was not found in $project_file" >&2
  fi

  perl -0pi -e 's/ASSETCATALOG_COMPILER_INCLUDE_ALL_APPICON_ASSETS: YES/ASSETCATALOG_COMPILER_INCLUDE_ALL_APPICON_ASSETS: NO/g' "$project_file"
  perl -0pi -e 's/ASSETCATALOG_COMPILER_INCLUDE_ALL_APPICON_ASSETS = YES;/ASSETCATALOG_COMPILER_INCLUDE_ALL_APPICON_ASSETS = NO;/g' "$project_file"
  echo "Disabled include-all app icon assets in $project_file"

  if grep -q "membershipExceptions = (" "$project_file"; then
    exception_lines=$'\t\t\t\tInfo.plist,\n'
    for icon_name in "${alternate_names[@]}"; do
      exception_lines+=$'\t\t\t\t'"$icon_name.icon"$',\n'
    done

    EXCEPTION_LINES="$exception_lines" perl -0pi -e '
      my $lines = $ENV{"EXCEPTION_LINES"};
      s/membershipExceptions = \(\n.*?\t\t\t\);/membershipExceptions = (\n$lines\t\t\t);/s;
    ' "$project_file"
    echo "Excluded alternate .icon sources from the Flare target in $project_file"
  fi
fi

if [[ "$generate_previews" != "true" && "$generate_appiconsets" != "true" ]]; then
  exit 0
fi

for icon_bundle in "${icon_bundles[@]}"; do
  icon_name="$(basename "$icon_bundle" .icon)"
  suffix="$(preview_suffix_for_icon_name "$icon_name")"

  if [[ "$generate_previews" == "true" ]]; then
    asset_name="${prefix}_${suffix}"
    imageset="$asset_catalog/${asset_name}.imageset"
    png_name="${asset_name}.png"
    png_path="$imageset/$png_name"

    if [[ -e "$imageset" && "$overwrite" != "true" ]]; then
      echo "Skipping existing $(basename "$imageset"). Use --overwrite to replace it."
    else
      if [[ -e "$imageset" ]]; then
        rm -rf "$imageset"
      fi

      mkdir -p "$imageset"

      if ! "$ictool" "$icon_bundle" \
        --export-image \
        --output-file "$png_path" \
        --platform "$platform" \
        --rendition "$rendition" \
        --width "$export_size" \
        --height "$export_size" \
        --scale 1; then
        rm -rf "$imageset"
        echo "error: ictool failed for $icon_bundle" >&2
        echo "If this happens only inside Codex, run this script from Terminal or allow the ictool command outside the sandbox." >&2
        exit 1
      fi

      normalize_png "$png_path"

      cat > "$imageset/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "$png_name",
      "idiom" : "universal",
      "scale" : "1x"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF

      echo "Generated $imageset"
    fi
  fi

  if [[ "$generate_appiconsets" == "true" && "$icon_name" != "AppIcon" ]]; then
    appiconset="$asset_catalog/${icon_name}.appiconset"
    png_name="${icon_name}.png"
    png_path="$appiconset/$png_name"

    if [[ -e "$appiconset" ]]; then
      if [[ "$overwrite" == "true" ]]; then
        rm -rf "$appiconset"
      else
        echo "Skipping existing $(basename "$appiconset"). Use --overwrite to replace it."
        continue
      fi
    fi

    mkdir -p "$appiconset"

    if ! "$ictool" "$icon_bundle" \
      --export-image \
      --output-file "$png_path" \
      --platform "$platform" \
      --rendition "$rendition" \
      --width "$export_size" \
      --height "$export_size" \
      --scale 1; then
      rm -rf "$appiconset"
      echo "error: ictool failed for $icon_bundle" >&2
      echo "If this happens only inside Codex, run this script from Terminal or allow the ictool command outside the sandbox." >&2
      exit 1
    fi

    normalize_png "$png_path"

    cat > "$appiconset/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "$png_name",
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "${size}x${size}"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF

    echo "Generated $appiconset"
  fi
done
