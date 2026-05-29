import fs from 'node:fs/promises';
import path from 'node:path';

const [manifestPath, outDir] = process.argv.slice(2);

if (!manifestPath || !outDir) {
	throw new Error('Usage: node generate-presenters.mjs <manifestPath> <outDir>');
}

const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));

await fs.rm(outDir, { recursive: true, force: true });
await fs.mkdir(outDir, { recursive: true });

const exports = [];
const refTypesByName = new Map();
const refCodecs = [
	{
		kind: 'presenter',
		is: (refType) => refType?.codec === 'presenter',
		renderType: renderPresenterRefType,
		needsCall: () => false
	},
	{
		kind: 'object',
		is: () => true,
		renderType: renderObjectRefType,
		needsCall: objectRefNeedsCall
	}
];
const stateCodecs = [
	{
		kind: 'uiState',
		is: (item) => item?.kind === 'uiState',
		content: (item) => item.data,
		renderTypes: () => `type UiStateSnapshot<T> =
\t| { type: "Loading" }
\t| { type: "Error"; message: string | null }
\t| { type: "Success"; data: T };

export type UiState<T> =
\t| { type: "Loading" }
\t| { type: "Error"; message: string | null }
\t| { type: "Success"; data: T };

`,
		snapshotType: (item) => `UiStateSnapshot<${snapshotTsType(item.data)}>`,
		publicType: (item) => `UiState<${publicTsType(item.data)}>`,
		initialValue: () => '{ type: "Loading" }',
		toPublicExpression: (item, valueExpression, refsExpression, callExpression, refTypes) =>
			`((value: ${snapshotTsType(item)}) => value.type === "Success" ? { ...value, data: ${toPublicValueExpression(item.data, 'value.data', refsExpression, callExpression, refTypes)} } : value)(${valueExpression})`,
		toBridgeExpression: (item, valueExpression, refsExpression, refTypes, visitedRefs) =>
			`((value: ${publicTsType(item)}) => value.type === "Success" ? { ...value, data: ${toBridgeValueExpression(item.data, 'value.data', refsExpression, refTypes, visitedRefs)} } : value)(${valueExpression})`
	},
	{
		kind: 'pagingState',
		is: (item) => item?.kind === 'pagingState',
		content: (item) => item.item,
		renderTypes: () => `type PagingStateSnapshot =
\t| { type: "Loading" }
\t| { type: "Error"; message: string | null }
\t| { type: "Empty" }
\t| { type: "Success"; itemCount: number; isRefreshing: boolean };

export type PagingState<T> =
\t| { type: "Loading" }
\t| { type: "Error"; message: string | null }
\t| { type: "Empty" }
\t| { type: "Success"; itemCount: number; isRefreshing: boolean; peek(index: number): T | null; get(index: number): void };

`,
		snapshotType: () => 'PagingStateSnapshot',
		publicType: (item) => `PagingState<${publicTsType(item.item)}>`,
		initialValue: () => '{ type: "Loading" }',
		needsCall: true,
		needsPublicConversion: () => true,
		toPublicExpression: (item, valueExpression, refsExpression, callExpression, refTypes, options = {}) => {
			const getStatement = options.getStatement ?? 'void index';
			const peekPath = options.peekPath;
			const peekReturn = { ...item.item, nullable: true };
			const peekStatement =
				peekPath === undefined
					? 'return null;'
					: `const result = ${callExpression}(${JSON.stringify(peekPath)}, { index });\n\t\treturn ${decodeReturnExpression('result', peekReturn)};`;
			return `((value: ${snapshotTsType(item)}) => value.type === "Success" ? { ...value, peek(index: number) { ${peekStatement} }, get(index: number) { ${getStatement}; } } : value)(${valueExpression})`;
		},
		toBridgeExpression: (item, valueExpression, refsExpression, refTypes, visitedRefs) =>
			valueExpression
	}
];

for (const presenter of manifest.presenters) {
	const fileName = `${presenter.name}.svelte.ts`;
	await fs.writeFile(path.join(outDir, fileName), renderPresenter(presenter));
	exports.push(`export * from './${presenter.name}.svelte';`);
}

await fs.writeFile(path.join(outDir, 'index.ts'), `${exports.join('\n')}\n`);

