import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import Ajv from "ajv";

const sdk = new URL("../skill/flare-plugin/", import.meta.url);
const schema = JSON.parse(readFileSync(new URL("manifest.schema.json", sdk), "utf8"));
const validate = new Ajv({ allErrors: true, strict: true }).compile(schema);
const fixtures = JSON.parse(readFileSync(new URL("fixtures/manifest-schema.json", import.meta.url), "utf8"));

function merge(base, patch) {
  base = base && typeof base === "object" && !Array.isArray(base) ? base : {};
  const result = { ...base };
  for (const [key, value] of Object.entries(patch)) {
    result[key] = value && typeof value === "object" && !Array.isArray(value)
      ? merge(base[key] ?? {}, value)
      : value;
  }
  return result;
}

test("draft-07 schema validates canonical manifests and every editor regression", (t) => {
  for (const path of ["assets/github-template/manifest.json", "examples/pixelfed/manifest.json"]) {
    assert.equal(validate(JSON.parse(readFileSync(new URL(path, sdk), "utf8"))), true, JSON.stringify(validate.errors));
  }
  for (const fixture of fixtures.cases) {
    const manifest = merge(fixtures.base, fixture.patch);
    const before = JSON.stringify(manifest);
    assert.equal(validate(manifest), fixture.schemaValid, `${fixture.name}: ${JSON.stringify(validate.errors)}`);
    assert.equal(JSON.stringify(manifest), before, "Schema validation must not change defaults or metadata");
  }
  t.diagnostic(`Verified ${fixtures.cases.length} manifest cases; semantic-only Host failures remain explicit`);
});
