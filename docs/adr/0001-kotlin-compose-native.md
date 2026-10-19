# ADR 0001: Native Android with Kotlin and Jetpack Compose

- Status: Accepted (2026-10-19)

## Context
Android-only personal app that needs alarms, biometrics, the Keystore, location, app launching and background sync, with strong security and Play Store compliance.

## Decision
Use Kotlin and Jetpack Compose with clean architecture and MVVM.

## Alternatives considered
- Flutter: good cross-platform story, but security and alarm features would depend on plugins.
- React Native: more native bridging for the same features.

## Consequences
Best platform support and learning value; no iOS reuse (not a requirement).
