import dayjs from 'dayjs';
import localizedFormat from 'dayjs/plugin/localizedFormat';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/ja';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/zh-tw';
import { webSharedInstallFormatter } from '@flare/web-shared';

let installed = false;

dayjs.extend(relativeTime);
dayjs.extend(localizedFormat);

export function installWebFormatter(): void {
	if (installed) return;
	installed = true;

	dayjs.locale(resolveDayjsLocale(globalThis.navigator?.language));

	webSharedInstallFormatter(
		formatNumber,
		formatRelativeInstant,
		formatFullInstant,
		formatAbsoluteInstant
	);
}

function formatNumber(value: number): string {
	return new Intl.NumberFormat(undefined, {
		notation: 'compact',
		maximumFractionDigits: 2,
		minimumFractionDigits: 0,
		useGrouping: false
	}).format(value);
}

function formatRelativeInstant(epochMillis: number): string {
	const instant = dayjs(epochMillis);
	const daysDiff = dayjs().startOf('day').diff(instant.startOf('day'), 'day');
	return daysDiff >= 7 ? instant.format('ll') : instant.fromNow();
}

function formatFullInstant(epochMillis: number): string {
	return dayjs(epochMillis).format('lll');
}

function formatAbsoluteInstant(epochMillis: number): string {
	const instant = dayjs(epochMillis);
	const daysDiff = dayjs().startOf('day').diff(instant.startOf('day'), 'day');
	if (daysDiff === 0) return instant.format('LT');
	if (daysDiff < 7) return instant.format('ddd LT');
	return instant.format('L LT');
}

function resolveDayjsLocale(locale: string | undefined): string {
	const normalized = locale?.toLowerCase() ?? '';
	if (normalized.startsWith('ja')) return 'ja';
	if (normalized.includes('hant') || normalized === 'zh-tw' || normalized === 'zh-hk') return 'zh-tw';
	if (normalized.startsWith('zh')) return 'zh-cn';
	return 'en';
}
