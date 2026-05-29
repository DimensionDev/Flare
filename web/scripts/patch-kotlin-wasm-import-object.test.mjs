import assert from 'node:assert/strict';
import { test } from 'node:test';

import { parseJsCodeEntries, patchImportObjectText } from './patch-kotlin-wasm-import-object.mjs';

const targetText = `
const js_code = {
    'present' : () => 1
}

export const importObject = { js_code };
`;

const sourceText = `
const js_code = {
    'present' : () => 1,
    'missing.simple' : (ref) => Number(ref),
    'missing.complex' :
    (() => {
        const data = { value: ',' };
        return () => data.value;
    })()
}

export const importObject = { js_code };
`;

test('patches only wasm-required missing js_code imports', () => {
	const result = patchImportObjectText({
		targetText,
		sourceText,
		requiredJsCodeImports: ['present', 'missing.complex']
	});

	assert.deepEqual(result.patchedEntries, ['missing.complex']);
	assert.match(result.text, /'present' : \(\) => 1,\n    'missing\.complex' :/);
	assert.doesNotMatch(result.text, /missing\.simple/);

	const parsed = parseJsCodeEntries(result.text);
	assert.equal(parsed.entries.has('present'), true);
	assert.equal(parsed.entries.has('missing.complex'), true);
	assert.equal(parsed.entries.has('missing.simple'), false);
});

test('is idempotent when all wasm-required imports are already present', () => {
	const first = patchImportObjectText({
		targetText,
		sourceText,
		requiredJsCodeImports: ['present', 'missing.simple']
	});
	const second = patchImportObjectText({
		targetText: first.text,
		sourceText,
		requiredJsCodeImports: ['present', 'missing.simple']
	});

	assert.deepEqual(second.patchedEntries, []);
	assert.equal(second.text, first.text);
});

test('fails when a required import is absent from the source import object', () => {
	assert.throws(
		() =>
			patchImportObjectText({
				targetText,
				sourceText,
				requiredJsCodeImports: ['missing.from.source']
			}),
		/Cannot patch Kotlin\/Wasm import object/
	);
});
