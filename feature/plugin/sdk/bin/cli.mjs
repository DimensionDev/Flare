#!/usr/bin/env node

import { randomUUID } from "node:crypto";
import { cp, lstat, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const usage = "Usage: flare-plugin-sdk init <directory>";

async function main() {
  const args = process.argv.slice(2);
  if (args.includes("--help") || args.includes("-h")) {
    console.log(usage);
    return;
  }
  if (args.length !== 2 || args[0] !== "init" || !args[1] || args[1].startsWith("-")) {
    throw new Error(usage);
  }

  const target = resolve(args[1]);
  const name = basename(target);
  if (!name.trim() || /[\u0000-\u001f\u007f]/.test(args[1])) {
    throw new Error("Choose a directory name without control characters or blank text");
  }
  const metadata = await lstat(target).catch((error) => {
    if (error.code !== "ENOENT") throw error;
  });
  if (metadata && (!metadata.isDirectory() || metadata.isSymbolicLink() || (await readdir(target)).length)) {
    throw new Error("Destination must be a new or empty directory, not a file or symbolic link");
  }

  const template = fileURLToPath(new URL("../skill/flare-plugin/assets/github-template/", import.meta.url));
  const manifestPath = join(template, "manifest.json");
  const manifest = JSON.parse(await readFile(manifestPath, "utf8"));
  const identity = randomUUID().replaceAll("-", "");
  manifest.id = `local.plugin.p${identity}`;
  manifest.name = name;
  manifest.platform.id = `Plugin${identity}`;
  manifest.platform.name = name;

  await mkdir(target, { recursive: true });
  for (const entry of await readdir(template)) {
    if (entry === "manifest.json") continue;
    await cp(join(template, entry), join(target, entry), {
      recursive: true,
      force: false,
      errorOnExist: true,
    });
  }
  await writeFile(join(target, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, { flag: "wx" });

  console.log(`Created ${target}\nPlugin ID: ${manifest.id}\nPlatform ID: ${manifest.platform.id}`);
  console.log("Adapt the example API in plugin.js and README.md, then load this directory in Flare's Plugin development.");
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
