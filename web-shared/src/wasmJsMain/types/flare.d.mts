export type WebPresenterRefs = unknown[];
export type WebPresenterCallbacks = unknown[];

export function webPresenterCreate(
	name: string,
	argsJson: string,
	callbacks: WebPresenterCallbacks
): number;
export function webPresenterBindRef(name: string, presenter: unknown): number;
export function webPresenterSubscribe(
	id: number,
	listener: (snapshotJson: string, refs: WebPresenterRefs) => void
): number;
export function webPresenterDispatch(id: number, actionJson: string, refs: WebPresenterRefs): void;
export function webPresenterCall(
	id: number,
	actionJson: string,
	refs: WebPresenterRefs,
	listener: (json: string, refs: WebPresenterRefs) => void
): void;
export function webPresenterUnsubscribe(id: number, subscriptionId: number): void;
export function webPresenterClose(id: number): void;
export function webSharedInitialize(): void;
export function webSharedInstallFormatter(
	formatNumber: (value: number) => string,
	formatRelativeInstant: (epochMillis: number) => string,
	formatFullInstant: (epochMillis: number) => string,
	formatAbsoluteInstant: (epochMillis: number) => string
): void;

export const memory: WebAssembly.Memory;
