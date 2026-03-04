# OkDriver Sensors

## What This App Does
OkDriver Sensors is an Android (Kotlin, XML Views) telemetry app that monitors device sensors and GPS during a driving session.

- Dashboard: live accelerometer, gyroscope, magnetometer, and GPS values with monitoring controls.
- Events: real-time detection log for `HARSH_ACCEL`, `HARSH_BRAKE`, and `SHARP_TURN`.
- Battery: session baseline and automatic 30-minute drain report (start/end battery, timestamps, drain percent).

## Architecture Overview
The app follows a lightweight MVVM + Flow architecture.

- `data/`
  - `sensors/`: `AndroidSensorDataSource` streams sensor snapshots and supports runtime sampling modes.
  - `location/`: `AndroidLocationDataSource` streams GPS snapshots and supports adaptive location intervals.
  - `battery/`: battery percent provider.
  - `events/`: in-memory shared `EventsRepository`.
  - `monitoring/`: shared `MonitoringSessionRepository`.
  - `model/`: app data models (`SensorSnapshot`, `LocationSnapshot`, `DrivingEvent`, `MonitoringSession`).
- `domain/`
  - `DrivingEventDetector`: event detection logic + debounce + severity.
  - `MotionStateEvaluator`: stationary/moving heuristic using speed + acceleration window.
  - `ThresholdConfig`: centralized thresholds and timing constants.
- `ui/`
  - `dashboard/`: monitoring control, live telemetry, optimization state.
  - `events/`: RecyclerView rendering shared event stream and clear action.
  - `battery/`: battery report and monitoring session status.
- `util/`
  - formatting helpers.

### Data Flow
- Sensor and location data sources emit `Flow`.
- `DashboardViewModel` consumes snapshots, updates UI state, runs event detection, and applies optimization mode transitions.
- Events are published to `EventsRepository` and observed by `EventsViewModel`.
- Monitoring session state is persisted with `SavedStateHandle` and exposed through `MonitoringSessionRepository` for `BatteryViewModel`.

## How To Run
1. Open the project in Android Studio.
2. Ensure an emulator/device with sensors and location support is available.
3. Build and run:
   - Android Studio Run, or
   - `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).
4. Grant location permission when prompted for live GPS updates.

## Thresholds And Tuning
All event, motion-state, debounce, and optimization thresholds are centralized in:

- `app/src/main/java/com/okdriver/sensors/domain/ThresholdConfig.kt`

Tune this file to adjust:
- harsh event sensitivity,
- debounce/sustain timings,
- stationary detection windows and hold durations,
- moving/stationary GPS intervals.

## Battery Optimizations Implemented
The monitoring pipeline applies real optimizations while monitoring is ON:

- Stationary detection using:
  - low speed (`< 3 km/h`) and
  - low linear-acceleration window activity.
- Dynamic sensor sampling:
  - moving: faster sampling (`SENSOR_DELAY_GAME`),
  - stationary: slower sampling (`SENSOR_DELAY_UI`) and gyroscope disabled.
- Adaptive GPS update mode:
  - moving: ~1.5s updates,
  - stationary: ~12s updates.
- UI exposes current motion state, sampling mode, GPS interval, and optimization status.

## Known Limitations
- Event detection uses phone-frame heuristics; mounting orientation and road conditions can affect precision.
- Location quality depends on device hardware, permission, and provider availability.
- Battery report timer uses in-process coroutine timing (Phase 5/6 scope), not background scheduler persistence.
- Events are in-memory only (not persisted to disk/Room).
