# SafeMotion — Privacy Policy

**Effective date:** 2026-05-16
**App:** SafeMotion (Android)
**Developer:** Rehmat Singh Chawla
**Contact:** <TODO: your contact email>

---

## 1. Summary

SafeMotion is an on-device fall-detection app. We designed it so that **none of your personal information ever leaves your phone**, except for one specific case: when SafeMotion believes you have fallen, it sends a single SMS message to the caretaker contact **you configured**. Nothing is uploaded to any server. We do not collect analytics. We do not share data with third parties. We do not sell anything.

---

## 2. Information SafeMotion accesses

### 2.1 Sensor data (accelerometer, gyroscope)
- **Purpose:** Detect possible falls using an on-device machine-learning model.
- **Storage:** Sensor data is processed in memory in 2-second windows and discarded immediately afterward. **Nothing is written to disk and nothing is uploaded.**
- **Where it goes:** Nowhere outside your phone.

### 2.2 Location (last known location)
- **Purpose:** When (and only when) a fall alert is sent, your most recent known location is included in the SMS so your caretaker knows where you are.
- **Storage:** Not stored. Read once at alert time.
- **Where it goes:** Only into the SMS sent to the caretaker phone number you configured. Not to us, not to any third party.
- **Background access:** SafeMotion uses background location only to be able to read the last-known location when an alert fires while the screen is off.

### 2.3 SMS sending
- **Purpose:** Deliver the fall alert to the caretaker.
- **What is sent:** A short text message containing your name, your phone number, and a Google Maps link to your last known location.
- **Recipient:** Only the caretaker phone number you set up in the wizard. SafeMotion never sends SMS to anyone else.
- **Frequency:** At most one alert message per detected fall. A 3-minute cooldown prevents repeated firing.

### 2.4 Profile information you enter
SafeMotion stores the following on your phone, in the app's private storage (`SharedPreferences`):
- Your name (for inclusion in the alert SMS)
- Your phone number
- Caretaker name
- Caretaker phone number

This information is **not transmitted off the device by SafeMotion at any time**. It only appears in the SMS the app sends when a fall is detected.

### 2.5 Notifications
SafeMotion uses the Android notification system to keep the foreground monitoring service running (a persistent notification while monitoring is on) and to alert you to a possible detected fall. Notifications stay on the device.

---

## 3. Information SafeMotion does NOT collect

- We do **not** have a server. We have no infrastructure that could receive your data.
- We do **not** use any analytics SDK (no Firebase Analytics, no Mixpanel, no Crashlytics, no Google Analytics).
- We do **not** use advertising IDs or run ads.
- We do **not** track you across other apps or websites.
- We do **not** collect contacts (other than the one caretaker you explicitly enter).
- We do **not** collect health, biometric, audio, or video data.
- We do **not** read your SMS inbox; the `SEND_SMS` permission is only used to compose and send the alert.

---

## 4. Permissions and why they are required

| Permission | Purpose |
|---|---|
| `BODY_SENSORS`, `HIGH_SAMPLING_RATE_SENSORS` | Read accelerometer + gyroscope at 50 Hz for fall detection |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `FOREGROUND_SERVICE_LOCATION` | Run the monitoring service in the foreground while you have monitoring on |
| `WAKE_LOCK` | Keep sensors active while the phone screen is off |
| `POST_NOTIFICATIONS` | Show the persistent monitoring notification and the fall-pending alert |
| `VIBRATE` | Vibrate the phone when a possible fall is detected |
| `SEND_SMS` | Send the fall alert to your configured caretaker number |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Include last-known location in the alert SMS |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after your phone reboots, if you had monitoring on |

---

## 5. Children's privacy

SafeMotion is intended for adults and caretakers of older adults. It is not directed at children under 13. We do not knowingly collect any data, and certainly nothing from children.

---

## 6. Security

Because all data stays on your device, the security of your information depends on the security of your phone. We strongly recommend:
- Using a screen lock (PIN, pattern, biometric)
- Keeping your phone's OS up to date
- Not granting SafeMotion permissions if you are uncomfortable with them — note that the app will not function without them

If you give your phone (or a backup) to someone else, they may be able to read the caretaker contact you configured in the app. The app does not include this in any cloud backup (`allowBackup` is disabled).

---

## 7. Third-party libraries

SafeMotion uses the following libraries on-device only. None of them transmit data to a network in the way SafeMotion uses them:

- **TensorFlow Lite** (Google) — runs the fall-detection model offline.
- **AndroidX / Material Components / Kotlin Coroutines** (Google / JetBrains) — UI and standard libraries.

---

## 8. Disclaimer

**SafeMotion is not a medical device.** It is a best-effort assistive tool. It will sometimes fail to detect a real fall, and it will sometimes raise an alert when no fall has occurred. You should not rely on SafeMotion as your sole means of emergency response. Always have a backup plan.

---

## 9. Changes to this policy

If we change this policy in a way that affects what data the app handles, we will publish the new version at the same URL and update the **Effective date** above. Your continued use of the app after a change indicates acceptance.

---

## 10. Contact

If you have a question about this policy, email **<TODO: your contact email>**.
