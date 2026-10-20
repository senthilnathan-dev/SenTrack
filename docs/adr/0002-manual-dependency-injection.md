# ADR 0002: Manual dependency injection

- Status: Accepted (2026-10-19); supersedes the early plan to use Hilt

## Context
The app has one process, one user, and a small set of long-lived objects (vault, database holder, repositories, reminders
engine, backup manager). The security design needs tight control over when things are created and destroyed (everything that
holds secrets must be dropped on lock).

## Decision
Build the long-lived objects in one `AppContainer` created by `SenTrackApp` and pass what a screen needs as parameters. No DI
framework and no annotation processing beyond Room's.

## Consequences
Less magic and faster builds; lifetimes are visible in one file. Adding a dependency means editing `AppContainer` and the call
sites. If the object graph grows much larger, revisit.
