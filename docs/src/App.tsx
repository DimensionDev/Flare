import type { Component } from 'solid-js';

import logo from './assets/logo.svg';
import pixel from './assets/pixel.png';
import iphone from './assets/iphone.png';
import ipad from './assets/ipad.png';
import tablet from './assets/tablet.png';
import macos from './assets/macos.png';
import en_badge_web_generic from './assets/en_badge_web_generic.png';
import styles from './App.module.css';

const App: Component = () => {
  return (
    <div class={styles.App}>
      <div class={styles.nav}>
        <div>
          <img src={logo} class={styles.AppLogo} alt="logo" />
          <p>Flare</p>
        </div>
        <a href='https://github.com/DimensionDev/Flare'>
          <img src='https://upload.wikimedia.org/wikipedia/commons/9/91/Octicons-mark-github.svg' />
        </a>
      </div>

      <div class={styles.header}>
        <div class={styles.headerInfo}>
          <div class={styles.headerTitle}>
            <p>The ultimate next-gen* <br/> Open-sourced <br/> AI powered** <br/> Decentralized <br/> Social network client <br/> For Android, iOS, macOS.</p>
            <p class={styles.caption}>* Just a joke</p>
            <p class={styles.caption}>** Not yet implemented.</p>
          </div>
          <div class={styles.badgeContainer}>
            <a class={styles.badge} href='https://testflight.apple.com/join/iYP7QZME'>
              <img src='https://developer.apple.com/app-store/marketing/guidelines/images/badge-example-preferred_2x.png' />
            </a>
            <a class={styles.badge} href='https://play.google.com/store/apps/details?id=dev.dimension.flare'>
              <img src={en_badge_web_generic} />
            </a>

          </div>
        </div>
        <div class={styles.headerScreenshot}>
          <img src={pixel} />
          <img src={iphone} />
        </div>
      </div>

      <div class={styles.divider} />

      <div class={styles.supportedContainer}>
        <h1>All your social platform in one client</h1>
        <p>Flare support Mastodon, Misskey, Bluesky and xQt, and more social platform in the future.</p>
        <div class={styles.supportedImgContainer}>
          <div>
            <img src="https://joinmastodon.org/logos/logo-purple.svg" />
            <p>Mastodon</p>
          </div>
          <div>
            <img src="https://github.com/misskey-dev/misskey/blob/develop/packages/backend/assets/favicon.png?raw=true" />
            <p>Misskey</p>
          </div>
          <div>
            <img src="https://blueskyweb.xyz/images/apple-touch-icon.png" />
            <p>Bluesky</p>
          </div>
        </div>
      </div>

      <div class={styles.tabletContainer}>
        <h1>Out of the box tablet mode</h1>
        <p>Flare support tablet mode out of the box, you will have a better experience on tablet.</p>
        <div class={styles.tabletImgContainer}>
          <img src={tablet} />
          <img src={ipad} />
        </div>
      </div>

      <div class={styles.desktopContainer}>
        <h1>No more Chromium in your Mac</h1>
        <p>Flare for macOS is a native app, it's not running on electron, you can enjoy the native experience.</p>
        <img src={macos} />
      </div>

      <div class={styles.fossContainer}>
        <h1>Free and open source</h1>
        <p>Flare is free and open source, you can check out the source code on GitHub, and you can also contribute to the project, let's make Fediverse great again!</p>
      </div>

      <div class={styles.footer}>
        <p>© 2024 DimensionDev</p>
      </div>

    </div>
  );
};

export default App;
