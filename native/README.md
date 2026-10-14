# Native notification scheduling (§1's `expect`/`actual` seam)

This is the last item from §13.3's implementation backlog: *"the two native scheduling
implementations behind the `expect`/`actual` seam §1 already commits to, **not** the logic
that decides when a reminder is due."*

That division is the whole point of the seam and these files hold to it. Nothing here decides
whether a reminder is owed, what its lead time is, or whether it already fired — those are
§3.11's rules, they live in shared code, and the prototype already implements them in
`syncReminderSchedule()` / `fireDueReminders()`.

## The project (Stage 1, 2026-09-14)

`native/` is a Gradle multi-project build -- `:shared` (KMP: Android + desktop JVM, Room, Compose
Multiplatform), `:androidApp`, `:desktopApp` -- pinned to the versions `Builds/Tendril` already
builds with, so it resolves offline. `gradlew --offline clean check :androidApp:assembleDebug` is the
acceptance run. The seam files below moved into `shared/src/*/kotlin/app/scifin/notify/` and the
manifest into `androidApp/src/main/`; the engine (spec §3, §5, §6, §7, §9) lives in
`shared/src/commonMain/kotlin/app/scifin/engine/`, its tests in `shared/src/desktopTest/`, and
`tools/export_fixture.mjs` (repo root) regenerates the parity fixture from the prototype.

## Stage 2 (2026-09-14, complete): the screens

The order is the optimizer's (spec §14): frame → Savings row + quick-add → onboarding screen 1 →
Settings hub → Envelopes → Activity → the rest of onboarding → Categories → Recurring/New Month
surfaces → Reports → §4.8 reflow → Learn + §9 calculators → tutorial. One architectural decision:

```
shared/src/commonMain/kotlin/app/scifin/
  app/AppStore.kt     one StateFlow<UiState>: AppState, the active ProfileData, the Route stack,
                      the open sheet, a pending catch-up summary. dispatch(write) saves through the
                      DAO and re-syncs notifications. Testable without Compose.
  app/Route.kt        sealed Route: tabs, pushes, sheets, Onboarding(step). Back = pop.
  ui/Frame.kt         SciFinApp(store, targetDp): width class from BoxWithConstraints -- bottom bar
                      <600dp, rail >=600 (§4.8 r1); list-detail at >=840 (r2); sheets as dialogs
                      >=600 (r3); Reports never two-pane.
  ui/Format.kt        money in the Profile's currency, dates, cadence words.
  ui/Charts.kt        Canvas-drawn line, bars and share list -- no chart library is in the offline cache.
  ui/components/      SavingsRow, QuickAddSheet, TickHatchBar (§2.1), Card, Row, Field, Sheet.
  ui/screens/         one file per §4.2 row and per push target; pure @Composable(state, tokens, actions).
```

Navigation is hand-rolled: no KMP navigation library is in `~/.gradle/caches`, and §4.8's pane
rule wants the route stack visible anyway. Screen tests render with `ImageComposeScene` over the
parity fixture and assert on the store's state; no Compose `ui-test` artifact is cached.

Every visible decision the spec does not make -- a placeholder, a row label, an empty state's
sentence -- is put to the person and recorded in the spec before it is coded. Nothing about
layout, wording or first-run behaviour is decided in this directory.

Status: all thirteen items built; `gradlew --offline :shared:desktopTest :desktopApp:test
:androidApp:assembleDebug` runs 184 tests green. `shared/build/screen-renders/` and
`desktopApp/build/frame-renders/` hold the headless renders of every screen over the parity
fixture (phone widths and 1100dp) after a test run. Debug package on a device: `app.scifin.debug`.
Open: §4.5's Learn bodies (need the research audit), the channel for §3.11's completion notices.

## Files

| File | What it is |
| --- | --- |
| `shared/src/commonMain/.../NotificationScheduler.kt` | The `NotificationScheduler` interface, the `expect` context + factory, `ReminderRegistration`, and §3.9's fixed copy |
| `shared/src/androidMain/.../NotificationScheduler.android.kt` | `actual` — WorkManager |
| `androidApp/src/main/AndroidManifest.xml` | The one permission, and a note on the two deliberately absent ones |
| `shared/src/desktopMain/.../NotificationScheduler.windows.kt` | `actual` — WinRT scheduled toasts + Task Scheduler + logon entry |

