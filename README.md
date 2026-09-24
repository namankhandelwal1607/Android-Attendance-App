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

### 1. Login Accounts & Seeded Demo Data

The app includes a dedicated login screen with Username/Employee-ID and Password authentication, verified locally against Room SQLite and fallback demo credentials.

#### Pre-loaded Accounts:

| Role | Name | Employee ID | Username | Password | Face Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Admin** | System Administrator | `ADM-001` | `admin` | `admin123` | Admin Portal Access |
| **Staff** | Rohan Sharma | `EMP-101` | `rohan` | `rohan123` | Pending Enrolment |
| **Staff** | Priya Verma | `EMP-102` | `priya` | `priya123` | Pending Enrolment |
| **Staff** | Aman Gupta | `EMP-103` | `aman` | `aman123` | Pending Enrolment |
| **Staff** | Sneha Iyer | `EMP-104` | `sneha` | `sneha123` | Pending Enrolment |
| **Staff** | Karan Mehta | `EMP-105` | `karan` | `karan123` | Pending Enrolment |

> **Important**: Staff faces must be enrolled by an Admin via the Admin Portal before attendance can be marked for them.

#### Generic Fallback Credentials:
- **Admin**: `username: admin` | `password: admin123` (Routes to Admin Portal)
- **Staff**: `username: staff` | `password: staff123` (Routes to Staff Attendance)

### 2. Testing Flow
1. **Admin Login & Face Enrolment**:
   - Sign in with `username: admin` / `password: admin123`.
   - In the **Admin Dashboard**, open the **Staff Directory** tab to view the 5 pre-loaded staff members (showing *Face Pending*).
   - Tap **"+ Enrol Staff"**, enter an employee's details (e.g. `Rohan Sharma` and `EMP-101`), capture a face selfie inside the oval guide, and tap **"Save & Enrol Staff Member"**.
   - The employee's record is immediately updated with their 192-d facial embedding.
2. **Staff Login & Attendance Verification**:
   - Log out from Admin and sign in as `rohan` / `rohan123` (or any enrolled staff member).
   - The app automatically routes to **Staff Attendance** with Rohan selected.
   - Tap **"Open Camera & Check In"** $\rightarrow$ look directly into the camera.
   - **Verification**: The app computes cosine similarity on-device:
     - **Match (≥70%)**: Confirms attendance with green badge, confidence %, timestamp, and reverse-geocoded GPS street address.
     - **Mismatch / Unenrolled**: Prompts an error badge without recording attendance.
3. **Review Attendance Logs**:
   - Log back in as Admin $\rightarrow$ check **"Who is Present"** tab to see live attendance entries with captured selfies and locations.

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
