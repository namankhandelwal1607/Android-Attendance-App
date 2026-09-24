# Smart Attendance App — 1:N Facial Kiosk, Admin Portal & Groq AI Assistant

An enterprise-ready Android attendance system built with **Kotlin** and **Jetpack Compose**, featuring on-device **1:N Face Recognition** using TensorFlow Lite & Google ML Kit, **CameraX** live selfie capture, **GPS Geolocation** verification, local **Room SQLite** persistence, and an intelligent **Groq Llama-3.3 AI Assistant** for natural-language queries and executive summaries.

---

## 📱 Core Features & Flow

1. **Clean Initial State & Role-Based Access**:
   - On a fresh installation, **zero pre-seeded staff exist**. Only one seeded Admin account exists:
     - **Username**: `admin`
     - **Password**: `admin123`
   - All staff members must be explicitly registered and enrolled by an Admin.

2. **Admin "Register Staff" with Mandatory Facial Enrolment**:
   - Admin registers each employee with **Full Name** and **Employee ID**.
   - Admin sets a password manually or taps **Auto-Generate** (e.g., `username = alice`, `password = 6-digit random code`) with credentials clearly displayed to hand to the employee.
   - **Mandatory Face Enrolment**: Requires taking a front-camera selfie with an oval face-alignment guide. The app detects the face, normalizes it, and extracts a 192-dimensional embedding via MobileFaceNet before saving. Staff cannot be saved without an enrolled face.

3. **Kiosk "Mark Attendance" (No Login Required)**:
   - Available directly on the landing screen via a prominent **"📷 Mark Attendance (Face Kiosk)"** button.
   - Any staff member walks up to the kiosk, taps the button, and faces the camera.
   - **True 1:N Face Identification**: The system computes on-device cosine similarity of the captured selfie against **all registered staff** simultaneously.
   - **Threshold Security (≥ 0.70)**: If the highest similarity is $\ge 70\%$, the staff member is automatically identified and their attendance is marked with GPS coordinates, reverse-geocoded address, and timestamp.
   - If no staff member matches $\ge 70\%$, an explicit error is displayed: *"Face not recognized — please contact Admin"*, and no record is logged.

4. **Staff Portal (Self-Scoped & Read-Only)**:
   - Staff sign in using their registered username/Employee ID and password.
   - Purely self-scoped: staff view their own photo, Employee ID, check-in count, and read-only attendance history.
   - Zero access to other employees' records, and no enrolment/administrative capabilities.

5. **Admin Dashboard & Combinable Filters**:
   - **All Staff Summary**: Total registered staff, per-staff check-in counts, and latest check-in timestamps.
   - **Attendance Records Tab**: Filterable by:
     - Specific Staff member (or all staff)
     - Single Date or Date Range (Today, Last 7 Days, Custom range)
     - Time-of-day window (e.g., 9:00 AM – 10:00 AM)
     - All filters are dynamically combinable with a 1-tap "Clear All" reset.

6. **AI Admin Assistant (Groq Cloud)**:
   - Natural language queries (e.g. *"Show me who was late today"*, *"Who checked in between 9 and 10 AM?"*, *"Did Alice check in this week?"*).
   - Instant executive **Daily Attendance Summary & Anomaly Report** (punctuality, late arrivals, missing check-ins).
   - Offline heuristic fallback ensures the app remains operational even without internet connectivity.

---

## 🔑 Demo Credentials

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin` | `admin123` | Full administrative access to dashboard, staff registration, and AI assistant. |
| **Staff** | *Created by Admin* | *Set or generated at registration* | Read-only self-scoped portal. Can also log in using their Employee ID. |

---

## 🎬 4-Step End-to-End Demo Script

Follow this 4-step script to test the entire system end-to-end:

### Step 1: Register a Staff Member as Admin
1. Open the app and log in with Admin credentials (`admin` / `admin123`).
2. On the **All Staff** tab, tap the **"+ Register Staff"** button.
3. Enter:
   - **Full Name**: `Alice Smith`
   - **Employee ID**: `EMP-201`
4. Tap **"Auto-Generate"** to create a username (`alice`) and a 6-digit password (note them down).
5. Tap **"Open Camera & Capture Face"** and capture a selfie within the oval guide.
6. Tap **"Register & Enrol Staff"**. Alice is now registered with her facial embedding stored in Room SQLite.

### Step 2: Mark Attendance via Kiosk (No Login)
1. Log out from the Admin portal to return to the landing screen.
2. Tap the prominent blue **"Mark Attendance (Face Kiosk)"** button (no login needed).
3. Face the front camera and tap **"Identify & Mark Attendance"**.
4. The system runs 1:N cosine similarity against all enrolled staff, identifies **Alice Smith** with high confidence (e.g., 90%+ match), and records her attendance along with current GPS coordinates and street address.

### Step 3: Verify Staff Read-Only History
1. Return to the landing screen.
2. Under "Sign In to Portal", enter Alice's credentials (`alice` and her 6-digit password).
3. Alice's **"My Attendance"** screen opens, displaying her profile, Employee ID `EMP-201`, and the attendance record just marked in Step 2.
4. Alice cannot see any other employee's records or perform administrative actions.
5. Tap **"Log Out"** in the top bar.

### Step 4: Admin Records Filter & AI Assistant
1. Log back in as Admin (`admin` / `admin123`).
2. Go to the **Records** tab $\rightarrow$ tap **"Filter Attendance Records"** $\rightarrow$ select `Alice Smith` to view Alice's filtered attendance.
3. Switch to the **AI Assistant** tab:
   - Tap **"Generate Daily Summary"** to get a 3–4 bullet executive briefing.
   - Or type a query like: *"Show me Alice's attendance"* and tap **Ask AI**. The assistant extracts the filter, queries Room SQLite, and presents the matching records.

---

## 🤖 AI Layer Architecture (Groq & FastMCP)

```
┌──────────────────────────────────────────────────────────┐
│                     Jetpack Compose UI                   │
│   (Admin Dashboard AI Tab / Search / Filter Controls)    │
└────────────────────────────┬─────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────┐
│                  AppViewModel & Repository               │
└──────────────┬────────────────────────────┬──────────────┘
               │                            │
               ▼                            ▼
