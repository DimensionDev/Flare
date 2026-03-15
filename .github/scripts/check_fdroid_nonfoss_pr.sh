#!/usr/bin/env bash

set -euo pipefail

SUSS_URL="${SUSS_URL:-https://fdroid.gitlab.io/fdroid-suss/suss.json}"
relevant_file_pattern='(^|/)(build\.gradle(\.kts)?|settings\.gradle(\.kts)?|libs\.versions\.toml)$'
start_marker='START Non-FOSS component'
end_marker='END Non-FOSS component'

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

suss_json="$tmp_dir/suss.json"
patterns_file="$tmp_dir/gradle_signatures.txt"

curl -fsSL "$SUSS_URL" -o "$suss_json"
jq -r '.signatures | to_entries[] | .value.gradle_signatures[]?' "$suss_json" > "$patterns_file"

if [[ ! -s "$patterns_file" ]]; then
  echo "No gradle_signatures found in $SUSS_URL" >&2
  exit 2
fi

repo_files=()
while IFS= read -r file; do
  repo_files+=("$file")
done < <(git ls-files | rg "$relevant_file_pattern" || true)

if [[ ${#repo_files[@]} -eq 0 ]]; then
  echo "No F-Droid-sensitive dependency files found."
  exit 0
fi

violations=()

for file in "${repo_files[@]}"; do
  sanitized_file="$tmp_dir/$(echo "$file" | tr '/' '_').sanitized"
  awk -v start="$start_marker" -v end="$end_marker" '
    {
      if (index($0, start)) {
        if (skip) {
          print "Nested START Non-FOSS component marker in " FILENAME > "/dev/stderr"
          exit 2
        }
        skip = 1
        print ""
        next
      }
      if (index($0, end)) {
        if (!skip) {
          print "END Non-FOSS component marker without START in " FILENAME > "/dev/stderr"
          exit 2
        }
        skip = 0
        print ""
        next
      }
      if (skip) {
        print ""
      } else {
        print
      }
    }
    END {
      if (skip) {
        print "Unclosed START Non-FOSS component marker in " FILENAME > "/dev/stderr"
        exit 2
      }
    }
  ' "$file" > "$sanitized_file"

  matches=()
  while IFS= read -r match; do
    matches+=("$match")
  done < <(
    rg -n -P -f "$patterns_file" "$sanitized_file" \
      || true
  )

  if [[ ${#matches[@]} -gt 0 ]]; then
    violations+=("$file")
    for match in "${matches[@]}"; do
      violations+=("${match/${sanitized_file}:/${file}:}")
    done
  fi
done

if [[ ${#violations[@]} -eq 0 ]]; then
  echo "No repo-wide Gradle dependencies matched F-Droid suss signatures outside allowed Non-FOSS blocks."
  exit 0
fi

echo "Detected repo-wide Gradle dependencies matching F-Droid suss signatures outside allowed Non-FOSS blocks:"
printf ' - %s\n' "${violations[@]}"
echo
echo "Source blacklist: ${SUSS_URL}"
exit 1
