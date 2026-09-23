name: Feature Request
description: Suggest a new feature or improvement for Omnidroid
title: "[Feature]: "
labels: ["enhancement"]
body:

- type: textarea
  id: problem
  attributes:
  label: What problem does this solve?
  description: Is this related to something that's missing or annoying right now?
  placeholder: e.g. I can't tell which games I've already finished
  validations:
  required: true
- type: textarea
  id: solution
  attributes:
  label: Proposed solution
  description: What would you like to see added or changed?
  validations:
  required: true
- type: textarea
  id: alternatives
  attributes:
  label: Alternatives considered
