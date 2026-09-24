# Smart Attendance App — Selfie Face Recognition & Geolocation

An Android attendance system built with **Kotlin** and **Jetpack Compose**, featuring on-device **Face Recognition** using TensorFlow Lite & Google ML Kit, **CameraX** live selfie capture, **GPS Geolocation** tracking, and local **Room SQLite** persistence.

---

## 📱 Features

- **Role-based Access**: Clean login portal for **Admin** and **Staff** with instant role switching.
- **Staff Management (Admin)**:
  - Add staff members with Full Name and unique Employee ID.
  - Live camera selfie capture with an oval face-alignment guide.
  - Automatic face detection and extraction of 192-dimensional embeddings via MobileFaceNet.
  - View staff directory with search filtering and profile overview.
  - View detailed staff profiles with full attendance history, check-in timestamps, GPS location, and match confidence scores.
- **Attendance Verification (Staff)**:
  - Select staff profile or search by Employee ID.
  - Front-camera selfie capture with real-time face detection.
  - On-device 1:1 face verification against the enrolled face embedding using **Cosine Similarity**.
  - **Threshold Security**: Attendance is only marked if the face similarity meets or exceeds **70%**.
  - Automatically records **exact timestamp**, **selfie photo**, and **GPS location** (latitude, longitude, and reverse-geocoded readable address).
- **100% Offline & Private**: Zero external cloud API calls or paid subscriptions required; all ML inference and data storage happen locally on-device.

---

## 🛠️ Architecture & Tech Stack

### Architecture: MVVM (Model-View-ViewModel) + Clean Architecture
- **UI Layer**: Jetpack Compose with Material 3, Navigation Compose, and reactive StateFlow streams.
- **Domain & Repository Layer**: Kotlin Coroutines for non-blocking asynchronous operations.
- **Data Layer**: Room Database (SQLite) + private internal app storage for selfie images.

### Key Libraries & Components

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **Language & UI** | Kotlin 1.9.24 + Jetpack Compose (Material 3) | Modern declarative UI with reactive state management. |
| **Camera** | AndroidX CameraX (`1.3.3`) | Official Android camera API with seamless lifecycle integration and front/back lens switching. |
| **Face Detection** | Google ML Kit Face Detection (`16.1.6`) | Fast, accurate on-device face bounding box detection and alignment. |
| **Face Recognition** | TensorFlow Lite (`2.14.0`) + `mobilefacenet.tflite` | Generates 192-d L2-normalized embeddings, compared via Cosine Similarity. |
| **Database** | AndroidX Room (`2.6.1`) | Type-safe local SQLite persistence for staff and attendance records. |
| **Geolocation** | Google Play Services Location (`21.2.0`) | High-accuracy GPS coordinates (`FusedLocationProviderClient`) + Geocoder for street addresses. |
| **Image Loading** | Coil Compose (`2.6.0`) | Smooth, memory-efficient rendering of captured selfie photos. |

---

## 🚀 How to Run the App

### Option A: Install Pre-built APK via ADB (Quickest)

Connect your Android device via USB with USB Debugging enabled, then run:

```bash
adb install AttendanceApp.apk
```

*(The APK is located in the root directory: `AttendanceApp.apk` or in `app/build/outputs/apk/debug/app-debug.apk`)*

### Option B: Build and Run from Source

1. Clone or open the repository in **Android Studio Hedgehog / Iguana / Jellyfish** (or later).
2. Ensure you have Android SDK 34 installed.
3. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run directly on an attached device or emulator with camera support.

---

## 🔑 Demo Credentials & Quick Test Guide

### 1. Login Screen
- The login screen provides two one-tap entry points:
  - **Admin Portal**: Tap **"Admin Portal"** to manage staff and enrol faces.
  - **Staff Attendance**: Tap **"Staff Attendance"** to mark attendance via face verification.

### 2. Testing Flow
1. **Enrol Staff (Admin)**:
   - Open **Admin Portal** $\rightarrow$ tap **"+ Enrol Staff"**.
   - Enter Name (e.g., `Alex Mercer`) and ID (e.g., `EMP-101`).
   - Tap **"Capture Face Selfie"** $\rightarrow$ grant camera permission $\rightarrow$ position your face inside the green guide oval $\rightarrow$ press the capture shutter button.
   - Tap **"Save & Enrol Staff Member"**.
2. **Mark Attendance (Staff)**:
   - Return to the Home screen and open **"Staff Attendance"**.
   - Select your profile (`Alex Mercer`).
   - Tap **"Open Camera to Mark Attendance"** $\rightarrow$ grant location/camera permissions.
   - Look directly into the camera and capture a selfie.
   - **Verification**: The app computes the cosine similarity against the enrolled face.
     - **Match**: Shows a green success confirmation, match confidence % (e.g. 94%), current timestamp, GPS coordinates, and street address.
     - **Mismatch**: If a different person tries to mark attendance, the app rejects it with an error badge and does not record attendance.
3. **Verify Profile & Logs**:
   - Go back to **Admin Portal** $\rightarrow$ tap on the staff member's card to view their profile, enrolled picture, and detailed attendance log entries.

---

## ⚙️ Assumptions & Limitations

1. **Camera Permissions**: The app requires `CAMERA` and `ACCESS_FINE_LOCATION` permissions. Prompts are displayed in-app when launching camera/attendance features.
2. **Face Recognition Threshold**: The cosine similarity threshold is set to **0.70** (70%), which provides strong separation between genuine matching faces and different individuals while allowing for normal lighting variations.
3. **Lighting & Angles**: Like any optical face recognition system, good lighting and facing forward directly towards the camera provides optimal matching accuracy.
4. **Offline Location**: If GPS is enabled but internet is unavailable, latitude and longitude coordinates are still stored accurately, with fallback coordinate text if the reverse geocoder cannot reach network map servers.

---

## 📦 Deliverables Checklist

- [x] Android Studio / Gradle Project with clean MVVM architecture
- [x] Pre-built debug APK (`AttendanceApp.apk`)
- [x] Comprehensive README
- [x] Ready for GitHub submission
