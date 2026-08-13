#!/usr/bin/env node

import { lstat, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, join, relative, resolve, sep } from "node:path";
import process from "node:process";

const MAX_FILES = 256;
const MAX_TOTAL_BYTES = 50 * 1024 * 1024;
const ALLOWED_PATH = /^(?:manifest\.json|plugin\.js|assets\/icon\.png|locales\/[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*\.json)$/;
const REQUIRED_PATHS = new Set(["manifest.json", "plugin.js", "assets/icon.png"]);

async function main() {
  const [inputArgument, outputArgument] = process.argv.slice(2);
  if (!inputArgument || process.argv.length > 4) {
    throw new Error("Usage: node pack.mjs <plugin-directory> [output.fpp]");
  }

  const input = resolve(inputArgument);
  const rootMetadata = await lstat(input);
  if (!rootMetadata.isDirectory() || rootMetadata.isSymbolicLink()) {
    throw new Error("Plugin source must be a real directory");
  }

  const files = await collectFiles(input);
  const names = files.map((file) => file.name);
  for (const required of REQUIRED_PATHS) {
    if (!names.includes(required)) throw new Error(`Missing required file: ${required}`);
  }

  const manifest = JSON.parse((await readFile(join(input, "manifest.json"))).toString("utf8"));
  const packageName = `${manifest.id ?? "plugin"}-${manifest.version ?? "0.0.0"}.fpp`;
  const output = resolve(outputArgument ?? packageName);
  if (!output.endsWith(".fpp")) throw new Error("Output file must use the .fpp extension");

  const archive = createZip(files);
  if (archive.length > 20 * 1024 * 1024) throw new Error("Package exceeds 20 MiB");
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, archive);
  process.stdout.write(`${output}\n`);
}

async function collectFiles(root) {
  const result = [];

  async function visit(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((left, right) => left.name.localeCompare(right.name, "en"));
    for (const entry of entries) {
      const absolute = join(directory, entry.name);
      const metadata = await lstat(absolute);
      if (metadata.isSymbolicLink()) throw new Error(`Symbolic links are not supported: ${entry.name}`);
      if (metadata.isDirectory()) {
        await visit(absolute);
        continue;
      }
      if (!metadata.isFile()) throw new Error(`Unsupported filesystem entry: ${entry.name}`);
      const name = relative(root, absolute).split(sep).join("/");
      if (name === "README.md") continue;
      if (!ALLOWED_PATH.test(name)) throw new Error(`Unsupported package path: ${name}`);
      if (![...name].every((character) => character.codePointAt(0) >= 0x20 && character.codePointAt(0) <= 0x7e)) {
        throw new Error(`Package paths must be ASCII: ${name}`);
      }
      result.push({ name, data: await readFile(absolute) });
    }
  }

  await visit(root);
  result.sort((left, right) => left.name.localeCompare(right.name, "en"));
  if (result.length === 0 || result.length > MAX_FILES) throw new Error("Invalid package file count");
  if (result.reduce((total, file) => total + file.data.length, 0) > MAX_TOTAL_BYTES) {
    throw new Error("Package exceeds 50 MiB uncompressed");
  }
  const folded = result.map((file) => file.name.toLowerCase());
  if (new Set(folded).size !== folded.length) throw new Error("Duplicate or case-conflicting package path");
  return result;
}

function createZip(files) {
  const localParts = [];
  const centralParts = [];
  let localOffset = 0;

  for (const file of files) {
    const name = Buffer.from(file.name, "ascii");
    const checksum = crc32(file.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt16LE(0, 10);
    local.writeUInt16LE(33, 12);
    local.writeUInt32LE(checksum, 14);
    local.writeUInt32LE(file.data.length, 18);
    local.writeUInt32LE(file.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    localParts.push(local, name, file.data);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(33, 14);
    central.writeUInt32LE(checksum, 16);
    central.writeUInt32LE(file.data.length, 20);
    central.writeUInt32LE(file.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34);
    central.writeUInt16LE(0, 36);
    central.writeUInt32LE(0, 38);
    central.writeUInt32LE(localOffset, 42);
    centralParts.push(central, name);

    localOffset += local.length + name.length + file.data.length;
  }

  const centralDirectory = Buffer.concat(centralParts);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralDirectory.length, 12);
  end.writeUInt32LE(localOffset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, centralDirectory, end]);
}

function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8);
  return (value ^ 0xffffffff) >>> 0;
}

const CRC_TABLE = Array.from({ length: 256 }, (_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit += 1) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});

main().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
  process.exitCode = 1;
});
