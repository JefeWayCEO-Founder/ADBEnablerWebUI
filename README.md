# BrokeScreen ADB Authorize Project

This repository contains the complete source code for the "BrokeScreen ADB Authorize" project, which includes both the Android application and its companion Web User Interface (Web UI).

## Project Purpose

The "BrokeScreen ADB Authorize" project aims to assist users with broken Android device screens. It provides a way to remotely authorize ADB debugging access and input screen lock passwords/PINs using a web interface, without needing direct touch interaction on the device.

## Components

This repository is divided into two main components:

1.  **Android Application (`AndroidApp/`):** The core Android application that runs on your device. It provides an Accessibility Service to interact with the device UI (like tapping ADB dialogs, typing passwords) and hosts a local HTTP server to receive commands from the Web UI.
2.  **Web User Interface (`WebUI/`):** A client-side web application (HTML, CSS, JavaScript) that you can open in any web browser. It provides a graphical interface to send commands and data to the Android app over your local network.

## Usage

### For the Android Application:

1.  **Open in Android Studio:** Open the `AndroidApp/` directory in Android Studio.
2.  **Build and Install:** Build the debug or release APK and install it on your Android device.
3.  **Enable Accessibility Service:** On your device, navigate to Accessibility Settings and enable the "BrokeScreen ADB Authorize" service.
4.  **Note Device IP:** Launch the app and note the local IP address displayed (e.g., `192.168.1.100:8080`).

### For the Web User Interface:

1.  **Open `index.html`:** Open the `WebUI/index.html` file from this repository in any modern web browser on a device connected to the *same local network* as your Android phone.
2.  **Configure & Control:** Enter the Android device's IP address (from step 4 above) and a shared secret key (which must also be set in the Android app via the Web UI). Use the provided interface to send commands or password data.

## Important Licensing Information

This source code, including both the Android application and the Web UI, is made available for **open use** under specific terms.

**You are permitted to use and distribute this source code, provided that:**

1.  **No modifications are made to the source code.** The code must be used and distributed exactly as provided in this repository.
2.  **Attribution is maintained.** Any application, service, or derivative work that incorporates or is based on this source code must clearly state that the original source code originated from:
    **Earl William Inscore Jr.**

These terms are formally governed by the **Creative Commons Attribution-NoDerivatives 4.0 International Public License (CC BY-ND 4.0)**. A copy of the full license text is available in the `LICENSE` file within this repository.
