import { browser } from '$app/environment';
import { getApp, getApps, initializeApp, type FirebaseApp } from 'firebase/app';

const firebaseConfig = {
	apiKey: 'AIzaSyBylOdoRre0TJ100JUR8pI3qTssxlrH60w',
	authDomain: 'flare-88d0a.firebaseapp.com',
	projectId: 'flare-88d0a',
	storageBucket: 'flare-88d0a.firebasestorage.app',
	messagingSenderId: '894536290792',
	appId: '1:894536290792:web:2cc41a8ed22ecf2fad145d',
	measurementId: 'G-PTK5G7N95B'
};

export const firebaseApp: FirebaseApp = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig);

export async function initializeFirebaseAnalytics(): Promise<void> {
	if (!browser || !firebaseConfig.measurementId) return;

	const { getAnalytics, isSupported } = await import('firebase/analytics');
	if (await isSupported()) {
		getAnalytics(firebaseApp);
	}
}
