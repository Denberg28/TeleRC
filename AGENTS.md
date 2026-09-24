# Project continuity

TeleRC is an Android Kotlin prototype, applicationId `io.github.denberg28.telerc`, version 0.8.0/code 18. Preserve this identity and signing key on upgrades. Read README before changing controls. Never add arming or flight use claims without SITL and bench evidence. Maintain heartbeat timeout and lifecycle disconnect. No secrets, signing keys, or release keystores in Git. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`; record actual results. Pending: SITL validation, hardware bridge tests, cryptographic MAVLink validation, release signing decision.
