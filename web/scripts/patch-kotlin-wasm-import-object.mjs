import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, '../..');

const defaultPaths = {
	wasmPath: path.join(repoRoot, 'web-shared/build/dist/wasmJs/productionLibrary/flare.wasm'),
	targetImportObjectPath: path.join(
		repoRoot,
		'web-shared/build/dist/wasmJs/productionLibrary/flare.import-object.mjs'
	),
	sourceImportObjectPath: path.join(
		repoRoot,
		'web-shared/build/compileSync/wasmJs/main/productionLibrary/kotlin/flare.import-object.mjs'
	)
};

export async function patchKotlinWasmProductionImportObject(paths = defaultPaths) {
	const [wasmBuffer, targetText, sourceText] = await Promise.all([
		fs.readFile(paths.wasmPath),
		fs.readFile(paths.targetImportObjectPath, 'utf8'),
		fs.readFile(paths.sourceImportObjectPath, 'utf8')
	]);

	const requiredJsCodeImports = collectRequiredJsCodeImports(wasmBuffer);
	const result = patchImportObjectText({
		targetText,
		sourceText,
		requiredJsCodeImports
	});

	if (result.patchedEntries.length > 0) {
		await fs.writeFile(paths.targetImportObjectPath, result.text);
	}

	return {
		requiredJsCodeImports: requiredJsCodeImports.length,
		patchedEntries: result.patchedEntries,
		targetImportObjectPath: paths.targetImportObjectPath
	};
}

export function collectRequiredJsCodeImports(wasmBuffer) {
	const wasmModule = new WebAssembly.Module(wasmBuffer);
	return WebAssembly.Module.imports(wasmModule)
		.filter((item) => item.module === 'js_code')
		.map((item) => item.name);
}

export function patchImportObjectText({ targetText, sourceText, requiredJsCodeImports }) {
	const targetJsCode = parseJsCodeEntries(targetText);
	const sourceJsCode = parseJsCodeEntries(sourceText);
	const missing = requiredJsCodeImports.filter((name) => !targetJsCode.entries.has(name));

	if (missing.length === 0) {
		return {
			text: targetText,
			patchedEntries: []
		};
	}

	const missingEntries = missing.map((name) => {
		const entry = sourceJsCode.entries.get(name);
		if (entry == null) {
			throw new Error(`Cannot patch Kotlin/Wasm import object. Missing source entry: ${name}`);
		}
		return `    ${entry}`;
	});

	const hasExistingEntries = targetText
		.slice(targetJsCode.openBraceIndex + 1, targetJsCode.closeBraceIndex)
		.trim().length > 0;
	const insertIndex = hasExistingEntries
		? findLastNonWhitespaceIndex(targetText, targetJsCode.closeBraceIndex) + 1
		: targetJsCode.closeBraceIndex;
	const insertion = hasExistingEntries
		? `,\n${missingEntries.join(',\n')}`
		: `\n${missingEntries.join(',\n')}\n`;
	const patchedText =
		targetText.slice(0, insertIndex) + insertion + targetText.slice(insertIndex);

	const patchedJsCode = parseJsCodeEntries(patchedText);
	const stillMissing = requiredJsCodeImports.filter((name) => !patchedJsCode.entries.has(name));
	if (stillMissing.length > 0) {
		throw new Error(
			`Kotlin/Wasm import object patch did not satisfy all wasm imports: ${stillMissing.join(', ')}`
		);
	}

	return {
		text: patchedText,
		patchedEntries: missing
	};
}

export function parseJsCodeEntries(text) {
	const declarationIndex = text.indexOf('const js_code =');
	if (declarationIndex < 0) {
		throw new Error('Cannot find Kotlin/Wasm js_code import object declaration.');
	}

	const openBraceIndex = text.indexOf('{', declarationIndex);
	if (openBraceIndex < 0) {
		throw new Error('Cannot find Kotlin/Wasm js_code import object body.');
	}

	const closeBraceIndex = findMatchingBrace(text, openBraceIndex);
	const entries = new Map();
	let index = openBraceIndex + 1;

	while (index < closeBraceIndex) {
		index = skipWhitespaceAndComments(text, index, closeBraceIndex);
		if (index >= closeBraceIndex) break;

		const entryStart = index;
		const key = readStringLiteral(text, index);
		index = skipWhitespaceAndComments(text, key.endIndex, closeBraceIndex);

		if (text[index] !== ':') {
			throw new Error(`Expected ':' after js_code entry '${key.value}'.`);
		}

		const entryEnd = findEntryEnd(text, index + 1, closeBraceIndex);
		entries.set(key.value, text.slice(entryStart, entryEnd).trimEnd());
		index = entryEnd;

		if (text[index] === ',') {
			index += 1;
		}
	}

	return {
		closeBraceIndex,
		entries,
		openBraceIndex
	};
}

