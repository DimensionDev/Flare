import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const sdk = fileURLToPath(new URL("../", import.meta.url));
const metadata = JSON.parse(readFileSync(join(sdk, "package.json"), "utf8"));

test("npm tarball preserves the complete SDK and works as an offline dependency", (t) => {
  const directory = mkdtempSync(join(tmpdir(), "flare-plugin-sdk-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const npm = (args, cwd = directory) =>
    execFileSync(process.execPath, [process.env.npm_execpath, ...args], {
      cwd,
      encoding: "utf8",
      env: {
        ...process.env,
        npm_config_cache: join(directory, "cache"),
        npm_config_update_notifier: "false",
      },
    });

  const [archive] = JSON.parse(npm(["pack", "--json", "--ignore-scripts", "--pack-destination", directory], sdk));
  const resources = readdirSync(join(sdk, "skill"), { recursive: true })
    .filter((path) => statSync(join(sdk, "skill", path)).isFile())
    .map((path) => `skill/${path.split(sep).join("/")}`);
  assert.deepEqual(
    archive.files.map(({ path }) => path).sort(),
    ["LICENSE", "README.md", "package.json", "bin/cli.mjs", ...resources].sort(),
  );
  assert.equal(
    readFileSync(join(sdk, "LICENSE"), "utf8"),
    readFileSync(resolve(sdk, "../../../LICENSE"), "utf8"),
  );

  writeFileSync(join(directory, "package.json"), JSON.stringify({ private: true }));
  npm([
    "install", "--offline", "--ignore-scripts", "--no-audit", "--no-fund",
    "--package-lock=false", join(directory, archive.filename),
  ]);
  const installed = join(directory, "node_modules", metadata.name);
  for (const path of resources) {
    assert.deepEqual(readFileSync(join(installed, path)), readFileSync(join(sdk, path)), path);
  }

  const generated = join(directory, "my-plugin");
  execFileSync(process.execPath, [
    join(dirname(process.env.npm_execpath), "npx-cli.js"), "--offline", metadata.name, "init", "my-plugin",
  ], {
    cwd: directory,
    env: { ...process.env, npm_config_cache: join(directory, "cache"), npm_config_update_notifier: "false" },
  });
  assert.equal(JSON.parse(readFileSync(join(generated, "manifest.json"), "utf8")).name, "my-plugin");
  execFileSync(process.execPath, ["--check", join(generated, "plugin.js")]);
  const generatedPackage = join(directory, "generated.fpp");
  npm(["exec", "--offline", "--", "flare-plugin-pack", generated, generatedPackage]);
  assert(readFileSync(generatedPackage).length > 0);

  writeFileSync(join(directory, "plugin.ts"), `
const plugin: PluginDefinitionV1 = {};
definePlugin(plugin);
const host: FlareHostV1 = flare;
void host.crypto.uuid();
// @ts-expect-error The Host exposes a fixed API, not arbitrary methods.
flare.fetch("https://example.org");
`);
  writeFileSync(join(directory, "tsconfig.json"), JSON.stringify({
    compilerOptions: {
      strict: true,
      noEmit: true,
      target: "ES2022",
      module: "NodeNext",
      moduleResolution: "NodeNext",
      lib: ["ES2022"],
      types: [metadata.name],
    },
    files: ["plugin.ts"],
  }));
  execFileSync(process.execPath, [
    join(sdk, "node_modules/typescript/bin/tsc"), "--project", join(directory, "tsconfig.json"),
  ]);

  const template = join(installed, "skill/flare-plugin/assets/github-template");
  const packaged = join(directory, "plugin.fpp");
  npm(["exec", "--offline", "--", "flare-plugin-pack", template, packaged]);
  const direct = join(directory, "direct.fpp");
  execFileSync(process.execPath, [join(sdk, metadata.bin["flare-plugin-pack"]), template, direct]);
  assert.deepEqual(readFileSync(packaged), readFileSync(direct));
  t.diagnostic(`Verified ${archive.files.length} packed files, offline npx initialization, consumer types, and installed CLIs`);
});
