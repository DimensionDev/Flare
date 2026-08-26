import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const baseEnglish = JSON.parse(
  readFileSync(new URL("../messages/en.json", import.meta.url), "utf8"),
);
const americanEnglish = JSON.parse(
  readFileSync(new URL("../messages/en-US.json", import.meta.url), "utf8"),
);

test("en-US mirrors base English outside temporarily exempt AI settings", () => {
  assert.deepEqual(
    Object.keys(americanEnglish).sort(),
    Object.keys(baseEnglish).sort(),
  );

  for (const [key, value] of Object.entries(baseEnglish)) {
    if (!key.startsWith("settingsAi")) {
      assert.equal(americanEnglish[key], value, key);
    }
  }
});