function renderPresenter(presenter) {
	const parameters = presenter.parameters ?? [];
	const valueParameters = parameters.filter((parameter) => isValue(parameter));
	const callbackParameters = parameters.filter((parameter) => parameter.kind === 'callback');
	const creatable = presenter.creatable !== false;
	const factoryName = creatable ? presenter.factory : (presenter.bindFactory ?? `bind${capitalize(presenter.name)}Presenter`);
	const factoryParameters = creatable ? renderParameters(parameters) : 'presenter: WebPresenterRef';
	const enumTypes = collectEnumTypes(presenter);
	const sealedTypes = collectSealedTypes(presenter);
	const refTypes = collectRefTypes(presenter);
	const usedStateCodecs = stateCodecs.filter((codec) => presenterUses(presenter, codec.is));
	refTypesByName.clear();
	for (const refType of refTypes) {
		refTypesByName.set(refType.name, refType);
	}
	const needsCall =
		presenter.actions.some((action) => hasReturn(action)) ||
		refTypes.some((refType) => refNeedsCall(refType, refTypes)) ||
		usedStateCodecs.some((codec) => codec.needsCall === true);
	const needsRefHelpers = refTypes.length > 0 || !creatable;
	const snapshotFields = presenter.properties.map((property) => `\t${property.name}: ${snapshotTsType(property)};`);
	const stateFields = presenter.properties.map((property) => `\t${property.name}: ${publicTsType(property)};`);
	const actionFields = presenter.actions.map((action) => renderActionType(action));
	const initialFields = presenter.properties.map((property) => `\t\t${property.name}: ${initialValue(property, refTypes)},`);
	const actionInitializers = presenter.actions.map((action) => renderActionInitializer(action));
	const snapshotAssignments = presenter.properties.map((property) => renderSnapshotAssignment(property, refTypes));
	const mountPresenterLine = creatable
		? `presenterId = webPresenterCreate('${presenter.name}', ${renderCreateArgsJson(valueParameters)}, ${renderCallbacksArray(callbackParameters)});`
		: `presenterId = webPresenterBindRef('${presenter.name}', webPresenterRefValue(presenter));`;

	return `import { onMount } from 'svelte';
import {
${needsCall ? '\twebPresenterCall,\n' : ''}${!creatable ? '\twebPresenterBindRef,\n' : ''}\twebPresenterClose,
${creatable ? '\twebPresenterCreate,\n' : ''}\twebPresenterDispatch,
\twebPresenterSubscribe,
\twebPresenterUnsubscribe
} from '@flare/web-shared';

${creatable ? renderWebPresenterCallbacksType() : ''}${renderWebPresenterRefsType()}${renderWebPresenterCallTypes(needsCall)}${renderStateCodecTypes(usedStateCodecs)}${renderEnumTypes(enumTypes)}${renderSealedTypes(sealedTypes, refTypes)}${renderRefTypes(refTypes, needsRefHelpers)}
type ${presenter.stateType}Snapshot = {
${snapshotFields.join('\n')}
};

export type ${presenter.stateType} = {
${stateFields.join('\n')}
${actionFields.join('\n')}
};

export function ${factoryName}(${factoryParameters}): ${presenter.stateType} {
\tlet presenterId: number | null = null;
\tlet subscriptionId: number | null = null;
\tlet closed = false;

\tconst state = $state<${presenter.stateType}>({
${initialFields.join('\n')}
${actionInitializers.join('\n')}
\t});

\tonMount(() => {
\t\t${mountPresenterLine}
\t\tsubscriptionId = webPresenterSubscribe(presenterId, (snapshotJson, refs) => {
\t\t\tconst snapshot = JSON.parse(snapshotJson) as ${presenter.stateType}Snapshot;
${snapshotAssignments.join('\n')}
\t\t});

\t\treturn close;
\t});

\tfunction dispatch(path: string, args: Record<string, unknown> = {}, refs: unknown[] = []) {
\t\tif (closed || presenterId === null) return;
\t\twebPresenterDispatch(presenterId, JSON.stringify({ path, args }), refs as unknown as WebPresenterRefs);
\t}
${renderCallFunction(needsCall)}
\tfunction close() {
\t\tif (closed) return;
\t\tclosed = true;

\t\tif (presenterId !== null && subscriptionId !== null) {
\t\t\twebPresenterUnsubscribe(presenterId, subscriptionId);
\t\t}
\t\tif (presenterId !== null) {
\t\t\twebPresenterClose(presenterId);
\t\t}

\t\tpresenterId = null;
\t\tsubscriptionId = null;
\t}

\treturn state;
}
`;
}

function collectRefTypes(presenter) {
	const refs = new Map();
	const visited = new Set();
	const addRef = (item) => {
		if (!isRef(item)) return;
		const refType = {
			name: item.tsType,
			codec: item.codec,
			properties: item.properties ?? [],
			methods: item.methods ?? []
		};
		if (refs.has(item.tsType)) {
			refs.set(item.tsType, mergeRefTypes(refs.get(item.tsType), refType));
		} else {
			refs.set(item.tsType, refType);
		}
		if (visited.has(item.tsType)) return;
		visited.add(item.tsType);
		if (refCodecFor(refType).kind === 'presenter') return;
		for (const property of item.properties ?? []) visitValue(property, addRef);
		for (const method of item.methods ?? []) {
			for (const arg of method.args ?? []) visitValue(arg, addRef);
			visitValue(method.return, addRef);
		}
	};

	for (const parameter of presenter.parameters ?? []) {
		if (parameter.kind === 'callback') {
			for (const arg of parameter.args ?? []) visitValue(arg, addRef);
		} else {
			visitValue(parameter, addRef);
		}
	}
	for (const property of presenter.properties) visitValue(property, addRef);
	for (const action of presenter.actions) {
		for (const arg of action.args) visitValue(arg, addRef);
		visitValue(action.return, addRef);
	}
	return [...refs.values()];
}

