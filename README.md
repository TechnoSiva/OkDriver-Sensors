# OkDriver Sensors - Android Kotlin Assignment

Android application that collects real-time motion and location data from a mobile device and analyzes driving behavior using device sensors.

The app streams sensor data, detects driving events such as harsh acceleration or sharp turns, and evaluates battery consumption during monitoring while applying runtime battery optimizations.

---

## Application Overview

This application demonstrates how a smartphone can be used as a lightweight telemetry system for driving behavior analysis.

It uses multiple hardware sensors and GPS to:

- Display real-time sensor data
- Detect aggressive driving events
- Track device motion patterns
- Evaluate battery consumption during monitoring
- Apply adaptive battery optimization strategies

The system is designed with a **clean layered architecture** and **lifecycle-aware components** to ensure stability and efficiency.

---

## Key Features

### 1. Real-Time Sensor Dashboard

The dashboard streams live data from device sensors.

Sensors used:

| Sensor | Purpose |
| --- | --- |
| Accelerometer | Detect acceleration and braking forces |
| Gyroscope | Detect rotational motion and sharp turns |
| Magnetometer | Orientation reference |
| GPS | Speed and location tracking |

Displayed values:

- Accelerometer (X, Y, Z)
- Gyroscope (X, Y, Z)
- Magnetometer (X, Y, Z)
- GPS Latitude / Longitude
- Speed
- Accuracy

All values update in real time while monitoring is active.

---

### 2. Driving Event Detection

The system analyzes motion data to detect unsafe or aggressive driving behavior.

Detected events:

| Event | Detection Logic |
| --- | --- |
| Harsh Acceleration | Linear acceleration exceeds threshold |
| Harsh Braking | Negative acceleration exceeds threshold |
| Sharp Turn | High gyroscope Z-axis rotation |

Each event records:

- Event type
- Timestamp
- Severity score
- Vehicle speed (if available)

Events are displayed in the **Events screen** using a live-updating list.

---

### 3. Battery Usage Monitoring

When monitoring begins:

1. Initial battery percentage is recorded.
2. Monitoring runs continuously.
3. After **30 minutes**, battery percentage is measured again.
4. Battery drain is calculated.

```text
Battery Drain = Start Battery % - End Battery %
```

The result is displayed in the **Battery screen**.

This allows evaluation of power usage during sensor monitoring.

---

## Battery Optimization Strategy

The application includes runtime battery optimizations based on device motion state.

### Motion State Detection

The device motion state is classified as:

```text
MOVING
STATIONARY
```

Motion state is determined using:

- GPS speed
- Linear acceleration variance
- Stability duration threshold

---

### Dynamic Sensor Sampling

When the device is **moving**:

| Component | Mode |
| --- | --- |
| Sensors | `SENSOR_DELAY_GAME` |
| Gyroscope | Enabled |

When the device is **stationary**:

| Component | Mode |
| --- | --- |
| Sensors | `SENSOR_DELAY_UI` |
| Gyroscope | Disabled |

Reducing sensor frequency significantly decreases CPU and sensor processing usage.

---

### Adaptive GPS Updates

Location updates adapt based on motion state.

| Motion State | GPS Interval |
| --- | --- |
| Moving | ~1.5 seconds |
| Stationary | ~12 seconds |

This prevents unnecessary GPS usage when the device is not moving.

---

### Additional Power-Saving Techniques

- Sensors only active while monitoring
- Sensor listeners properly unregistered
- Location updates dynamically adjusted
- No continuous polling loops
- Lifecycle-aware Flow collectors

These measures help reduce battery drain during monitoring.

---

## Architecture

The application follows a **clean layered architecture using MVVM**.

```text
UI Layer
Fragments + ViewModels
  ->
Domain Layer
DrivingEventDetector
MotionStateEvaluator
ThresholdConfig
  ->
Data Layer
SensorDataSource
LocationDataSource
BatteryDataSource
Repositories
```

### Technologies Used

- Kotlin
- Android SDK
- Coroutines + Flow
- ViewModel
- SensorManager
- FusedLocationProviderClient

---

## Project Structure

```text
com.okdriver.sensors

data
|-- sensors
|-- location
|-- battery
|-- events
`-- monitoring

domain
|-- DrivingEventDetector
|-- MotionStateEvaluator
`-- ThresholdConfig

ui
|-- dashboard
|-- events
`-- battery

util
`-- helpers
```

---

## How to Run the Project

### Requirements

- Android Studio (latest version recommended)
- Minimum SDK: 33+

### Steps

1. Clone the repository:

```bash
git clone https://github.com/TechnoSiva/OkDriver-Sensors.git
```

2. Open the project in Android Studio.
3. Build the project:

```bash
./gradlew assembleDebug
```

4. Install the APK on a physical Android device.

---

## APK Build Location

After building:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Demo Instructions

1. Launch the application.
2. Tap **Start Monitoring**.
3. Observe live sensor and GPS data on the dashboard.
4. Move or shake the phone to generate events.
5. Open the **Events** screen to see detected driving events.
6. Leave the device stationary to trigger battery optimization mode.
7. Open the **Battery** screen to view monitoring status and battery report.

---

## Author

**K Sivaram Achary**  
Android Kotlin Developer Assignment - OkDriver
