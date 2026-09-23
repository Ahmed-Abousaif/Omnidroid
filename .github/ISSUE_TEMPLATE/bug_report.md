name: Bug Report
description: Report something broken in Omnidroid
title: "[Bug]: "
labels: ["bug"]
body:

- type: textarea
  id: description
  attributes:
  label: Describe the bug
  description: What happened, and what did you expect instead?
  validations:
  required: true
- type: textarea
  id: steps
  attributes:
  label: Steps to reproduce
  placeholder: | 1. Open... 2. Tap... 3. See error
  validations:
  required: true
- type: input
  id: device
  attributes:
  label: Device
  placeholder: e.g. Pixel 8 Pro
  validations:
  required: true
- type: input
  id: android-version
  attributes:
  label: Android version
  validations:
  required: true
- type: input
  id: app-version
  attributes:
  label: Omnidroid version
  placeholder: e.g. v2.1.0-beta
  validations:
  required: true
- type: dropdown
  id: build
  attributes:
  label: Build type
  options: - Free (GitHub release) - Play Store
  validations:
  required: true