function collectEnumTypes(presenter) {
	const enums = new Map();
	const addEnum = (item) => {
		if (isEnum(item) && !enums.has(item.tsType)) {
			enums.set(item.tsType, {
				name: item.tsType,
				values: item.values ?? []
			});
		}
	};

	for (const parameter of presenter.parameters ?? []) {
		if (parameter.kind === 'callback') {
			for (const arg of parameter.args ?? []) visitValue(arg, addEnum);
		} else {
			visitValue(parameter, addEnum);
		}
	}
	for (const property of presenter.properties ?? []) visitValue(property, addEnum);
	for (const action of presenter.actions ?? []) {
		for (const arg of action.args ?? []) visitValue(arg, addEnum);
		visitValue(action.return, addEnum);
	}
	return [...enums.values()];
}

function collectSealedTypes(presenter) {
	const sealeds = new Map();
	const addSealed = (item) => {
		if (isSealed(item) && !sealeds.has(item.tsType)) {
			sealeds.set(item.tsType, {
				name: item.tsType,
				discriminator: item.discriminator ?? 'type',
				variants: item.variants ?? []
			});
		}
	};

	for (const parameter of presenter.parameters ?? []) {
		if (parameter.kind === 'callback') {
			for (const arg of parameter.args ?? []) visitValue(arg, addSealed);
		} else {
			visitValue(parameter, addSealed);
		}
	}
	for (const property of presenter.properties ?? []) visitValue(property, addSealed);
	for (const action of presenter.actions ?? []) {
		for (const arg of action.args ?? []) visitValue(arg, addSealed);
		visitValue(action.return, addSealed);
	}
	return [...sealeds.values()];
}

function mergeRefTypes(left, right) {
	const properties = new Map((left.properties ?? []).map((property) => [property.name, property]));
	for (const property of right.properties ?? []) {
		properties.set(property.name, mergeValue(properties.get(property.name), property));
	}

	const methods = new Map((left.methods ?? []).map((method) => [method.path ?? method.name, method]));
	for (const method of right.methods ?? []) {
		methods.set(method.path ?? method.name, method);
	}

	return {
		name: left.name,
		codec: left.codec === 'presenter' || right.codec === 'presenter' ? 'presenter' : (left.codec ?? right.codec),
		properties: [...properties.values()],
		methods: [...methods.values()]
	};
}

function mergeValue(left, right) {
	if (left === undefined) return right;
	if (isRef(right) && (!isRef(left) || (right.properties ?? []).length > (left.properties ?? []).length)) return right;
	return left;
}

function visitValue(item, visitor, visitedRefs = new Set()) {
	if (item === undefined || item === null) return;
	visitor(item);

	if (isArray(item)) {
		visitValue(item.item, visitor, visitedRefs);
	}
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) {
		visitValue(stateCodec.content(item), visitor, visitedRefs);
	}
	if (isSealed(item)) {
		for (const variant of item.variants ?? []) {
			for (const property of variant.properties ?? []) visitValue(property, visitor, visitedRefs);
		}
	}
	if (isRef(item)) {
		if (visitedRefs.has(item.tsType)) return;
		visitedRefs.add(item.tsType);
		if (refCodecFor(item).kind === 'presenter') return;
		for (const property of item.properties ?? []) visitValue(property, visitor, visitedRefs);
		for (const method of item.methods ?? []) {
			for (const arg of method.args ?? []) visitValue(arg, visitor, visitedRefs);
			visitValue(method.return, visitor, visitedRefs);
		}
	}
}

function presenterUses(presenter, predicate) {
	let found = false;
	const visitor = (item) => {
		if (predicate(item)) found = true;
	};
	for (const parameter of presenter.parameters ?? []) {
		if (parameter.kind === 'callback') {
			for (const arg of parameter.args ?? []) visitValue(arg, visitor);
		} else {
			visitValue(parameter, visitor);
		}
	}
	for (const property of presenter.properties ?? []) visitValue(property, visitor);
	for (const action of presenter.actions ?? []) {
		for (const arg of action.args ?? []) visitValue(arg, visitor);
		visitValue(action.return, visitor);
	}
	return found;
}

function renderEnumTypes(enumTypes) {
	if (enumTypes.length === 0) return '';
	return `${enumTypes.map((enumType) => renderEnumType(enumType)).join('\n')}\n`;
}

function renderEnumType(enumType) {
	const values = enumType.values.map((value) => JSON.stringify(value)).join(' | ') || 'never';
	return `export type ${enumType.name} = ${values};`;
}

function renderStateCodecTypes(usedStateCodecs) {
	return usedStateCodecs.map((codec) => codec.renderTypes()).join('');
}

function renderSealedTypes(sealedTypes, refTypes) {
	if (sealedTypes.length === 0) return '';
	return `${sealedTypes.map((sealedType) => renderSealedType(sealedType, refTypes)).join('\n\n')}\n`;
}