## Why an interface plus one `expect` factory

The two platforms need genuinely different things to construct: Android a `Context`, Windows
the path to the executable its Task Scheduler entries invoke. An `expect class` cannot express
that — its actuals would have to agree on a constructor signature, and forcing them to means
inventing a holder that exists only to satisfy the compiler.

So the contract is a plain interface, and the only `expect` declarations are `PlatformContext`
(a one-field holder of `Context` on Android — it was a typealias until the first compile on 2026-09-14 refused the modality mismatch between a final expect class and the abstract `Context`; a one-field class on desktop) and
`notificationScheduler(context)`. §1 asks for "a single expect/actual scheduling API" — this is
one API with one construction point.

The one exception: `AndroidNotificationScheduler` is public, because its `permissionRequester`
has to be installed by the host Activity that owns the `ActivityResult` launcher, and shared
code holding the interface cannot reach it. Android host code constructs that type directly;
everything else goes through the factory.

## No network, by construction

The Android manifest declares no `INTERNET` permission and the Windows build makes no network
call. §1's networked-features table has no rows, and this is what makes that table true rather
than aspirational: an app without the permission cannot open a socket. A feature that needs the
network adds its row to that table first (added 2026-09-14).

## The contract, stated once

**The seam is declarative.** `syncReminders(list)` makes the OS match the list exactly —
anything previously registered and absent is cancelled. This is what satisfies §3.11's
"rescheduled whenever a `dueDate` advances" by construction: advance the date, hand over the
new list, done. There is no path where a stale registration survives because a cancel was
missed.

