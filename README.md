# SentinelShield Android App

A custom-built Android antivirus and security application designed for personal device protection. Built with native Kotlin and Jetpack Compose, featuring a Black & Gold theme.

## 🎨 UI Preview

**Interactive Mockup:** [View SentinelShield Interface Preview](https://3000-i1nsoh3iwn5cm1bylbwqm-bd7eb8a9.us1.manus.computer)

The mockup showcases the Black & Gold theme (light and dark modes), all 5 main screens, and the bottom navigation system.

## Project Structure

```
app/src/main/java/com/sentinelshield/
├── MainActivity.kt              # Main entry point
├── SentinelShieldApp.kt         # Application class (initializes services)
├── data/
│   ├── database/                # SQLite threat database
│   └── models/                  # Data models
├── navigation/                  # Navigation graph & routes
├── notifications/               # Smart alert system
├── screens/                     # UI screens (Compose)
├── services/
│   ├── ai/                      # TFLite AI threat engine (Phase 4)
│   ├── behavioral/              # Statistical behavioral analysis
│   ├── network/                 # VPN-based network monitor
│   ├── overlay/                 # Clickjacking detection
│   ├── protection/              # Real-time protection service
│   ├── scanner/                 # Malware, phishing, link scanning
│   └── updater/                 # Auto-update threat feeds
├── ui/theme/                    # Black & Gold Material3 theme
└── viewmodels/                  # ViewModels for all screens
```

## Features

### Phase 1 - Foundation
- Black and Gold theme (Material Design 3, light/dark modes)
- Dashboard, Scan Results, Permission Auditor, Network Monitor, Settings screens
- Bottom navigation system
- Shield app icon

### Phase 2 - Core Security Engine
- **Malware Scanner**: Hash-based detection of installed apps against threat database
- **Permission Auditor**: Risk scoring (0-100), dangerous permission detection
- **Clickjacking/Overlay Detection**: AccessibilityService-based overlay monitoring
- **Network Monitor**: VPN-based traffic inspection, flags malicious IP connections
- **Phishing Scanner**: URL checking against local DB + pattern matching
- **Real-time Protection**: Auto-scans newly installed/updated apps (foreground service)
- **Boot Receiver**: Auto-restarts protection on device boot

### Phase 3 - Intelligence & Automation
- **Threat Database Auto-Update**: Pulls from MalwareBazaar, Abuse.ch URLhaus, Feodo Tracker every 6 hours via WorkManager
- **AI Behavioral Anomaly Detection**: Monitors app behavior patterns (data usage, background activity, network patterns) and flags deviations using statistical analysis (Welford's algorithm)
- **Enhanced Phishing Protection (PhishingGuard)**: Homograph attack detection, typosquatting detection (Levenshtein distance), suspicious TLD flagging, URL pattern analysis, IP-based URL detection
- **Real-time Link Interception**: AccessibilityService monitors browser URL bars and checks links before they load
- **Smart Notification System**: Categorized alerts (threats, scans, updates, behavioral) with actionable notifications

### Phase 4 - AI Threat Engine
- **TensorFlow Lite Behavioral Classifier**: ML-based app behavior classification (normal/suspicious/malicious)
- **Behavior Data Collector**: Real-time collection of app metrics (network activity, background wakeups, data transmission, battery drain, CPU usage, sensor access, IPC frequency)
- **AI Threat Engine**: Orchestrates all AI detection with trend analysis, confidence scoring, and smart alerting
- **Heuristic Fallback**: When TFLite model is unavailable, uses weighted heuristic classification
- **Periodic AI Scans**: WorkManager-scheduled deep scans every 8 hours
- **Context-Aware Permission Scoring**: Improved permission auditor that considers app category (communication, media, navigation, finance) for realistic risk assessment

## Building the APK

### Via GitHub Actions (Recommended)
1. Push to `main` branch triggers automatic build
2. Go to Actions tab > latest run > Artifacts
3. Download `SentinelShield-debug` APK

### Via Android Studio
1. Clone the repo
2. Open in Android Studio
3. Build > Build Bundle(s) / APK(s) > Build APK(s)

## Installation

1. Transfer APK to your Android device
2. Enable "Install from unknown sources" for your file manager
3. Tap the APK to install
4. Grant requested permissions for full protection
5. Enable Accessibility Services for overlay & phishing protection

## Permissions Used

| Permission | Purpose |
|-----------|---------|
| INTERNET | Threat feed updates |
| ACCESS_NETWORK_STATE | Network monitoring |
| QUERY_ALL_PACKAGES | App scanning |
| FOREGROUND_SERVICE | Real-time protection |
| POST_NOTIFICATIONS | Security alerts |
| RECEIVE_BOOT_COMPLETED | Auto-start protection |
| PACKAGE_USAGE_STATS | Behavioral analysis |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM with ViewModels
- **AI/ML**: TensorFlow Lite (behavioral classification)
- **Background Tasks**: WorkManager (periodic updates, analysis & AI scans)
- **Database**: SQLite (threat signatures)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Future Development

- Phase 5: Anti-theft features (remote lock/wipe)
- Phase 6: Web protection (safe browsing proxy)
- Phase 7: Privacy advisor & app privacy scoring
