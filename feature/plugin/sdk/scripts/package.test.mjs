import assert from "node:assert/strict";
import { execFileSync, spawn } from "node:child_process";
import { once } from "node:events";
import { cpSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";
import Ajv from "ajv";
import vm from "node:vm";

const sdk = fileURLToPath(new URL("../", import.meta.url));
const metadata = JSON.parse(readFileSync(join(sdk, "package.json"), "utf8"));

test("npm tarball preserves the SDK and creates a typed, buildable plugin project", { timeout: 60000 }, async (t) => {
  const directory = mkdtempSync(join(tmpdir(), "flare-plugin-sdk-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const toolingCache = execFileSync(process.execPath, [process.env.npm_execpath, "config", "get", "cache"], { encoding: "utf8" }).trim();
  const npm = (args, cwd = directory, cache = join(directory, "cache")) =>
    execFileSync(process.execPath, [process.env.npm_execpath, ...args], {
      cwd,
      encoding: "utf8",
      env: {
        ...process.env,
        npm_config_cache: cache,
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

  const projectName = "typed plugin 插件";
  const generated = join(directory, projectName);
  execFileSync(process.execPath, [
    join(dirname(process.env.npm_execpath), "npx-cli.js"), "--offline", metadata.name, "init", projectName,
  ], {
    cwd: directory,
    env: { ...process.env, npm_config_cache: join(directory, "cache"), npm_config_update_notifier: "false" },
  });
  assert.equal(JSON.parse(readFileSync(join(generated, "manifest.json"), "utf8")).name, projectName);
  const generatedManifest = JSON.parse(readFileSync(join(generated, "manifest.json"), "utf8"));
  assert.equal(generatedManifest.$schema, `https://unpkg.com/flare-plugin-sdk@${metadata.version}/skill/flare-plugin/manifest.schema.json`);
  const validate = new Ajv({ strict: true }).compile(JSON.parse(readFileSync(join(installed, "skill/flare-plugin/manifest.schema.json"), "utf8")));
  assert.equal(validate(generatedManifest), true, JSON.stringify(validate.errors));
  execFileSync(process.execPath, ["--check", join(generated, "plugin.js")]);
  const project = JSON.parse(readFileSync(join(generated, "package.json"), "utf8"));
  for (const tool of ["typescript", "esbuild"]) {
    assert.equal(project.devDependencies[tool], metadata.devDependencies[tool]);
  }
  npm(["install", "--prefer-offline", "--ignore-scripts", "--no-save", "--no-audit", "--no-fund", "--package-lock=false", join(directory, archive.filename)], generated, toolingCache);
  const prebuilt = readFileSync(join(generated, "plugin.js"));
  npm(["run", "check"], generated);
  npm(["run", "build"], generated);
  assert.deepEqual(readFileSync(join(generated, "plugin.js")), prebuilt, "Rebuild and commit the canonical plugin.js when the TS source changes");
  let registrations = 0;
  vm.runInNewContext(prebuilt.toString("utf8"), {
    defineFlareSocialPlugin(plugin) {
      registrations += 1;
      assert.equal(typeof plugin.login.token.begin, "function");
      assert.equal(typeof plugin.capabilities.timeline.page, "function");
    },
    flare: new Proxy({}, { get() { throw new Error("Host calls during registration"); } }),
  });
  assert.equal(registrations, 1);
  assert.doesNotMatch(prebuilt.toString("utf8"), /\brequire\s*\(|\bmodule\.exports|\bprocess\./);
  mkdirSync(join(generated, "locales"));
  writeFileSync(join(generated, "locales/en.json"), '{"example":"Translation"}\n');
  writeFileSync(join(generated, "locales/README.md"), "Not a locale catalog\n");
  writeFileSync(join(generated, "assets/README.md"), "Not an icon\n");
  npm(["run", "package"], generated);
  const generatedPackage = join(generated, "plugin.fpp");
  assert(readFileSync(generatedPackage).length > 0);

  writeFileSync(join(generated, "src/type-check.ts"), `
const profile: ProfileV1 = { key: { id: "1", host: "example.org" }, handle: "alice", displayName: "Alice" };
const page: PageV1<PostV1> = { items: [{ key: profile.key, author: profile, createdAt: "2026-01-01T00:00:00Z", content: { value: "Text" } }] };
// @ts-expect-error Unsupported Host method.
flare.fetch("https://example.org");
// @ts-expect-error Invalid SDK error code.
const code: PluginErrorCodeV1 = "Typo";
// @ts-expect-error Browser globals are unavailable.
fetch("https://example.org");
// @ts-expect-error Node globals are unavailable.
process.cwd();
`);
  npm(["run", "check"], generated);
  writeFileSync(join(generated, "src/broken.ts"), 'const broken: number = "invalid";\n');
  assert.throws(() => npm(["run", "build"], generated));
  assert.deepEqual(readFileSync(join(generated, "plugin.js")), prebuilt, "Failed type checking must preserve the working script");
  rmSync(join(generated, "src/broken.ts"));

  const sourcePath = join(generated, "src/plugin.ts");
  const originalSource = readFileSync(sourcePath, "utf8");
  writeFileSync(join(generated, "src/message.ts"), 'export const message = "Bundled module marker";\n');
  writeFileSync(sourcePath, 'import { message } from "./message";\n' + originalSource.replace('"Enter an access token"', "message"));
  npm(["run", "build"], generated);
  assert.match(readFileSync(join(generated, "plugin.js"), "utf8"), /Bundled module marker/);
  const moduleSource = readFileSync(sourcePath, "utf8");
  writeFileSync(sourcePath, 'import { readFile } from "node:fs";\n' + moduleSource.replace("message);", "String(readFile));"));
  assert.throws(() => execFileSync(process.execPath, [join(generated, "build.mjs")], { cwd: generated, stdio: "pipe" }));
  writeFileSync(sourcePath, moduleSource);

  const watcher = spawn(process.execPath, [join(generated, "build.mjs"), "--watch"], { cwd: generated, stdio: ["ignore", "pipe", "pipe"] });
  const closed = once(watcher, "exit");
  let log = "";
  watcher.stdout.on("data", chunk => { log += chunk; });
  watcher.stderr.on("data", chunk => { log += chunk; });
  async function waitFor(check) {
    const deadline = Date.now() + 10000;
    while (!check()) {
      assert.equal(watcher.exitCode, null, log);
      assert(Date.now() < deadline, log);
      await new Promise(resolve => setTimeout(resolve, 25));
    }
  }
  try {
    await waitFor(() => log.includes("watching for changes"));
    writeFileSync(join(generated, "src/message.ts"), 'export const message = "Watch rebuild marker";\n');
    await waitFor(() => readFileSync(join(generated, "plugin.js"), "utf8").includes("Watch rebuild marker"));
    const working = readFileSync(join(generated, "plugin.js"));
    log = "";
    writeFileSync(sourcePath, "defineFlareSocialPlugin({\n");
    await waitFor(() => log.includes("[ERROR]"));
    assert.deepEqual(readFileSync(join(generated, "plugin.js")), working);
    writeFileSync(sourcePath, originalSource);
    await waitFor(() => readFileSync(join(generated, "plugin.js")).equals(prebuilt));
  } finally {
    watcher.kill();
    await closed;
  }

  writeFileSync(join(directory, "plugin.ts"), `
const plugin: PluginDefinitionV1 = {};
defineFlareSocialPlugin(plugin);
const host: FlareHostV1 = flare;
void host.crypto.uuid();
// @ts-expect-error Social plugins use the explicit registration entry point.
definePlugin(plugin);
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

  const runtime = join(directory, "runtime");
  mkdirSync(runtime);
  for (const path of ["manifest.json", "plugin.js", "assets/icon.png", "locales/en.json"]) {
    mkdirSync(dirname(join(runtime, path)), { recursive: true });
    cpSync(join(generated, path), join(runtime, path));
  }
  const direct = join(directory, "direct.fpp");
  execFileSync(process.execPath, [join(sdk, metadata.bin["flare-plugin-pack"]), runtime, direct]);
  assert.deepEqual(readFileSync(generatedPackage), readFileSync(direct));
  t.diagnostic(`Verified ${archive.files.length} files, offline SDK install, typed project build, stale-output guard, SDK types, module bundling, watch recovery, and packaging`);
});
