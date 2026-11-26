# Dostt Audio Auto-Dialer (Android App)

An Android helper app built specifically for the **`live.friend.dostt`** flow shown in your screen recording:

1. **Home Screen** (`Find me the right dostt` bar or expanded `Audio 1/min` button) → taps **Audio Call**
2. **Matching Screen** (`Finding a dostt for you...`) → waits passively
3. **Unavailable Screen** (`Dostts unavailable` / `Go back home`) → taps **Go back home** and retries Audio Call after your configured delay
4. **Call Connects** → stops immediately so it never interferes with your call or spends extra coins

## Banking Apps & Background Safety

- **Package-scoped:** `accessibility_service_config.xml` sets `android:packageNames="live.friend.dostt"`, so Android never sends events from banking apps, UPI apps, or other apps to this service.
- **No background processes:** The service has zero internet, SMS, contacts, or `BOOT_COMPLETED` permissions, no wake-locks, and hides its floating pill as soon as you leave `live.friend.dostt`.
- **Strict banking/UPI apps:** Some Indian banking apps check Android's global `ENABLED_ACCESSIBILITY_SERVICES` setting even when a service is package-scoped. Use the **Banking Mode: Stop & Open Accessibility Settings** button in the app to toggle the service off in one tap before opening banking apps.

## How to Build the APK

Because Java / Android SDK are not installed on this Windows machine, build the APK using **Android Studio**:

1. Install **Android Studio** (includes JDK 17, Gradle, and Android SDK 34).
2. Open the folder `d:\server\htdocs\dostt\android-dostt-autodialer` in Android Studio and let Gradle sync.
3. Run unit tests: `./gradlew test` (or right-click `DialerStateMachineTest.kt` → **Run**).
4. Build APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**, then install the generated `app-debug.apk` on your Android phone.

## How to Use on Your Phone (Android 13+)

1. Install `Dostt Auto-Dialer` on your Android phone.
2. Open **Settings → Apps → Dostt Auto-Dialer → ⋮ (top right) → Allow restricted settings** (required by Android 13+ for sideloaded Accessibility apps).
3. Open **Dostt Auto-Dialer**, tap **Enable / Manage in Accessibility Settings**, and turn on **Dostt Audio Auto-Dialer**.
4. Tap **Start Auto-Dialer & Open Dostt** (or use the floating **Start Auto-Dial / Stop** pill at the top of Dostt).
