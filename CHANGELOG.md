# Changelog

<!--
Pending release notes. Write the entries for the *next* release below this comment.

The release workflow copies everything after the title and this comment into the GitHub Release
body, and aborts the release if there is nothing there. Once the release is published it resets
this file back to this header on the development branch — published notes live in the GitHub
Release, so this file never accumulates version sections.

Suggested shape, delete what you do not use:

## Added
-

## Changed
-

## Fixed
-
-->

## Added

- Every item, block, fluid and entity carries its vanilla translation key, so a consumer can
  localise a display name instead of formatting the registry key itself.
- A manual `Extract registry` workflow runs the extractor in a headless client beside a chosen set
  of Integrated mods, then either uploads the registry JSON or commits it into the docs repository.
  Its `minecraft_version` input picks the line to extract for, so one branch can serve several.

## Fixed

- Tag lists are sorted before they are written, so the same registry produces the same document on
  every run.