function findEntryEnd(text, startIndex, limitIndex) {
	let depth = 0;
	let index = startIndex;

	while (index < limitIndex) {
		const char = text[index];
		const next = text[index + 1];

		if (char === '"' || char === "'" || char === '`') {
			index = skipStringLiteral(text, index);
			continue;
		}
		if (char === '/' && next === '/') {
			index = skipLineComment(text, index);
			continue;
		}
		if (char === '/' && next === '*') {
			index = skipBlockComment(text, index);
			continue;
		}

		if (char === '(' || char === '[' || char === '{') {
			depth += 1;
		} else if (char === ')' || char === ']' || char === '}') {
			depth -= 1;
			if (depth < 0) {
				throw new Error('Unexpected closing delimiter while parsing js_code import object.');
			}
		} else if (char === ',' && depth === 0) {
			return index;
		}

		index += 1;
	}

	return limitIndex;
}

function findMatchingBrace(text, openBraceIndex) {
	let depth = 0;
	let index = openBraceIndex;

	while (index < text.length) {
		const char = text[index];
		const next = text[index + 1];

		if (char === '"' || char === "'" || char === '`') {
			index = skipStringLiteral(text, index);
			continue;
		}
		if (char === '/' && next === '/') {
			index = skipLineComment(text, index);
			continue;
		}
		if (char === '/' && next === '*') {
			index = skipBlockComment(text, index);
			continue;
		}

		if (char === '{') {
			depth += 1;
		} else if (char === '}') {
			depth -= 1;
			if (depth === 0) return index;
		}

		index += 1;
	}

	throw new Error('Cannot find matching js_code import object closing brace.');
}

function readStringLiteral(text, startIndex) {
	const quote = text[startIndex];
	if (quote !== '"' && quote !== "'") {
		throw new Error('Expected js_code entry key to be a string literal.');
	}

	let value = '';
	let index = startIndex + 1;

	while (index < text.length) {
		const char = text[index];
		if (char === '\\') {
			value += text[index + 1] ?? '';
			index += 2;
			continue;
		}
		if (char === quote) {
			return {
				endIndex: index + 1,
				value
			};
		}
		value += char;
		index += 1;
	}

	throw new Error('Unterminated js_code entry key string literal.');
}

function skipStringLiteral(text, startIndex) {
	const quote = text[startIndex];
	let index = startIndex + 1;

	while (index < text.length) {
		const char = text[index];
		if (char === '\\') {
			index += 2;
			continue;
		}
		if (char === quote) {
			return index + 1;
		}
		index += 1;
	}

	throw new Error('Unterminated string literal while parsing js_code import object.');
}

function skipWhitespaceAndComments(text, startIndex, limitIndex) {
	let index = startIndex;
	while (index < limitIndex) {
		const char = text[index];
		const next = text[index + 1];

		if (/\s/.test(char)) {
			index += 1;
		} else if (char === '/' && next === '/') {
			index = skipLineComment(text, index);
		} else if (char === '/' && next === '*') {
			index = skipBlockComment(text, index);
		} else {
			break;
		}
	}
	return index;
}

function findLastNonWhitespaceIndex(text, beforeIndex) {
	let index = beforeIndex - 1;
	while (index >= 0 && /\s/.test(text[index])) {
		index -= 1;
	}
	return index;
}

function skipLineComment(text, startIndex) {
	const newlineIndex = text.indexOf('\n', startIndex + 2);
	return newlineIndex < 0 ? text.length : newlineIndex + 1;
}

function skipBlockComment(text, startIndex) {
	const endIndex = text.indexOf('*/', startIndex + 2);
	if (endIndex < 0) {
		throw new Error('Unterminated block comment while parsing js_code import object.');
	}
	return endIndex + 2;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
	const result = await patchKotlinWasmProductionImportObject();
	if (result.patchedEntries.length === 0) {
		console.log(
			`Kotlin/Wasm production import object already satisfies ${result.requiredJsCodeImports} js_code imports.`
		);
	} else {
		console.log(
			`Patched ${result.patchedEntries.length} missing Kotlin/Wasm js_code imports in ${result.targetImportObjectPath}.`
		);
	}
}
