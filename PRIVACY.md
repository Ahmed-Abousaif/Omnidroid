# Privacy Policy for Omnidroid

**Last updated:** November 24, 2026

Omnidroid (“the app”) is developed by Ahmed Abousaif. This policy explains what information the app uses and how it is handled.

## Summary

Omnidroid is built to run locally on your device. It does not require an account to play games, and it does not sell your personal data.

## Information the app uses

### On your device

- **Game library data:** ROM paths, titles, covers, play time, favorites, custom names, and similar library metadata are stored on your device.
- **Save states and save files:** Stored locally unless you enable cloud saves.
- **Settings and preferences:** Stored on your device.
- **Optional profile data:** Gamer tag, avatar, XP, and related profile details stay on your device unless cloud saves sync is enabled.

### Optional cloud saves

If you enable cloud saves (Play builds), the app may sync save files and optional profile data with the cloud provider you choose (for example Google Drive, Dropbox, or OneDrive). That sync uses your account with that provider and is subject to that provider’s privacy policy.

### Network use

The app may use the network to:

- download or update emulation cores
- fetch optional game metadata or trailers
- sync cloud saves when you enable that feature

### Artwork and game information

Installed game databases and libretro thumbnails are used first. When online artwork and game details are enabled, unresolved artwork and requested game details can be looked up through the Omnidroid metadata service and IGDB. The service receives a cleaned game title, platform, optional alternate title and region, and an opaque lookup key. ROM files, filesystem paths, save data, CRCs and serials are not uploaded by this feature.

Accepted game-to-provider associations, public game metadata and artwork references are retained in a shared catalog without an expiry. They are not associated with an Omnidroid account. Failed or ambiguous lookups are remembered temporarily to avoid repeated requests. The service uses a salted network-address hash for temporary rate limits and keeps aggregate request counters. Image downloads contact libretro or IGDB image servers directly.

You can disable online artwork and game details in Library settings, and restrict background artwork downloads to unmetered networks. Your manual cover selections and match corrections remain on your device; selecting a candidate does not publish a global correction. Additional game information and artwork are provided by [IGDB.com](https://www.igdb.com).

### Permissions

Permissions are requested only as needed for features such as storage access for your ROM folder, network access, and optional controller or casting features.

## What we do not do

- We do not sell your personal information.
- We do not use your game library for advertising.
- We do not require you to create an Omnidroid account to use the app.

## Children’s privacy

Omnidroid is a general-purpose multi-console emulation platform. Parents and guardians should supervise device and content use as appropriate.

## Changes

This policy may be updated from time to time. The “Last updated” date at the top will change when it does.

## Contact

Questions about this policy: **ahmedehab25299@gmail.com**

Project source: [github.com/Ahmed-Abousaif/Omnidroid](https://github.com/Ahmed-Abousaif/Omnidroid)