function renderSealedType(sealedType, refTypes) {
	const variants = sealedType.variants ?? [];
	if (variants.length === 0) {
		return `type ${sealedSnapshotTypeName(sealedType.name)} = never;
export type ${sealedType.name} = never;`;
	}
	const publicType = `export type ${sealedType.name} =\n${variants.map((variant) => renderSealedVariant(sealedType, variant, publicTsType)).join('\n')};`;
	const snapshotType = `type ${sealedSnapshotTypeName(sealedType.name)} =\n${variants.map((variant) => renderSealedVariant(sealedType, variant, snapshotTsType)).join('\n')};`;
	return `${snapshotType}

${publicType}

${renderSealedToPublicFunction(sealedType, refTypes)}

${renderSealedToBridgeFunction(sealedType, refTypes)}`;
}

function renderSealedVariant(sealedType, variant, typeRenderer) {
	const fields = [
		`${JSON.stringify(sealedType.discriminator)}: ${JSON.stringify(variant.tag ?? variant.name)}`,
		...(variant.properties ?? []).map((property) => `${JSON.stringify(property.name)}: ${typeRenderer(property)}`)
	];
	return `\t| { ${fields.join('; ')} }`;
}

function renderSealedToPublicFunction(sealedType, refTypes) {
	const callParameter = sealedNeedsCall(sealedType, refTypes) ? ', call: WebPresenterCall' : '';
	const cases = (sealedType.variants ?? [])
		.map((variant) => {
			const assignments = (variant.properties ?? [])
				.map((property) => `${JSON.stringify(property.name)}: ${toPublicValueExpression(property, `value.${property.name}`, 'refs', 'call', refTypes)}`)
				.join(', ');
			return `\t\tcase ${JSON.stringify(variant.tag ?? variant.name)}:
\t\t\treturn { ...value${assignments.length === 0 ? '' : `, ${assignments}`} };`;
		})
		.join('\n');
	return `function ${toPublicSealedFunctionName(sealedType.name)}(value: ${sealedSnapshotTypeName(sealedType.name)}, refs: WebPresenterRefs${callParameter}): ${sealedType.name} {
\tswitch (value.${sealedType.discriminator}) {
${cases}
\t}
}`;
}

function renderSealedToBridgeFunction(sealedType, refTypes) {
	if (!valueContainsRef(sealedType)) return '';
	const cases = (sealedType.variants ?? [])
		.map((variant) => {
			const assignments = (variant.properties ?? [])
				.map((property) => `${JSON.stringify(property.name)}: ${toBridgeValueExpression(property, `value.${property.name}`, 'refs', refTypes)}`)
				.join(', ');
			return `\t\tcase ${JSON.stringify(variant.tag ?? variant.name)}:
\t\t\treturn { ...value${assignments.length === 0 ? '' : `, ${assignments}`} };`;
		})
		.join('\n');
	return `function ${toBridgeSealedFunctionName(sealedType.name)}(value: ${sealedType.name}, refs: unknown[]): ${sealedSnapshotTypeName(sealedType.name)} {
\tswitch (value.${sealedType.discriminator}) {
${cases}
\t}
}`;
}

function renderRefTypes(refTypes, needsRefHelpers = refTypes.length > 0) {
	if (refTypes.length === 0) return needsRefHelpers ? `${renderRefHelpers()}\n` : '';
	return `${renderRefHelpers()}\n${refTypes.map((refType) => renderRefType(refType)).join('\n\n')}\n`;
}

function renderWebPresenterRefsType() {
	return `type WebPresenterRefs = Parameters<typeof webPresenterDispatch>[2];
`;
}

function renderWebPresenterCallbacksType() {
	return `type WebPresenterCallbacks = Parameters<typeof webPresenterCreate>[2];
`;
}

function renderWebPresenterCallTypes(needsCall) {
	if (!needsCall) return '';
	return `type WebPresenterCallResult = {
\tjson: string;
\trefs: WebPresenterRefs;
};

type WebPresenterCall = (
\tpath: string,
\targs?: Record<string, unknown>,
\trefs?: unknown[]
) => WebPresenterCallResult;
`;
}

function renderRefHelpers() {
	return `export type WebPresenterRef = {
\treadonly __webPresenterRef: number;
\treadonly __webPresenterRefs: WebPresenterRefs;
};

function attachWebPresenterRef<T extends { __webPresenterRef: number }>(value: T, refs: WebPresenterRefs): T & WebPresenterRef {
\tObject.defineProperty(value, '__webPresenterRefs', {
\t\tvalue: refs,
\t\tenumerable: false,
\t\tconfigurable: true
\t});
\treturn value as T & WebPresenterRef;
}

function webPresenterRefValue(value: WebPresenterRef): unknown {
\tconst ref = (value.__webPresenterRefs as unknown as unknown[])[value.__webPresenterRef];
\tif (ref === undefined) {
\t\tthrow new Error('Web presenter reference is not attached.');
\t}
\treturn ref;
}

function pushWebPresenterRef(refs: unknown[], value: WebPresenterRef): number {
\tconst ref = webPresenterRefValue(value);
\trefs.push(ref);
\treturn refs.length - 1;
}
`;
}

