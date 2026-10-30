import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { lstatSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const cli = fileURLToPath(new URL("../bin/cli.mjs", import.meta.url));
const template = fileURLToPath(new URL("../skill/flare-plugin/assets/github-template/", import.meta.url));
const original = JSON.parse(readFileSync(join(template, "manifest.json"), "utf8"));
const { version } = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));
const run = (args, cwd) => spawnSync(process.execPath, [cli, ...args], { cwd, encoding: "utf8" });

function temporary(t) {
  const directory = mkdtempSync(join(tmpdir(), "flare-plugin-init-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return directory;
}

test("init copies the bundled template and creates valid independent identities", (t) => {
  const directory = temporary(t);
  const manifests = [];
  for (const target of [join("first", "my-plugin"), join("second", "my-plugin"), "插件 with spaces"]) {
    const name = basename(target);
    const result = run(["init", target], directory);
    assert.equal(result.status, 0, result.stderr);
    const project = join(directory, target);
    assert.match(result.stdout, /Created/);
    const manifest = JSON.parse(readFileSync(join(project, "manifest.json"), "utf8"));
    assert.equal(manifest.$schema, `https://unpkg.com/flare-plugin-sdk@${version}/skill/flare-plugin/manifest.schema.json`);
    assert.match(manifest.id, /^[a-z][a-z0-9]*(?:\.[a-z][a-z0-9-]*)+$/);
    assert.match(manifest.platform.id, /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/);
    assert.notEqual(manifest.id, original.id);
    assert.notEqual(manifest.platform.id, original.platform.id);
    assert.equal(manifest.name, name);
    assert.equal(manifest.platform.name, name);
    assert.deepEqual(
      {
        ...manifest,
        $schema: original.$schema,
        id: original.id,
        name: original.name,
        platform: { ...manifest.platform, id: original.platform.id, name: original.platform.name },
      },
      original,
    );
    assert.deepEqual(readdirSync(project, { recursive: true }).sort(), readdirSync(template, { recursive: true }).sort());
    for (const path of ["plugin.js", "README.md", "assets/icon.png"]) {
      assert.deepEqual(readFileSync(join(project, path)), readFileSync(join(template, path)));
    }
    manifests.push(manifest);
  }
  assert.equal(new Set(manifests.map((manifest) => manifest.id)).size, manifests.length);
  assert.equal(new Set(manifests.map((manifest) => manifest.platform.id)).size, manifests.length);
});

test("init accepts an empty current directory and a nested new directory", (t) => {
  const directory = temporary(t);
  assert.equal(run(["init", "."], directory).status, 0);
  assert.equal(JSON.parse(readFileSync(join(directory, "manifest.json"))).name, basename(directory));
  const nested = join(directory, "new parent", "child");
  const result = run(["init", nested], directory);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(readFileSync(join(nested, "manifest.json"))).name, "child");
});

test("init refuses non-empty directories, files and symlinks without changing them", (t) => {
  const directory = temporary(t);
  const occupied = join(directory, "occupied");
  mkdirSync(occupied);
  writeFileSync(join(occupied, ".keep"), "preserve me");
  writeFileSync(join(occupied, "manifest.json"), "original manifest");
  const hidden = join(directory, "hidden-only");
  mkdirSync(hidden);
  writeFileSync(join(hidden, ".keep"), "hidden file");
  const file = join(directory, "file");
  writeFileSync(file, "original file");
  const empty = join(directory, "empty");
  mkdirSync(empty);
  const link = join(directory, "link");
  symlinkSync(empty, link, "dir");
  const dangling = join(directory, "dangling");
  symlinkSync(join(directory, "missing"), dangling, "dir");
  for (const target of [occupied, hidden, file, link, dangling]) {
    const result = run(["init", target], directory);
    assert.equal(result.status, 1, result.stderr);
    assert.match(result.stderr, /new or empty directory/);
  }
  assert.deepEqual(readdirSync(occupied).sort(), [".keep", "manifest.json"]);
  assert.equal(readFileSync(join(occupied, ".keep"), "utf8"), "preserve me");
  assert.equal(readFileSync(join(occupied, "manifest.json"), "utf8"), "original manifest");
  assert.deepEqual(readdirSync(hidden), [".keep"]);
  assert.equal(readFileSync(join(hidden, ".keep"), "utf8"), "hidden file");
  assert.equal(readFileSync(file, "utf8"), "original file");
  assert.deepEqual(readdirSync(empty), []);
  assert(lstatSync(link).isSymbolicLink());
  assert(lstatSync(dangling).isSymbolicLink());
});

test("help and invalid arguments never create a project", (t) => {
  const directory = temporary(t);
  for (const args of [["--help"], ["-h"], ["init", "--help"]]) {
    const result = run(args, directory);
    assert.equal(result.status, 0);
    assert.match(result.stdout, /Usage: flare-plugin-sdk init <directory>/);
  }
  for (const args of [
    [], ["init"], ["init", ""], ["other", "project"], ["init", "project", "extra"],
    ["init", "--force"], ["init", " "], ["init", "bad\nname"],
  ]) {
    const result = run(args, directory);
    assert.equal(result.status, 1, JSON.stringify(args));
    assert(result.stderr.trim());
  }
  assert.deepEqual(readdirSync(directory), []);
});
