# TeleRC v0.8.88 — compact UI and safety review

Balance is now available only from the tab dropdown; the duplicate Setup header button is removed. Dropdowns stay inside the visible screen. Shared popups have equal side padding, compact widths, centered headings and equal-height actions. Setup, Controls, Test, Waypoint and Balance share consistent action styling and readable disabled states.

Opening the tab dropdown now disables manual control or requests HOLD during Waypoint movement, just like a dialog; closing it never resumes driving.

Balance confirmations are bound to the original connection session and robot boot. Reconnecting or restarting requires a new confirmation. Manual telemetry rejects unsupported MAVLink2 flags, incorrect source components and malformed frames; valid MAVLink2 zero-truncated ACK/position messages remain supported. APK download verification is single-flight and checks DownloadManager status before trusting a completion broadcast.

Install `TeleRC-v0.8.88.apk` over the existing app without uninstalling. Settings, wallpaper, wallet and mission drafts are retained. **App-only update:** keep the firmware already working with your rover. Balancer setup continues to require balancer **v0.3.0** from the v0.8.87 release; no new reflash is required for this update.

Open Setup → tab dropdown → Balance. Calibrate only when needed, raise/secure the wheels for motor tests, and confirm directions only after observing the physical result. Setup never arms. Open Controls, enable Phone control, center sticks and ARM deliberately.

Automated build, lint, regression, native UI and signing results are in `BUILD_INFO.txt`; the code review and remaining gaps are in `CODE_REVIEW.md`. This is a prerelease. Actual phone/USB/Wi-Fi/motor motion and balancing remain unverified. Existing Wi-Fi/MAVLink is unsigned: use an isolated trusted rover network. Native ELRS Backpack remains handset-only; Mini and existing USB LoRa remain planning-only for Waypoint.

Rollback: keep v0.8.87 and its matching firmware. Android normally blocks lower-code installation; retain app data and request a forward-version rollback build if needed. Do not clear data simply to downgrade. Stable update channel remains unchanged.