function renderRefType(refType) {
	return refCodecFor(refType).renderType(refType);
}

function renderPresenterRefType(refType) {
	return renderObjectRefType({
		...refType,
		properties: [],
		methods: []
	});
}

function renderObjectRefType(refType) {
	return `${renderRefSnapshotType(refType)}

export type ${refType.name} = WebPresenterRef & {
${refType.properties.map((property) => `\t${property.name}: ${publicTsType(property)};`).join('\n')}
${(refType.methods ?? []).map((method) => `\t${method.name}(${renderActionParameters(method)}): ${returnTsType(method.return)};`).join('\n')}
};

${renderAttachRefFunction(refType)}`;
}

function renderRefSnapshotType(refType) {
	return `type ${refType.name}Snapshot = {
\t__webPresenterRef: number;
${refType.properties.map((property) => `\t${property.name}: ${snapshotTsType(property)};`).join('\n')}
};`;
}

function renderAttachRefFunction(refType) {
	const attachName = attachFunctionName(refType.name);
	const callParameter = refNeedsCall(refType, [...refTypesByName.values()]) ? ', call: WebPresenterCall' : '';
	const propertyAssignments = (refType.properties ?? [])
		.map((property) => `\tref.${property.name} = ${toPublicValueExpression(property, `value.${property.name}`, 'refs', 'call', [...refTypesByName.values()])};`)
		.join('\n');
	const methodAssignments = (refType.methods ?? []).map((method) => renderRefMethodAssignment(method)).join('\n');
	return `function ${attachName}(value: ${refType.name}Snapshot, refs: WebPresenterRefs${callParameter}): ${refType.name} {
\tconst ref = attachWebPresenterRef(value, refs) as unknown as ${refType.name};
${propertyAssignments}
${methodAssignments}
\treturn ref;
}`;
}

function renderRefMethodAssignment(method) {
	const methodArgs = renderActionParameters(method);
	const refsSetup = `const refs: unknown[] = [];`;
	const callArgs = renderMethodArgsObject(method);
	if (!hasReturn(method)) {
		return `\tref.${method.name} = (${methodArgs}) => {
\t\t${refsSetup}
\t\tcall('${method.path}', ${callArgs}, refs);
\t};`;
	}
	return `\tref.${method.name} = (${methodArgs}) => {
\t\t${refsSetup}
\t\tconst result = call('${method.path}', ${callArgs}, refs);
\t\treturn ${decodeReturnExpression('result', method.return)};
\t};`;
}

function renderActionType(action) {
	return `\t${action.name}(${renderActionParameters(action)}): ${returnTsType(action.return)};`;
}

function renderParameters(parameters) {
	return parameters
		.map((parameter) => {
			if (parameter.kind === 'callback') {
				return `${parameter.name}: ${callbackTsType(parameter)}`;
			}
			return `${parameter.name}: ${publicTsType(parameter)}`;
		})
		.join(', ');
}

function renderActionInitializer(action) {
	if (!hasReturn(action)) {
		if (!action.args.some((arg) => valueContainsRef(arg))) {
			return `\t\t${action.name}: (${renderActionParameters(action)}) => dispatch('${action.name}', ${renderActionArgsObject(action)}),`;
		}
		return `\t\t${action.name}: (${renderActionParameters(action)}) => {
\t\t\tif (closed || presenterId === null) return;
\t\t\tconst refs: unknown[] = [];
\t\t\tdispatch('${action.name}', ${renderActionArgsObject(action)}, refs);
\t\t},`;
	}

	return `\t\t${action.name}: (${renderActionParameters(action)}) => {
\t\t\tconst refs: unknown[] = [];
\t\t\tconst result = call('${action.name}', ${renderActionArgsObject(action)}, refs);
\t\t\treturn ${decodeReturnExpression('result', action.return)};
\t\t},`;
}

function renderCreateArgsJson(parameters) {
	if (parameters.length === 0) return "'{}'";
	return `JSON.stringify({ ${parameters.map((parameter) => parameter.name).join(', ')} })`;
}

function renderCallbacksArray(callbackParameters) {
	return `[${callbackParameters.map((parameter) => renderCallbackValue(parameter)).join(', ')}] as unknown as WebPresenterCallbacks`;
}

function renderCallbackValue(parameter) {
	const args = parameter.args ?? [];
	if (!args.some((arg) => isSealed(arg))) return parameter.name;
	const parameters = args.map((arg) => `${arg.name}: ${callbackNativeTsType(arg)}`).join(', ');
	const invocationArgs = args.map((arg) => renderCallbackInvocationArg(arg)).join(', ');
	const callback = `(${parameters}) => ${parameter.name}(${invocationArgs})`;
	if (isNullable(parameter)) {
		return `${parameter.name} === null ? null : (${callback})`;
	}
	return callback;
}

function callbackNativeTsType(arg) {
	if (isSealed(arg)) return isNullable(arg) ? 'string | null' : 'string';
	return valueTsType(arg);
}

