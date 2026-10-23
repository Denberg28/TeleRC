# Balancer motor test and setup — TeleRC 0.8.90 / firmware 0.3.1

Baseline: app0.8.89/code107, commit cbfb507c8513b3dd57cba84762ac37249ce76e05;
Library standalone firmware0.3.0 matches bridge/TT_Balancer_ESP32S3 sources.
User reports calibration ready but no physical movement during step2.

Confirmed source findings: test duty55/255 for150ms, no encoder/current/voltage
feedback, firmware completion cannot establish movement; Android0.8.89 only
accepts testMs150 and exactly20 status fields. Working MiniRover uses20kHz
proportional dual-input PWM, explicit LEDC channels and inactive-leg-first writes.
Its documentation explicitly specifies no automatic startup kick.

Change: app0.8.90/code108 and balancer0.3.1. Explicit suspended-wheel tests are
140/255 for300ms with unchanged MAX_PWM160, safety/arming, sensor profile,
calibration storage, PID and balancing output mapping. Persist last-test duty
readback for the next poll. PWM register values are software/peripheral evidence,
not physical power or rotation. Do not copy MiniRover's GPIO8–11 motor map onto
the balancer:GPIO8/9 remain the IMU I2C bus. New test duration is capped500ms.

App0.8.90 reads both20-field old and29-field new reports. New firmware needs
new app: install APK first, retain data/signature, then upload complete sketch.
Never auto-retry/replay motor actions. Existing one-use challenge,500ms queued
request expiry, session/boot consent and STOP priority remain. New observation
checkbox cannot clear firmware's completed-Both gate. Calibration ready already
completes step1; avoid repeatedly erasing working saved calibration.

Hardware acceptance (not performed): disconnect motor power to upload; reset
upright/still; connect to TeleRC-Balancer/192.168.4.1:14550; confirm calibration
ready. Apply regulated motor supply, common GND, support with wheels raised.
Read Left/Right/Both command acknowledgement and last-test register values;
check single-side masks, expected directions, <=300ms commanded duration, zero
and every stop/fault path. Measure MX1508 supply/output voltage/current during
the pulse; do not infer voltage from a connected battery. Do not confirm if
wheels never move. Check sensor tilt signs before verification/arming. Actual
chassis motion, PWM timing, H-bridge/current/heat and balance tuning remain HIL.

Phone acceptance: compact popup stays bounded/scrollable in three themes and
large text; footer visible; offline/uncalibrated/stale/PWM-fault/STOP/moving states
lock tests; observation resets on boot/calibration/new test/disconnect; stale
confirmation or navigation/background cannot send. No movement help sends no
motor command. A finished pulse is labeled as requiring visual observation.

Rollback: retain0.8.89 and firmware0.3.0. The new app supports the old firmware,
so roll back firmware with motor power disconnected while retaining app data
and saved gyro format. APK downgrade generally requires uninstall/data loss;
prefer a forward-code rollback if needed. No stable promotion/publication.

Final gate results and build identity are recorded in docs/TESTING.md and
release BUILD_INFO. Target compile is software evidence; physical reliability
and UI measurements require the stated hardware/phone acceptance.
