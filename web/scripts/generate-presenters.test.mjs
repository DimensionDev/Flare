import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const execFileAsync = promisify(execFile);
const generatorPath = fileURLToPath(new URL('./generate-presenters.mjs', import.meta.url));

test('generates presenter facade with value and callback parameters', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'counter',
						factory: 'createCounterPresenter',
						stateType: 'CounterState',
						parameters: [
							{ name: 'initialValue', kind: 'value', tsType: 'number' },
							{
								name: 'alert',
								kind: 'callback',
								args: [{ name: 'value', tsType: 'number' }]
							}
						],
						properties: [
							{ name: 'count', kind: 'value', tsType: 'number' },
							{
								name: 'sample',
								kind: 'ref',
								tsType: 'Sample',
								properties: [
									{ name: 'text', tsType: 'string' },
									{ name: 'index', tsType: 'number' }
								],
								methods: [
									{
										name: 'export',
										path: 'dev.dimension.flare.web.shared.Sample.export',
										args: [],
										return: { kind: 'ref', tsType: 'Sample' }
									}
								]
							}
						],
						actions: [
							{ name: 'increment', args: [] },
							{ name: 'setLabel', args: [{ name: 'label', kind: 'value', tsType: 'string' }] },
							{ name: 'showSample', args: [{ name: 'sample', kind: 'ref', tsType: 'Sample' }] },
							{ name: 'exportSample', args: [], return: { kind: 'ref', tsType: 'Sample' } }
						]
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'counter.svelte.ts'), 'utf8');
		const index = await fs.readFile(path.join(outDir, 'index.ts'), 'utf8');

		assert.match(
			generated,
			/export function createCounterPresenter\(initialValue: number, alert: \(value: number\) => void\): CounterState/
		);
		assert.match(
			generated,
			/export function createCounterPresenterController\(initialValue: number, alert: \(value: number\) => void\): WebPresenterController<CounterState>/
		);
		assert.match(generated, /export type WebPresenterController<TState> = \{/);
		assert.match(generated, /const controller = createCounterPresenterController\(initialValue, alert\)/);
		assert.match(
			generated,
			/webPresenterCreate\('counter', JSON\.stringify\(\{ initialValue \}\), \[alert\] as unknown as WebPresenterCallbacks\)/
		);
		assert.match(generated, /webPresenterCall/);
		assert.match(generated, /type WebPresenterCallbacks = Parameters<typeof webPresenterCreate>\[2\]/);
		assert.doesNotMatch(generated, /WebPresenterCallbackEvent/);
		assert.doesNotMatch(generated, /eventJson/);
		assert.match(generated, /export type Sample = WebPresenterRef & \{/);
		assert.match(generated, /export\(\): Sample;/);
		assert.match(generated, /type SampleSnapshot = \{/);
		assert.match(generated, /function attachSample\(value: SampleSnapshot, refs: WebPresenterRefs, call: WebPresenterCall\): Sample/);
		assert.match(generated, /sample: Sample;/);
		assert.match(generated, /webPresenterSubscribe\(presenterId, \(snapshotJson, refs\) =>/);
		assert.match(generated, /type WebPresenterRefs = Parameters<typeof webPresenterDispatch>\[2\]/);
		assert.match(generated, /state\.sample = attachSample\(snapshot\.sample as SampleSnapshot, refs, call\)/);
		assert.match(generated, /setLabel\(label: string\): void/);
		assert.match(generated, /setLabel: \(label: string\) => dispatch\('setLabel', \{ label: label \}\)/);
		assert.match(generated, /showSample\(sample: Sample\): void/);
		assert.match(generated, /showSample: \(sample: Sample\) => \{/);
		assert.match(generated, /exportSample\(\): Sample/);
		assert.match(generated, /exportSample: \(\) => \{/);
		assert.match(generated, /const result = call\('exportSample', \{\}, refs\)/);
		assert.match(generated, /call\('dev\.dimension\.flare\.web\.shared\.Sample\.export'/);
		assert.match(generated, /sample: \{ \.\.\.sample, __webPresenterRef: pushWebPresenterRef\(refs, sample\)/);
		assert.match(
			generated,
			/webPresenterDispatch\(presenterId, JSON\.stringify\(\{ path, args \}\), refs as unknown as WebPresenterRefs\)/
		);
		assert.doesNotMatch(generated, /close\(\): void/);
		assert.equal(index, "export * from './counter.svelte';\n");
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates dispatch refs type without complex references', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'counter',
						factory: 'createCounterPresenter',
						stateType: 'CounterState',
						parameters: [],
						properties: [{ name: 'count', kind: 'value', tsType: 'number' }],
						actions: [{ name: 'increment', args: [] }]
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'counter.svelte.ts'), 'utf8');

		assert.match(generated, /type WebPresenterRefs = Parameters<typeof webPresenterDispatch>\[2\]/);
		assert.match(generated, /type WebPresenterCallbacks = Parameters<typeof webPresenterCreate>\[2\]/);
		assert.match(generated, /webPresenterCreate\('counter', '\{\}', \[\] as unknown as WebPresenterCallbacks\)/);
		assert.match(
			generated,
			/webPresenterDispatch\(presenterId, JSON\.stringify\(\{ path, args \}\), refs as unknown as WebPresenterRefs\)/
		);
		assert.doesNotMatch(generated, /export type WebPresenterRef/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates arbitrary reference facade properties and methods', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'profile',
						factory: 'createProfilePresenter',
						stateType: 'ProfileState',
						parameters: [],
						properties: [
							{
								name: 'profile',
								kind: 'ref',
								tsType: 'ExternalProfile',
								properties: [
									{ name: 'avatar', tsType: 'string', nullable: true },
									{ name: 'name', tsType: 'string' }
								],
								methods: [
									{
										name: 'displayName',
										path: 'com.example.ExternalProfile.displayName',
										args: [],
										return: { kind: 'value', tsType: 'string' }
									}
								]
							}
						],
						actions: []
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'profile.svelte.ts'), 'utf8');

		assert.match(generated, /export type ExternalProfile = WebPresenterRef & \{/);
		assert.match(generated, /avatar: string \| null;/);
		assert.match(generated, /name: string;/);
		assert.match(generated, /displayName\(\): string;/);
		assert.match(
			generated,
			/function attachExternalProfile\(value: ExternalProfileSnapshot, refs: WebPresenterRefs, call: WebPresenterCall\): ExternalProfile/
		);
		assert.match(generated, /ref\.displayName = \(\) => \{/);
		assert.match(
			generated,
			/const result = call\('com\.example\.ExternalProfile\.displayName', \{ __receiver: pushWebPresenterRef\(refs, ref\) \}, refs\)/
		);
		assert.match(generated, /return \(JSON\.parse\(result\.json\) as \{ value: string \}\)\.value;/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates opaque reference return facades', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'homeTimelineWithTabs',
						factory: 'createHomeTimelineWithTabsPresenter',
						stateType: 'HomeTimelineWithTabsState',
						parameters: [],
						properties: [
							{
								name: 'tab',
								kind: 'ref',
								tsType: 'TimelineTabItemV2',
								properties: [],
								methods: [
									{
										name: 'createPresenter',
										path: 'dev.dimension.flare.data.model.tab.TimelineTabItemV2.createPresenter',
										args: [],
											return: {
												kind: 'ref',
												tsType: 'TimelinePresenter',
												codec: 'presenter',
												properties: [{ name: 'leaked', tsType: 'string' }],
												methods: [
													{
														name: 'getKoin',
														path: 'dev.dimension.flare.ui.presenter.home.TimelinePresenter.getKoin',
														args: [],
														return: { kind: 'value', tsType: 'string' }
													}
												]
											}
										}
									]
							}
						],
						actions: []
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'homeTimelineWithTabs.svelte.ts'), 'utf8');

		assert.match(generated, /export type TimelinePresenter = WebPresenterRef & \{/);
		assert.match(generated, /function attachTimelinePresenter\(value: TimelinePresenterSnapshot, refs: WebPresenterRefs\): TimelinePresenter/);
		assert.match(generated, /createPresenter\(\): TimelinePresenter;/);
		assert.doesNotMatch(generated, /leaked: string/);
		assert.doesNotMatch(generated, /getKoin\(\)/);
		assert.match(
			generated,
			/return attachTimelinePresenter\(\(JSON\.parse\(result\.json\) as \{ value: TimelinePresenterSnapshot \}\)\.value as TimelinePresenterSnapshot, result\.refs\);/
		);
		assert.doesNotMatch(generated, /attachTimelinePresenter\([^)]*, result\.refs, call\)/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates nullable presenter facade types and ref handling', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'counter',
						factory: 'createCounterPresenter',
						stateType: 'CounterState',
						parameters: [
							{ name: 'initialValue', kind: 'value', tsType: 'number', nullable: true },
							{
								name: 'alert',
								kind: 'callback',
								nullable: true,
								args: [{ name: 'value', tsType: 'number', nullable: true }]
							}
						],
						properties: [
							{ name: 'optionalCount', kind: 'value', tsType: 'number', nullable: true },
							{
								name: 'optionalSample',
								kind: 'ref',
								tsType: 'Sample',
								nullable: true,
								properties: [
									{ name: 'text', tsType: 'string', nullable: true },
									{ name: 'index', tsType: 'number' }
								],
								methods: [
									{
										name: 'export',
										path: 'dev.dimension.flare.web.shared.Sample.export',
										args: [],
										return: { kind: 'ref', tsType: 'Sample', nullable: true }
									}
								]
							}
						],
						actions: [
							{
								name: 'showOptionalSample',
								args: [{ name: 'sample', kind: 'ref', tsType: 'Sample', nullable: true }]
							},
							{
								name: 'exportOptionalSample',
								args: [],
								return: { kind: 'ref', tsType: 'Sample', nullable: true }
							}
						]
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'counter.svelte.ts'), 'utf8');

		assert.match(
			generated,
			/export function createCounterPresenter\(initialValue: number \| null, alert: \(\(value: number \| null\) => void\) \| null\): CounterState/
		);
		assert.match(generated, /optionalCount: number \| null;/);
		assert.match(generated, /optionalSample: Sample \| null;/);
		assert.match(generated, /text: string \| null;/);
		assert.match(generated, /optionalSample: null,/);
		assert.match(
			generated,
			/state\.optionalSample = \(\(value: SampleSnapshot \| null\) => value === null \? null : attachSample\(value as SampleSnapshot, refs, call\)\)\(snapshot\.optionalSample\)/
		);
		assert.match(generated, /showOptionalSample\(sample: Sample \| null\): void/);
		assert.match(generated, /sample: \(\(value: Sample \| null\) => value === null \? null : \{ \.\.\.value, __webPresenterRef: pushWebPresenterRef\(refs, value\)/);
		assert.match(generated, /exportOptionalSample\(\): Sample \| null/);
		assert.match(generated, /\{ value: SampleSnapshot \| null \}/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates enum presenter facade types', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');
		const counterMode = { kind: 'enum', tsType: 'CounterMode', values: ['Even', 'Odd'] };

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'counter',
						factory: 'createCounterPresenter',
						stateType: 'CounterState',
						parameters: [
							{ name: 'initialMode', ...counterMode },
							{
								name: 'modeAlert',
								kind: 'callback',
								args: [{ name: 'mode', ...counterMode, nullable: true }]
							}
						],
						properties: [
							{ name: 'mode', ...counterMode },
							{ name: 'optionalMode', ...counterMode, nullable: true },
							{
								name: 'sample',
								kind: 'ref',
								tsType: 'Sample',
								properties: [{ name: 'mode', ...counterMode, nullable: true }]
							}
						],
						actions: [
							{ name: 'setMode', args: [{ name: 'mode', ...counterMode, nullable: true }] },
							{ name: 'exportMode', args: [], return: counterMode }
						]
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'counter.svelte.ts'), 'utf8');

		assert.match(generated, /export type CounterMode = "Even" \| "Odd";/);
		assert.match(
			generated,
			/export function createCounterPresenter\(initialMode: CounterMode, modeAlert: \(mode: CounterMode \| null\) => void\): CounterState/
		);
		assert.match(generated, /mode: CounterMode;/);
		assert.match(generated, /optionalMode: CounterMode \| null;/);
		assert.match(generated, /mode: "Even",/);
		assert.match(generated, /optionalMode: null,/);
		assert.match(
			generated,
			/webPresenterCreate\('counter', JSON\.stringify\(\{ initialMode \}\), \[modeAlert\] as unknown as WebPresenterCallbacks\)/
		);
		assert.match(generated, /setMode\(mode: CounterMode \| null\): void/);
		assert.match(generated, /setMode: \(mode: CounterMode \| null\) => dispatch\('setMode', \{ mode: mode \}\)/);
		assert.match(generated, /exportMode\(\): CounterMode/);
		assert.match(generated, /\(JSON\.parse\(result\.json\) as \{ value: CounterMode \}\)\.value/);
		assert.match(generated, /sample: Sample;/);
		assert.match(generated, /type SampleSnapshot = \{\n\t__webPresenterRef: number;\n\tmode: CounterMode \| null;/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates sealed presenter facade types', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');
		const counterMode = { kind: 'enum', tsType: 'CounterMode', values: ['Even', 'Odd'] };
		const counterStatus = {
			kind: 'sealed',
			tsType: 'CounterStatus',
			discriminator: 'type',
			variants: [
				{ name: 'Stable', tag: 'Stable', properties: [] },
				{
					name: 'Threshold',
					tag: 'Threshold',
					properties: [
						{ name: 'count', kind: 'value', tsType: 'number' },
						{ name: 'label', kind: 'value', tsType: 'string', nullable: true },
						{ name: 'mode', ...counterMode }
					]
				}
			]
		};

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'counter',
						factory: 'createCounterPresenter',
						stateType: 'CounterState',
						parameters: [
							{
								name: 'statusAlert',
								kind: 'callback',
								args: [{ name: 'status', ...counterStatus, nullable: true }]
							}
						],
						properties: [
							{ name: 'status', ...counterStatus },
							{ name: 'optionalStatus', ...counterStatus, nullable: true }
						],
						actions: [
							{ name: 'showStatus', args: [{ name: 'status', ...counterStatus, nullable: true }] },
							{ name: 'exportStatus', args: [], return: counterStatus }
						]
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'counter.svelte.ts'), 'utf8');

		assert.match(generated, /export type CounterMode = "Even" \| "Odd";/);
		assert.match(generated, /export type CounterStatus =\n\t\| \{ "type": "Stable" \}\n\t\| \{ "type": "Threshold"; "count": number; "label": string \| null; "mode": CounterMode \};/);
		assert.match(
			generated,
			/export function createCounterPresenter\(statusAlert: \(status: CounterStatus \| null\) => void\): CounterState/
		);
		assert.match(
			generated,
			/webPresenterCreate\('counter', '\{\}', \[\(status: string \| null\) => statusAlert\(\(status === null \? null : JSON\.parse\(status\) as CounterStatus\)\)\] as unknown as WebPresenterCallbacks\)/
		);
		assert.match(generated, /status: CounterStatus;/);
		assert.match(generated, /optionalStatus: CounterStatus \| null;/);
		assert.match(generated, /status: \{ "type": "Stable" \},/);
		assert.match(generated, /showStatus\(status: CounterStatus \| null\): void/);
		assert.match(generated, /showStatus: \(status: CounterStatus \| null\) => dispatch\('showStatus', \{ status: status \}\)/);
		assert.match(generated, /exportStatus\(\): CounterStatus/);
		assert.match(generated, /\(JSON\.parse\(result\.json\) as \{ value: CounterStatusSnapshot \}\)\.value/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates ui state arrays with nested refs', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'homeTimeline',
						factory: 'createHomeTimelinePresenter',
						stateType: 'HomeTimelineState',
						parameters: [],
						properties: [
							{
								name: 'items',
								kind: 'uiState',
								data: {
									kind: 'array',
									item: {
										kind: 'ref',
										tsType: 'Sample',
										properties: [
											{ name: 'text', tsType: 'string' },
											{ name: 'index', tsType: 'number' }
										]
									}
								}
							}
						],
						actions: []
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'homeTimeline.svelte.ts'), 'utf8');

		assert.match(generated, /type UiStateSnapshot<T> =/);
		assert.match(generated, /export type UiState<T> =/);
		assert.match(generated, /items: UiStateSnapshot<Array<SampleSnapshot>>;/);
		assert.match(generated, /items: UiState<Array<Sample>>;/);
		assert.match(generated, /items: \{ type: "Loading" \},/);
		assert.match(
			generated,
			/state\.items = \(\(value: UiStateSnapshot<Array<SampleSnapshot>>\) => value\.type === "Success" \? \{ \.\.\.value, data: value\.data\.map\(\(item\) => attachSample\(item as SampleSnapshot, refs\)\) \} : value\)\(snapshot\.items\)/
		);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});

test('generates paging state get bridge for visible items', async () => {
	const tmp = await fs.mkdtemp(path.join(os.tmpdir(), 'flare-web-presenters-'));

	try {
		const manifestPath = path.join(tmp, 'manifest.json');
		const outDir = path.join(tmp, 'out');

		await fs.writeFile(
			manifestPath,
			JSON.stringify({
				presenters: [
					{
						name: 'timeline',
						factory: 'createTimelinePresenter',
						stateType: 'TimelineState',
						parameters: [],
						properties: [
							{
								name: 'listState',
								kind: 'pagingState',
								item: {
									kind: 'ref',
									tsType: 'Sample',
									properties: [{ name: 'text', tsType: 'string' }]
								}
							}
						],
						actions: []
					}
				]
			})
		);

		await execFileAsync(process.execPath, [generatorPath, manifestPath, outDir]);

		const generated = await fs.readFile(path.join(outDir, 'timeline.svelte.ts'), 'utf8');

		assert.match(generated, /type PagingStateSnapshot =/);
		assert.match(generated, /export type PagingLoadState =/);
		assert.match(
			generated,
			/export type PagingState<T> =\n\t\| \{ type: "Loading" \}\n\t\| \{ type: "Error"; message: string \| null \}\n\t\| \{ type: "Empty" \}\n\t\| \{ type: "Success"; itemCount: number; isRefreshing: boolean; appendState: PagingLoadState; peek\(index: number\): T \| null; get\(index: number\): void; retry\(\): void \};/
		);
		assert.match(generated, /call\("__webPagingPeek:listState", \{ index \}\)/);
		assert.match(generated, /dispatch\("__webPagingGet:listState", \{ index \}\)/);
		assert.match(generated, /dispatch\("__webPagingRetry:listState"\)/);
		assert.doesNotMatch(generated, /items: Array<T>/);
		assert.doesNotMatch(generated, /value\.items/);
	} finally {
		await fs.rm(tmp, { recursive: true, force: true });
	}
});