function renderCallbackInvocationArg(arg) {
	if (!isSealed(arg)) return arg.name;
	if (isNullable(arg)) {
		return `(${arg.name} === null ? null : JSON.parse(${arg.name}) as ${arg.tsType})`;
	}
	return `JSON.parse(${arg.name}) as ${arg.tsType}`;
}

function renderCallbackParameters(parameter) {
	return parameter.args.map((arg) => `${arg.name}: ${publicTsType(arg)}`).join(', ');
}

function callbackTsType(parameter) {
	const type = `(${renderCallbackParameters(parameter)}) => void`;
	return isNullable(parameter) ? `(${type}) | null` : type;
}

function renderActionParameters(action) {
	return action.args.map((arg) => `${arg.name}: ${publicTsType(arg)}`).join(', ');
}

function renderActionArgsObject(action) {
	if (action.args.length === 0) return '{}';
	return `{ ${action.args.map((arg) => renderActionArgument(arg)).join(', ')} }`;
}

function renderMethodArgsObject(method) {
	return `{ __receiver: pushWebPresenterRef(refs, ref)${method.args.length === 0 ? '' : `, ${method.args.map((arg) => renderActionArgument(arg)).join(', ')}`} }`;
}

function renderActionArgument(arg) {
	return `${arg.name}: ${toBridgeValueExpression(arg, arg.name, 'refs', [...refTypesByName.values()])}`;
}

function renderSnapshotAssignment(property, refTypes) {
	const stateCodec = stateCodecFor(property);
	const options =
		stateCodec?.kind === 'pagingState'
			? {
					peekPath: pagingPeekPath(property.name),
					getStatement: `dispatch(${JSON.stringify(pagingGetPath(property.name))}, { index })`
				}
			: {};
	return `\t\t\tstate.${property.name} = ${toPublicValueExpression(property, `snapshot.${property.name}`, 'refs', 'call', refTypes, options)};`;
}

function renderCallFunction(needsCall) {
	if (!needsCall) return '';
	return `
\tfunction call(path: string, args: Record<string, unknown> = {}, refs: unknown[] = []): WebPresenterCallResult {
\t\tif (closed || presenterId === null) {
\t\t\tthrow new Error('Web presenter is not mounted.');
\t\t}

\t\tlet result: WebPresenterCallResult | null = null;
\t\twebPresenterCall(
\t\t\tpresenterId,
\t\t\tJSON.stringify({ path, args }),
\t\t\trefs as unknown as WebPresenterRefs,
\t\t\t(json, refs) => {
\t\t\t\tresult = { json, refs };
\t\t\t}
\t\t);
\t\tif (result === null) {
\t\t\tthrow new Error('Web presenter call did not return a result.');
\t\t}
\t\treturn result;
\t}
`;
}

function initialValue(property, refTypes) {
	if (isNullable(property)) return 'null';
	if (isEnum(property)) return JSON.stringify((property.values ?? [''])[0]);
	if (isSealed(property)) return initialSealedValue(property, refTypes);
	if (isArray(property)) return '[]';
	const stateCodec = stateCodecFor(property);
	if (stateCodec !== undefined) return stateCodec.initialValue(property);
	if (isRef(property)) {
		const fields = (property.properties ?? []).map((field) => `${field.name}: ${initialValue(field, refTypes)}`);
		return attachRefExpression(
			property,
			`{ __webPresenterRef: -1${fields.length === 0 ? '' : `, ${fields.join(', ')}`} }`,
			'[] as unknown as WebPresenterRefs',
			refTypes
		);
	}
	switch (property.tsType) {
		case 'boolean':
			return 'false';
		case 'number':
			return '0';
		case 'string':
			return "''";
		default:
			return 'null';
	}
}

function initialSealedValue(property, refTypes) {
	const variant = (property.variants ?? [])[0];
	if (variant === undefined) return 'null';
	const discriminator = property.discriminator ?? 'type';
	const fields = [
		`${JSON.stringify(discriminator)}: ${JSON.stringify(variant.tag ?? variant.name)}`,
		...(variant.properties ?? []).map((field) => `${JSON.stringify(field.name)}: ${initialValue(field, refTypes)}`)
	];
	return `{ ${fields.join(', ')} }`;
}

function decodeReturnExpression(resultExpression, returnValue) {
	if (!hasReturn({ return: returnValue })) return 'undefined';
	const valueExpression = `(JSON.parse(${resultExpression}.json) as { value: ${snapshotTsType(returnValue)} }).value`;
	return toPublicValueExpression(returnValue, valueExpression, `${resultExpression}.refs`, 'call', [...refTypesByName.values()]);
}

function attachRefExpression(ref, valueExpression, refsExpression, refTypes, callExpression = 'call') {
	const refType = refTypes.find((candidate) => candidate.name === ref.tsType || candidate.tsType === ref.tsType) ?? ref;
	const callArgument = refNeedsCall(refType, refTypes) ? `, ${callExpression}` : '';
	return `${attachFunctionName(ref.tsType)}(${valueExpression} as ${ref.tsType}Snapshot, ${refsExpression}${callArgument})`;
}

function attachFunctionName(typeName) {
	return `attach${typeName}`;
}

