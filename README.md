<div align="center">

# Verso

**A refined android music player combining YouTube Music streaming, fast offline downloads, word-by-word synced lyrics, Last.fm scrobbling, and a shareable Recap.**

[![Version](https://img.shields.io/badge/version-v1.0--beta1-FF6E4A?style=for-the-badge)](https://github.com/vimi-tt/verso/releases)
[![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-19183D?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)

[Portfolio](https://vimi-tt.github.io/) · [Releases](https://github.com/vimi-tt/verso/releases) · [Report a Bug](https://github.com/vimi-tt/verso/issues)

</div>

---

## Overview

**Verso** is an Android music player built on top of [Elovaire](https://github.com/droidbeauty/elovaire-music) by Droid Beauty, expanded with YouTube Music streaming, account synchronization, offline collection support, syllable-synced lyrics, listening statistics, and custom shareable cards.

## Features

### Streaming & Discovery
- **YouTube Music Integration**: Browse Home shelves, mood chips, artists, albums, and playlists, or stream in anonymous mode without signing in.
- **Account Sync & Profile**: Sign in with Google / YouTube Music to sync liked songs, saved albums, followed artists, and show your profile avatar in the top bar.
- **Activity Mix**: Personalized mix on Home built from your account shelves and recent listening activity.
- **YouTube Watch History Sync**: Optional setting to report playback to your YouTube Music account after 30 seconds so plays count toward your official YouTube Recap.

### Offline Downloads & Library
- **High-Speed Chunked Downloads**: Parallel track queue (`4` concurrent downloads) using HTTP `Range` chunking and buffered I/O to avoid single-stream throttling.
- **Offline Albums & Playlists**: Downloaded collections persist their metadata and tracklist locally so you can open and play them offline directly from **Library -> Downloads**.
- **Optional Local Audio**: Local storage permission is completely optional during onboarding; you can also play local audio, audiobooks, and SMB/WebDAV network folders when granted.
- **Storage & Quality Controls**: Configure Wi-Fi, mobile data, and download audio quality, Wi-Fi-only downloads, and clear music cache or downloads from Settings.

### Lyrics & Now Playing
- **Word-by-Word Synced Lyrics**: Smooth syllable/word karaoke highlighting powered by **BetterLyrics** (TTML) with **LRCLIB** fallback and configurable provider priority.
- **Refined Player & Navigation**: Dynamic artwork backdrop, floating minimal navigation capsule, skip-next mini-player control, sleep timer, equalizer, crossfade, and audio upscaler.
- **Shareable PNG Cards**: Create and download/share stylized **Song Cards** or **Lyrics Cards** as PNGs with Light/Dark modes and 5 custom themes directly from the player menu.

### Stats, Last.fm & Verso Recap
- **30-Second Real-Time Tracking**: Plays and real active listening duration are counted only after 30 seconds of playback.
- **Last.fm-Style Stats**: View ranked top tracks, artists, and albums with proportional play bars, filterable by **Month**, **Year**, or **All time**.
- **Last.fm Scrobbling & Genre Discovery**: Connect your Last.fm account with username and password for automatic scrobbling, plus multi-source genre resolution (Last.fm tags + iTunes Search fallback).
- **Verso Recap**: Story-style tabbed Recap playing your #1 most-listened song in the background, culminating in a downloadable & shareable PNG summary card.

---

## Tech Stack & Credits

Verso is built with gratitude on top of open-source foundations:

- **[Elovaire](https://github.com/droidbeauty/elovaire-music)** by [Droid Beauty](https://play.google.com/store/apps/dev?id=7791472155222642322) — Base player architecture and UI foundation
- **Jetpack Compose & Media3 (ExoPlayer)** — UI, media session, and audio playback (`Apache-2.0`)
- **[NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)** — YouTube stream resolution (`GPL-3.0`)
- **[BetterLyrics](https://better-lyrics.boidu.dev)** — Word-synced TTML lyrics (`GPL-3.0`)
- **[LRCLIB](https://lrclib.net)** — Time-synced lyrics fallback (`MIT`)
- **[OkHttp](https://square.github.io/okhttp)** — HTTP & range-chunked download transport (`Apache-2.0`)
- **[Last.fm API](https://www.last.fm/api)** — Scrobbling and tag discovery
- **[jaudiotagger](https://bitbucket.org/ijabz/jaudiotagger)** (`LGPL`), **[Lucide Icons](https://lucide.dev/)** (`ISC / MIT`), and **[Geist Font](https://github.com/vercel/geist-font)** (`OFL-1.1`)

---

## Building from Source

```bash
git clone https://github.com/vimi-tt/verso.git
cd verso
./gradlew :app:assembleDebug
```

To build the release APK:

```bash
./gradlew :app:assembleRelease
```

> **Requirements:** JDK 17+ and Android SDK (API 37 compileSdk / API 29 minSdk).
