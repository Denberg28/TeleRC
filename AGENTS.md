# TeleRC development instructions

Apply Marlou's AGENTS.md v1.1 workflow (6 October 2026): **Define → Baseline → Design → Implement → Review → Verify → Package → Deliver**. Prioritize safety, correctness, reliability, recoverability and compatibility. Prefer free-first solutions and existing hardware.

## Context and scope

- Read README, this file, relevant sources/tests, CI, and `docs/TESTING.md`, `docs/ARCHITECTURE.md`, `docs/KNOWN_ISSUES.md` before consequential changes.
- Inspect the working tree, branch and destination. Preserve user changes; never reset/clean/force-push to simplify work. Use isolated review branches. Complete authorized work without repeated approvals; publication requires authorization.
- Record the actual baseline, assumptions, acceptance criteria, affected clients and rollback. Repository state and observed evidence override conversation claims.
- TeleRC is Android/Kotlin, application ID `io.github.denberg28.telerc`, minSdk 26. Read the version from `app/build.gradle.kts`; preserve signing identity and increasing version codes. Never commit credentials, keystores or pairing keys.

## Control and compatibility

- Preserve CH1 steer / CH2 bidirectional drive, 1500 µs neutral, existing sparse PC overrides, source selector and independent downstream watchdogs.
- Keep the dedicated motor ESP32-S3 fixed; connect exactly one Wi-Fi or LoRa gateway through the keyed UART harness. Swap only with power removed. No conflicting active authorities.
- Distinguish transport connected, fresh telemetry, control enabled, armed, neutral hold, receiver release and motor-off. Reconnection or recovered heartbeat must never automatically enable or arm.
- Bound queues, buffers and retries. Do not let telemetry/discovery refresh stale motor commands. Do not replay expired ARM requests. Keep radio/diagnostics outside the motor loop.
- Update compatibility and paired-upgrade notes for changes spanning Android, PC, gateway, motor MCU or FC. No flight-use or physical reliability claims without relevant measured evidence.

## Verification and delivery

- Record gates A–F in `docs/TESTING.md`, using PASS/FAIL/NOT TESTED/BLOCKED and explicit reviewed/build verified/simulator tested/hardware tested/field validated evidence labels.
- Required commands: `python bridge/sync_motor_core.py --check`, `bridge/tests/run.sh`, `SANITIZE=1 bridge/tests/run.sh`, and `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`. For packaging also lint/build the release variant and compile every affected actual ESP32 target with the pinned CI toolchain.
- Add meaningful regression protection for behavioral fixes. Later changes invalidate affected evidence. Host stubs are not hardware or actual-board compilation.
- Provide executable restrained-wheel HIL procedures when hardware is unavailable. Mark missing HIL BLOCKED, continue software work, and do not claim stable readiness.
- Resolve known critical/high defects before publishing even a test candidate. Candidates require automated and packaging checks; stable promotion additionally requires every applicable hardware/installation/compatibility gate. Current release automation publishes prereleases only.
- Deliver source/artifacts tied to a commit with checksums, changelog, limitations, installation and rollback. Record completed work and the next concrete check in repository docs so continuity does not depend on chat history.