**The caller filters.** `ReminderRegistration.fireAt` must be in the future. Shared code drops
anything already fired (§12's `lastReminderFiredFor`) before calling. Both implementations
additionally skip past-dated entries rather than delivering them late — firing one on a
restore is exactly the *reboot re-fires what already fired* bug that field exists to prevent.

**Bodies are composed before registration.** No process of ours is alive at delivery time on
either platform, so nothing can build text then. This is also what makes the Windows route
viable at all.

**Nothing here throws at the caller.** A scheduling failure prints and returns. §4.7 already
carries the honest line when the OS will deliver nothing; a crash at the moment someone edited
a due date would be a worse channel for the same fact.

## Android — WorkManager

Chosen over `AlarmManager.setExactAndAllowWhileIdle`, which is the obvious reach and the wrong
one: Android 12 put it behind `SCHEDULE_EXACT_ALARM`, and Play restricts `USE_EXACT_ALARM` to
apps whose core function is alarms or timers. A bill reminder does not qualify. Shipping it
would be the same class of mistake §7.3 already refused with `READ_SMS` — requesting a
permission whose policy gate the app cannot honestly pass.

It is also unnecessary. §3.11's lead time is a whole number of **days**; arriving on the right
morning is the artifact, and exact-to-the-second never was.

WorkManager persists its queue and re-enqueues across reboot, so **§13.3's boot recovery costs
nothing** and the app needs no `BOOT_COMPLETED` receiver of its own.

§3.9's monthly ping cannot be a `PeriodicWorkRequest` — periodic work is a fixed interval and
months are not one — so each occurrence enqueues the next. A self-renewing chain, no horizon.

## Windows — three pieces, and why

- **`ScheduledToastNotification` is the mechanism.** Hand the OS a toast and a time; it fires
  with nothing of ours running, and survives reboot. This is what makes §3.11's requirement
  satisfiable: *"a reminder that only appears once you have already opened the app is not a
  reminder."*
- **A daily Task Scheduler entry is the horizon.** Toasts must be registered before they can
  fire, and §3.9's ping recurs indefinitely. The task re-arms the next slice. Without it the
  app is only ever armed as far ahead as its last launch.
- **A logon entry is the recovery** (§13.3).

No resident service: a local-only budgeting app has no business running all the time, and the
toast queue means it does not have to.

### Two integration prerequisites — both met by the app itself (walked 2026-09-15)

1. **AppUserModelID.** Windows addresses toasts by AUMID. A packaged (MSIX) build gets one
   from its manifest. An unpackaged Win32 build needs a **Start Menu shortcut carrying
   `System.AppUserModel.ID`** matching the `AUMID` constant — not because `CreateToastNotifier`
   throws (it does not) but because without it Windows accepts every registration and shows
   none, with no error and no history entry. A per-user `HKCU\Software\Classes\AppUserModelId`
   entry and a process-level AUMID were each tried on the walk and neither was enough; the
   shortcut alone was. jpackage's MSI writes a shortcut without the property, so
   `WindowsNotificationScheduler.installAppRegistration()` writes one on the first normal launch
   of the packaged executable (through the shell property store, from inline C#).
2. **A headless sync mode.** The Task Scheduler entries invoke the app as
   `--sync-notifications`: `main` re-arms from stored state (`AppSession.restoreAfterBoot`) and
   exits without a window, logging what it did to `launch.log` in the data folder since the
   packaged launcher has no console. The same first normal launch registers the two entries
   (`Register-ScheduledTask`, executable and argument as separate values — `schtasks /TR`'s
   one-value form cannot survive the JVM's argument quoting). A development launcher
   (`java.exe`) installs nothing: a task that re-launched the JVM daily would be junk.

The walk's other findings, each fixed: the registration script needs a UTF-8 BOM or Windows
PowerShell reads "€" as `â‚¬` and its parser breaks on the `‚`; and the notifier's `Setting`
property is empty through PowerShell's projection, so the permission read comes from the
registry (`...\Notifications\Settings`). The tests that drive the real OS live in
`WindowsNotificationWalkTest` and run only with `SCIFIN_WALK=1`.

### On calling WinRT from the JVM

Compose Multiplatform's desktop target is a JVM and cannot call WinRT directly. Rather than
take on a JNI shim for four API calls, this drives the same WinRT types through PowerShell:
the process registers the scheduled toast with the OS and exits, and the registration outlives
it. The OS is holding the toast either way, so this is a real implementation rather than a
placeholder — but it is the one design decision here I would revisit first if the desktop
build later gains a native bridge for other reasons.

## Permission handling (§11.5, §12)

`permissionState()` is **re-read from the OS on every call**, never trusted from storage —
§12's rule, because the permission can be revoked at any time.

- **Android** has a runtime prompt; §11.5 requests it at onboarding step 2.
  `areNotificationsEnabled()` is checked *first*, because a granted runtime permission with
  notifications switched off in system Settings still delivers nothing.
- **Windows has no runtime prompt** — notifications are on per-app by default and disabled
  from system Settings. §11.5: the onboarding step is *skipped entirely* there rather than
  rendered as an in-app stand-in for a dialog that does not exist. `NOT_ASKED` never occurs on
  this platform.

Either way §4.7's plain line beneath the Notifications toggle behaves identically: it appears
whenever the OS-level answer is negative, however that answer was arrived at.

## ~~Not compiled here~~ Compiled 2026-09-14

Both targets compile and the tests run. The compiler found two defects the eye-review below had
not: kotlinx-datetime 0.7 moved `Clock`/`Instant` to `kotlin.time`, and the Android
`actual typealias PlatformContext = Context` is illegal (a final expect class against an abstract
actual), so Android now carries a one-field holder. The paragraph that follows is kept as the
record of what was known before the first build.

There was no Kotlin toolchain where these were written, so **these files had not been
compiled** — they are reviewed by eye against the KMP rules, not verified by `kotlinc`. The
structural risk that check would have caught first (an `expect class` whose actuals disagree on
their constructor) is designed out above rather than left to be discovered. Treat a first
`./gradlew build` as the real verification.

## What is still not built

The prototype's `syncReminderSchedule()` is the app side and is complete. Wiring it to this
seam needs the host app to construct the platform `NotificationScheduler` and call it from
every path that already calls `syncReminderSchedule()` — mechanical, but it belongs to the
Compose app rather than to either file here, so it is not attempted in this directory.