┌──────────────────────────────┐ ┌─────────────────────────┐
│     AttendanceQueryAgent     │ │   Local Room Database   │
│   (OkHttp 4.12.0 Client)     │ │   (Staff & Attendance)  │
└──────────────┬───────────────┘ └──────────▲──────────────┘
               │                            │
               ▼                            │
┌──────────────────────────────┐            │
│       Groq Cloud API         │            │
│  (llama-3.3-70b-versatile)   │            │
│  OpenAI-compatible Endpoint  │            │
└──────────────┬───────────────┘            │
               │ Parses JSON structured     │
               │ query filters              │
               └────────────────────────────┘
```

### 1. Groq Integration
- Uses OkHttp to communicate with the Groq OpenAI-compatible Chat Completions endpoint (`https://api.groq.com/openai/v1/chat/completions`).
- Model: **`llama-3.3-70b-versatile`** with `response_format: { type: "json_object" }` for zero-shot natural language filter parsing.
- Query Parsing Pipeline:
  1. The user inputs a query in plain English (e.g. *"Who arrived after 10 AM yesterday?"*).
  2. Groq extracts structured JSON: `staffName`, `dateFrom`, `dateTo`, `timeFrom`, `timeTo`.
  3. The app executes this filter directly against Room SQLite database and returns verified records.
- **Resilient Fallback**: If network is unavailable or Groq is unreachable, the query agent automatically switches to an offline heuristic parser so user queries never crash or fail silently.

### 2. API Key Configuration
The Groq API key is read at compile time from `local.properties` into `BuildConfig` and is **never committed to version control**:
```properties
# In local.properties (gitignored)
GROQ_API_KEY=gsk_your_groq_api_key_here
```
In `app/build.gradle.kts`:
```kotlin
buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
```

### 3. FastMCP Server Stub (`mcp_server/attendance_mcp.py`)
For external AI agent integrations (such as Anthropic Claude or custom MCP-compatible AI systems), a FastMCP server is provided under `mcp_server/attendance_mcp.py`.
- **Tools exposed**:
  - `get_staff()`: Retrieves registered staff members.
  - `get_attendance(staff_id, date)`: Retrieves attendance records.
  - `query_attendance_by_filter(staff_id, date_from, date_to, time_from, time_to)`: Runs combinable filters matching the Room SQLite data model.
- Run using:
  ```bash
  cd mcp_server
  pip install fastmcp
  python attendance_mcp.py
  ```

---

## 🛠️ Architecture & Tech Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language & UI** | Kotlin 1.9.24 + Jetpack Compose (Material 3) | Declarative reactive UI with StateFlow and Navigation Compose. |
| **Camera** | AndroidX CameraX (`1.3.3`) | Lifecycle-aware front-facing camera selfie capture. |
| **Face Detection** | Google ML Kit Face Detection (`16.1.6`) | Fast bounding-box detection, face centering, and validation. |
| **Face Recognition** | TensorFlow Lite (`2.14.0`) + MobileFaceNet | 192-d L2-normalized face embeddings compared via 1:N Cosine Similarity ($\ge 0.70$). |
| **Database** | AndroidX Room (`2.6.1`) | Local SQLite persistence with schema migrations. |
| **Geolocation** | Google Play Services Location (`21.2.0`) | GPS coordinates (`FusedLocationProviderClient`) + Geocoder address lookup. |
| **AI Layer** | Groq Cloud (`llama-3.3-70b-versatile`) + OkHttp | Natural-language query translation and automated daily executive summaries. |
| **Image Loading** | Coil Compose (`2.6.0`) | High-performance image loading for selfie thumbnails and enrolled photos. |

---

## 🚀 Installation & Running

### Option A: Install Pre-built APK via ADB (Quickest)
Connect your Android phone via USB with USB Debugging enabled, then run:
```bash
adb install -r AttendanceApp.apk
```
Launch the app:
```bash
adb shell am start -n com.attendance.app/.MainActivity
```

### Option B: Build from Source
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