function snapshotTsType(item) {
	const stateCodec = stateCodecFor(item);
	const baseType =
		isRef(item)
			? `${item.tsType}Snapshot`
			: isArray(item)
				? `Array<${snapshotTsType(item.item)}>`
				: stateCodec !== undefined
					? stateCodec.snapshotType(item)
					: isSealed(item)
						? sealedSnapshotTypeName(item.tsType)
						: item.tsType;
	return nullableTsType(baseType, item);
}

function returnTsType(returnValue) {
	if (!hasReturn({ return: returnValue })) return 'void';
	return publicTsType(returnValue);
}

function publicTsType(item) {
	const stateCodec = stateCodecFor(item);
	const baseType =
		isArray(item)
			? `Array<${publicTsType(item.item)}>`
			: stateCodec !== undefined
				? stateCodec.publicType(item)
				: item.tsType;
	return nullableTsType(baseType, item);
}

function valueTsType(item) {
	return nullableTsType(item.tsType, item);
}

function nullableTsType(baseType, item) {
	return isNullable(item) ? `${baseType} | null` : baseType;
}

function toPublicValueExpression(item, valueExpression, refsExpression, callExpression, refTypes, options = {}) {
	if (!valueNeedsPublicConversion(item, refTypes)) return valueExpression;
	if (isNullable(item)) {
		const nonNullItem = withoutNullable(item);
		return `((value: ${snapshotTsType(item)}) => value === null ? null : ${toPublicNonNullValueExpression(nonNullItem, 'value', refsExpression, callExpression, refTypes, options)})(${valueExpression})`;
	}
	return toPublicNonNullValueExpression(item, valueExpression, refsExpression, callExpression, refTypes, options);
}

function toPublicNonNullValueExpression(item, valueExpression, refsExpression, callExpression, refTypes, options = {}) {
	if (isRef(item)) return attachRefExpression(item, valueExpression, refsExpression, refTypes, callExpression);
	if (isArray(item)) {
		return `${valueExpression}.map((item) => ${toPublicValueExpression(item.item, 'item', refsExpression, callExpression, refTypes)})`;
	}
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) {
		return stateCodec.toPublicExpression(item, valueExpression, refsExpression, callExpression, refTypes, options);
	}
	if (isSealed(item)) {
		const sealedType = findSealedType(item);
		const callArgument = sealedNeedsCall(sealedType, refTypes) ? `, ${callExpression}` : '';
		return `${toPublicSealedFunctionName(item.tsType)}(${valueExpression}, ${refsExpression}${callArgument})`;
	}
	return valueExpression;
}

function toBridgeValueExpression(item, valueExpression, refsExpression, refTypes, visitedRefs = new Set()) {
	if (!valueContainsRef(item)) return valueExpression;
	if (isNullable(item)) {
		const nonNullItem = withoutNullable(item);
		return `((value: ${publicTsType(item)}) => value === null ? null : ${toBridgeNonNullValueExpression(nonNullItem, 'value', refsExpression, refTypes, visitedRefs)})(${valueExpression})`;
	}
	return toBridgeNonNullValueExpression(item, valueExpression, refsExpression, refTypes, visitedRefs);
}

function toBridgeNonNullValueExpression(item, valueExpression, refsExpression, refTypes, visitedRefs = new Set()) {
	if (isRef(item)) return toBridgeRefExpression(item, valueExpression, refsExpression, refTypes, visitedRefs);
	if (isArray(item)) {
		return `${valueExpression}.map((item) => (${toBridgeValueExpression(item.item, 'item', refsExpression, refTypes, visitedRefs)}))`;
	}
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) {
		return stateCodec.toBridgeExpression(item, valueExpression, refsExpression, refTypes, visitedRefs);
	}
	if (isSealed(item)) return `${toBridgeSealedFunctionName(item.tsType)}(${valueExpression}, ${refsExpression})`;
	return valueExpression;
}

function toBridgeRefExpression(ref, valueExpression, refsExpression, refTypes, visitedRefs = new Set()) {
	const refType = refTypes.find((candidate) => candidate.name === ref.tsType || candidate.tsType === ref.tsType) ?? ref;
	const base = `{ ...${valueExpression}, __webPresenterRef: pushWebPresenterRef(${refsExpression}, ${valueExpression})`;
	if (visitedRefs.has(refType.name)) {
		return `${base} }`;
	}
	const nextVisitedRefs = new Set(visitedRefs);
	nextVisitedRefs.add(refType.name);
	const fields = (refType.properties ?? [])
		.map((property) => `${property.name}: ${toBridgeValueExpression(property, `${valueExpression}.${property.name}`, refsExpression, refTypes, nextVisitedRefs)}`)
		.join(', ');
	return `${base}${fields.length === 0 ? '' : `, ${fields}`} }`;
}

function withoutNullable(item) {
	return { ...item, nullable: false };
}

function findSealedType(item) {
	return {
		name: item.tsType,
		discriminator: item.discriminator ?? 'type',
		variants: item.variants ?? []
	};
}

