# SentinelShield Android App

This repository contains the initial codebase for the SentinelShield Android antivirus application, developed using native Kotlin and Jetpack Compose.

## 🎨 UI Preview

**Interactive Mockup:** [View SentinelShield Interface Preview](https://3000-i1nsoh3iwn5cm1bylbwqm-bd7eb8a9.us1.manus.computer)

The mockup showcases the Black & Gold theme (light and dark modes), all 5 main screens, and the bottom navigation system.

## Project Structure

The project follows a standard Android project structure with a focus on modularity and clean architecture principles. Key directories include:

- `app/`: Contains the main application module.
  - `src/main/java/com/sentinelshield/`: Main source code for the application.
    - `navigation/`: Defines navigation routes and the navigation graph.
    - `screens/`: Contains individual Composable screens for the app (Dashboard, Scan Results, etc.).
    - `ui/theme/`: Defines the app's theme, including colors and typography.
  - `src/main/res/`: Android resources (layouts, drawables, values, etc.).

## Features Implemented (Phase 1)

- **Project Structure**: Basic Android project setup with Gradle configuration.
- **Theme System**: Black and Gold color scheme implemented using Material Design 3 for both light and dark modes.
- **Core UI**: Placeholder UI for the following screens:
    - Dashboard
    - Scan Results
    - Permission Auditor
    - Network Monitor
    - Settings
- **Navigation**: Bottom navigation bar for switching between the main screens.
- **App Icon Concept**: A placeholder shield icon drawable is included.

## Setup Instructions

To set up and run the project locally, follow these steps:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/Ifeanyiuche98/SentinelShield.git
    cd SentinelShield
    ```

2.  **Open in Android Studio:**
    Open the `SentinelShield` project directory in Android Studio (Jellyfish | 2023.3.1 or newer recommended).

3.  **Sync Gradle:**
    Android Studio should automatically prompt you to sync Gradle. If not, manually sync the project with Gradle files by clicking `File > Sync Project with Gradle Files`.

4.  **Run the app:**
    Select an emulator or a physical device and run the application. The app should build and launch, displaying the Dashboard screen with the implemented theme and navigation.

## Future Development

Future phases will include:

- Malware scanning
- Permission auditing functionality
- Clickjacking protection
- Phishing URL scanning
- Network traffic monitoring
- Real-time protection
- Overlay attack detection
