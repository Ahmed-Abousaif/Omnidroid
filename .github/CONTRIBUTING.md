# Contributing to Omnidroid

Thank you for your interest in contributing to **Omnidroid**! 🎉

Omnidroid is an open-source Libretro-powered multi-console emulation platform for Android focused on delivering a landscape-first, controller-friendly gaming experience with high performance (Vulkan/GLES), clean Jetpack Compose UI, and rich emulation capabilities.

Whether you are fixing a bug, adding support for a new feature, improving documentation, or translating strings, your contributions are welcome. Please take a few moments to review these guidelines before getting started.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to Contribute](#ways-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Translations](#translations)
  - [Code Contributions](#code-contributions)
- [Development Setup](#development-setup)
  - [Prerequisites](#prerequisites)
  - [Cloning the Repository](#cloning-the-repository)
  - [Building the Project](#building-the-project)
- [Repository Structure](#repository-structure)
- [Development Guidelines](#development-guidelines)
  - [Branching Strategy](#branching-strategy)
  - [Coding Standards & Formatting](#coding-standards--formatting)
  - [Compose & Controller Navigation](#compose--controller-navigation)
  - [Commit Messages](#commit-messages)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Getting Help](#getting-help)

---

## Code of Conduct

We are committed to providing a welcoming, respectful, and collaborative environment for all contributors. Please:
- Treat fellow contributors and maintainers with kindness and respect.
- Provide constructive, actionable feedback in discussions and code reviews.
- Focus on what is best for the community and the end users.

---

## Ways to Contribute

### Reporting Bugs

If you find a bug:
1. **Search existing issues:** Check [GitHub Issues](https://github.com/Ahmed-Abousaif/Omnidroid/issues) to ensure the issue has not already been reported or resolved.
2. **Use the Bug Report Template:** Open a new issue using our [Bug Report Template](https://github.com/Ahmed-Abousaif/Omnidroid/issues/new?template=bug_report.yml).
3. **Include details:**
   - Android version and device model (e.g., Pixel 8, Odin 2, Retroid Pocket 4).
   - Omnidroid version or commit hash.
   - Emulation core and game title (if applicable).
   - Clear steps to reproduce the issue.
   - Relevant logcat logs or screenshots/screen recordings.

### Suggesting Features

Have an idea for an enhancement or new capability?
- Check existing [Feature Requests](https://github.com/Ahmed-Abousaif/Omnidroid/issues) or join the [Discussions](https://github.com/Ahmed-Abousaif/Omnidroid/discussions) to see if it's already being planned.
- Open a feature request using the [Feature Request Template](https://github.com/Ahmed-Abousaif/Omnidroid/issues/new?template=feature_request.yml). Explain the use case, why it benefits Omnidroid users, and how you envision it working.

### Translations

- Base Lemuroid strings can be translated on [Crowdin](https://crowdin.com/project/lemuroid).
- Omnidroid-specific UI strings are located in `omnidroid-app/src/main/res/values/strings.xml`. Pull requests adding or refining localized string resources (`values-<locale>/strings.xml`) are welcome!

---

## Development Setup

### Prerequisites

- **Android Studio:** Android Studio Ladybug / Meerkat or newer recommended.
- **JDK:** Java Development Kit (JDK) 17 (e.g., Eclipse Temurin 17 or OpenJDK 17).
- **Android SDK:**
  - `compileSdk` & `targetSdk`: 36
  - `minSdk`: 23
  - Build Tools: 36.0.0
  - NDK & CMake (for C++/native modules and submodules).
- **Git:** Git 2.30+ with submodule support.

### Cloning the Repository

Omnidroid relies on Git submodules for core packages (such as `omnidroid-cores` and `libchdr`). Make sure to clone with submodules initialized:

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/Ahmed-Abousaif/Omnidroid.git
cd Omnidroid
```

If you already cloned without `--recurse-submodules`, initialize and update them:

```bash
git submodule update --init --recursive
```

### Building the Project

You can build the project from the command line or using Android Studio:

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows (PowerShell / Command Prompt)
.\gradlew.bat assembleDebug
```

To run ktlint verification and unit tests:

```bash
# Run ktlint check
./gradlew ktlintCheck

# Run unit tests
./gradlew test
```

---

## Repository Structure

Omnidroid is a multi-module Gradle project:

| Module | Description |
| :--- | :--- |
| `omnidroid-app` | Core Android application (Jetpack Compose UI, ViewModels, Hilt DI, Navigation). |
| `omnidroid-app-ext-free` | FOSS distribution flavor configurations. |
| `omnidroid-app-ext-play` | Play Store flavor configurations (cloud save providers, in-app updates). |
| `omnidroid-cores` | Libretro core configurations and definitions (Git submodule). |
| `omnidroid-chd` | CHD compressed disc image support (`libchdr` C++ native submodule). |
| `omnidroid-touchinput` | On-screen touch controller layouts, touch coordinates, and haptics. |
| `omnidroid-metadata-rawg` | RAWG game metadata & trailer integration. |
| `omnidroid-metadata-libretro-db` | Libretro DB game scanner and metadata provider. |
| `retrograde-app-shared` | Shared database entities (Room), preferences, and repository layers. |
| `retrograde-util` | Common utility classes and extensions. |

---

## Development Guidelines

### Branching Strategy

- The primary development branch is **`master`**.
- Always create a new topic branch from up-to-date `master` for your changes:

```bash
# Ensure master is up to date
git checkout master
git pull origin master

# Create and switch to your feature or fix branch
git checkout -b feature/your-feature-name
# or
git checkout -b fix/issue-description
```

### Coding Standards & Formatting

- **Language:** Kotlin for Android codebase, modern C++ (C++17/20) for native components.
- **Code Style:** We follow official Android Kotlin style guidelines and `.editorconfig` rules:
  - 4-space indentation.
  - 120-character maximum line length.
  - Consistent import ordering.
- **Linter (ktlint):** Run ktlint before committing to ensure formatting adheres to project rules:
  ```bash
  # Check for formatting violations
  ./gradlew ktlintCheck

  # Automatically format code
  ./gradlew ktlintFormat
  ```

### Compose & Controller Navigation

Omnidroid is designed for handheld consoles and gamepad navigation:
- **Gamepad / Focus Support:** All new screens and dialogs must support hardware D-pad / directional navigation (`Modifier.focusable()`, focus requesters, and clear focus indicators).
- **Landscape-First:** Ensure all layouts adapt cleanly to landscape orientation and varying screen aspect ratios.
- **State Hoisting:** Keep Composables stateless where possible, managing state inside ViewModels.

### Commit Messages

Use clear, descriptive commit messages adhering to the [Conventional Commits](https://www.conventionalcommits.org/) convention:

- `feat: add custom button mapping profile for 8BitDo controllers`
- `fix: prevent crash when rotating screen during ROM scan`
- `refactor: simplify library grid item state hoisting`
- `docs: update build instructions for JDK 17`
- `chore: bump dependency versions`

---

## Submitting a Pull Request

When you are ready to submit your changes:

1. **Test your code:**
   - Verify that `./gradlew assembleDebug` compiles without errors.
   - Verify that `./gradlew ktlintCheck` passes.
   - Test on an emulator or physical Android device (touch and gamepad navigation if applicable).

2. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Open a Pull Request:**
   - Target the `master` branch.
   - Provide a concise title and descriptive PR summary.
   - Link related issues (e.g., `Closes #42` or `Fixes #105`).
   - Include before/after screenshots or screen recordings for any UI or visual changes.

4. **Participate in Code Review:**
   - Address any reviewer feedback or CI check failures.
   - Keep commits organized (squashing or rebasing as requested).

---

## Getting Help

- Join discussions and ask questions in [GitHub Discussions](https://github.com/Ahmed-Abousaif/Omnidroid/discussions).
- Open an issue for bug reports or feature proposals.

Thank you for helping make Omnidroid better for retro gamers everywhere! 🕹️
