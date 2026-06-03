# Screen Lock Button — Samsung Note 9 (Exynos)
A floating power/screen lock button for when your physical button is broken.

---

## HOW TO BUILD & INSTALL

### Option A — Android Studio (Recommended, Free)

1. Download & install **Android Studio** from https://developer.android.com/studio
2. Extract this ZIP and open the `ScreenLockButton` folder in Android Studio
3. Wait for Gradle sync to finish
4. Connect your Note 9 via USB, enable **USB Debugging**:
   - Settings → About Phone → tap "Build Number" 7 times
   - Settings → Developer Options → USB Debugging → ON
5. Click the ▶ **Run** button in Android Studio
6. The app installs directly on your phone!

### Option B — Build APK manually

1. Open terminal inside the `ScreenLockButton` folder
2. Run: `./gradlew assembleDebug`
3. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`
4. Transfer to your phone and install (enable "Install from unknown sources")

---

## FIRST-TIME SETUP (In the App)

Once installed, open the app and follow these 3 steps:

**Step 1 — Grant Overlay Permission**
- Tap "Grant Overlay Permission"
- Find "Screen Lock Button" in the list → toggle ON
- Go back to the app

**Step 2 — Grant Device Admin**
- Tap "Grant Device Admin"
- Read the prompt → tap "Activate"
- This allows the button to lock your screen

**Step 3 — Start the Button**
- Tap "START Floating Button"
- A ⏻ floating button appears on your screen!

---

## USING THE BUTTON

- **Tap** the floating ⏻ button → locks your screen instantly
- **Drag** the button → moves it anywhere on screen
- Button **survives app switching** and stays on top always
- Button **auto-restarts** after reboot (no need to manually start again)
- To stop: open the app → tap "STOP Floating Button"

---

## TROUBLESHOOTING

| Problem | Fix |
|---|---|
| Button doesn't lock screen | Re-grant Device Admin permission |
| Button disappears | Open app → tap START again |
| Can't install APK | Settings → Apps → Special Access → Install Unknown Apps → enable for your browser/Files app |
| "Overlay permission" not sticking | Go to Settings → Apps → Screen Lock Button → Permissions → Display over other apps → ON |

---

## PERMISSIONS EXPLAINED

| Permission | Why Needed |
|---|---|
| `SYSTEM_ALERT_WINDOW` | To show the floating button on top of all apps |
| `DEVICE_ADMIN` | To actually lock the screen (Android requirement) |
| `FOREGROUND_SERVICE` | To keep the button running while you use other apps |
| `RECEIVE_BOOT_COMPLETED` | To auto-start the button after phone restarts |

---

Built specifically for Samsung Galaxy Note 9 (Exynos) running Android 8.0+