function valueContainsRef(item, visitedRefs = new Set()) {
	if (item === undefined || item === null) return false;
	if (isRef(item)) return true;
	if (isArray(item)) return valueContainsRef(item.item, visitedRefs);
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) return valueContainsRef(stateCodec.content(item), visitedRefs);
	if (isSealed(item) || Array.isArray(item.variants)) {
		return (item.variants ?? []).some((variant) =>
			(variant.properties ?? []).some((property) => valueContainsRef(property, visitedRefs))
		);
	}
	if ((item.properties ?? []).length > 0) {
		if (visitedRefs.has(item.tsType)) return false;
		visitedRefs.add(item.tsType);
		return (item.properties ?? []).some((property) => valueContainsRef(property, visitedRefs));
	}
	return false;
}

function valueNeedsPublicConversion(item, refTypes, visitedRefs = new Set()) {
	if (item === undefined || item === null) return false;
	if (isNullable(item)) return valueNeedsPublicConversion(withoutNullable(item), refTypes, visitedRefs);
	if (isRef(item)) return true;
	if (isArray(item)) return valueNeedsPublicConversion(item.item, refTypes, visitedRefs);
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) {
		if (stateCodec.needsPublicConversion?.(item) === true) return true;
		return valueNeedsPublicConversion(stateCodec.content(item), refTypes, visitedRefs);
	}
	if (isSealed(item) || Array.isArray(item.variants)) {
		if ((item.variants ?? []).length === 0) return true;
		return (item.variants ?? []).some((variant) =>
			(variant.properties ?? []).some((property) => valueNeedsPublicConversion(property, refTypes, visitedRefs))
		);
	}
	if ((item.properties ?? []).length > 0) {
		if (visitedRefs.has(item.tsType)) return false;
		visitedRefs.add(item.tsType);
		return (item.properties ?? []).some((property) => valueNeedsPublicConversion(property, refTypes, visitedRefs));
	}
	return false;
}

function valueNeedsCall(item, refTypes, visitedRefs = new Set()) {
	if (item === undefined || item === null) return false;
	if (isRef(item)) {
		const refType = refTypes.find((candidate) => candidate.name === item.tsType || candidate.tsType === item.tsType) ?? item;
		return refNeedsCall(refType, refTypes, visitedRefs);
	}
	if (isArray(item)) return valueNeedsCall(item.item, refTypes, visitedRefs);
	const stateCodec = stateCodecFor(item);
	if (stateCodec !== undefined) return stateCodec.needsCall === true || valueNeedsCall(stateCodec.content(item), refTypes, visitedRefs);
	if (isSealed(item) || Array.isArray(item.variants)) return sealedNeedsCall(findSealedType(item), refTypes, visitedRefs);
	return false;
}

function refNeedsCall(refType, refTypes, visitedRefs = new Set()) {
	if (refType === undefined || refType === null) return false;
	return refCodecFor(refType).needsCall(refType, refTypes, visitedRefs);
}

function objectRefNeedsCall(refType, refTypes, visitedRefs = new Set()) {
	if ((refType.methods ?? []).length > 0) return true;
	if (visitedRefs.has(refType.name)) return false;
	visitedRefs.add(refType.name);
	return (refType.properties ?? []).some((property) => valueNeedsCall(property, refTypes, visitedRefs));
}

function sealedNeedsCall(sealedType, refTypes, visitedRefs = new Set()) {
	if (sealedType === undefined || sealedType === null) return false;
	return (sealedType.variants ?? []).some((variant) =>
		(variant.properties ?? []).some((property) => valueNeedsCall(property, refTypes, visitedRefs))
	);
}

function sealedSnapshotTypeName(typeName) {
	return `${typeName}Snapshot`;
}

function toPublicSealedFunctionName(typeName) {
	return `toPublic${typeName}`;
}

function toBridgeSealedFunctionName(typeName) {
	return `toBridge${typeName}`;
}

function isValue(item) {
	return (
		item?.kind === undefined ||
		item.kind === 'value' ||
		isEnum(item) ||
		isSealed(item) ||
		isArray(item) ||
		stateCodecFor(item) !== undefined
	);
}

function isRef(item) {
	return item?.kind === 'ref';
}

function isEnum(item) {
	return item?.kind === 'enum';
}

function isSealed(item) {
	return item?.kind === 'sealed';
}

function isArray(item) {
	return item?.kind === 'array';
}

function stateCodecFor(item) {
	return stateCodecs.find((codec) => codec.is(item));
}

function refCodecFor(refType) {
	return refCodecs.find((codec) => codec.is(refType));
}

function isNullable(item) {
	return item?.nullable === true;
}

function hasReturn(action) {
	return action.return !== undefined && action.return.kind !== 'void';
}

function hasMethods(refType) {
	return (refType.methods ?? []).length > 0;
}

function capitalize(value) {
	return `${value.slice(0, 1).toUpperCase()}${value.slice(1)}`;
}

function pagingGetPath(propertyName) {
	return `__webPagingGet:${propertyName}`;
}

function pagingPeekPath(propertyName) {
	return `__webPagingPeek:${propertyName}`;
}
