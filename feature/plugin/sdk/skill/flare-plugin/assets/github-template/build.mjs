import { execFileSync } from "node:child_process";
import { cp, mkdir, mkdtemp, readdir, rename, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { build, context } from "esbuild";

const root = fileURLToPath(new URL(".", import.meta.url));
const mode = process.argv[2];
if (process.argv.length > 3 || (mode && !["--watch", "--package"].includes(mode))) {
  throw new Error("Usage: node build.mjs [--watch|--package]");
}
const options = {
  absWorkingDir: root,
  entryPoints: ["src/plugin.ts"],
  outfile: "plugin.js",
  bundle: true,
  format: "iife",
  platform: "neutral",
  mainFields: ["module", "main"],
  target: "es2020",
  logLevel: "info",
  write: false,
  plugins: [{
    name: "plugin-output",
    setup(builder) {
      builder.onEnd(async (result) => {
        if (result.errors.length) return;
        if (result.outputFiles.length !== 1) throw new Error("A plugin build must emit one JavaScript file");
        const temporary = await mkdtemp(join(root, ".plugin-build-"));
        try {
          // Preserve the installable script while edits are invalid or a build is interrupted.
          await writeFile(join(temporary, "plugin.js"), result.outputFiles[0].contents, { flag: "wx" });
          await rename(join(temporary, "plugin.js"), join(root, "plugin.js"));
        } finally {
          await rm(temporary, { recursive: true, force: true });
        }
      });
    },
  }],
};

if (mode === "--watch") {
  const watcher = await context(options);
  await watcher.watch();
} else {
  await build(options);
  if (mode === "--package") {
    const staging = await mkdtemp(join(tmpdir(), "flare-plugin-package-"));
    try {
      for (const name of ["manifest.json", "plugin.js"]) {
        await cp(join(root, name), join(staging, name));
      }
      await mkdir(join(staging, "assets"));
      for (const name of await readdir(join(root, "assets"))) {
        if (/^icon\.(?:png|jpe?g|webp|gif|ico)$/.test(name)) {
          await cp(join(root, "assets", name), join(staging, "assets", name));
        }
      }
      const catalogs = await readdir(join(root, "locales")).catch((error) => {
        if (error.code !== "ENOENT") throw error;
        return [];
      });
      if (catalogs.length) {
        await mkdir(join(staging, "locales"));
        for (const name of catalogs.filter((name) => name.endsWith(".json"))) {
          await cp(join(root, "locales", name), join(staging, "locales", name));
        }
      }
      const packer = fileURLToPath(import.meta.resolve("flare-plugin-sdk/skill/flare-plugin/pack.mjs"));
      execFileSync(process.execPath, [packer, staging, join(root, "plugin.fpp")], { stdio: "inherit" });
    } finally {
      await rm(staging, { recursive: true, force: true });
    }
  }
}
