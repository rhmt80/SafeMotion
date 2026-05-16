# SafeMotion — Play Console submission notes

This file contains everything you'll be asked to paste into the Play Console: permission justifications, data-safety form answers, store-listing copy, and the order in which to fill them out.

---

## 1. App content questionnaire (Policy → App content)

| Question | Answer |
|---|---|
| Privacy policy URL | `https://<your-github-user>.github.io/<repo>/` (the `PRIVACY_POLICY.md` rendered) |
| Ads | **No** |
| App access | All functionality available without restricted access (no login wall) |
| Content rating | Everyone (the questionnaire takes ~5 min — answer "no" to violence/sex/drugs etc.) |
| Target audience | Adults (18+) — explain in the field that the app is intended for older adults and their caretakers |
| News app | No |
| COVID-19 contact tracing | No |
| Data safety | (see Section 4) |
| Government app | No |
| Financial features | No |

---

## 2. Sensitive permission declarations (Policy → App content → Sensitive app permissions)

### 2.1 SMS permission (`SEND_SMS`)

> **Use case category:** Default SMS handler — **No**, select "Other" → "Sending SMS only when triggered by an emergency event the user opted into."

**Justification text (paste verbatim):**

> SafeMotion is an on-device fall-detection app for older adults. When the app's machine-learning pipeline detects a possible fall, the app sends a single SMS message to the caretaker phone number that the user explicitly configured during setup. The SMS contains the user's name, their phone number, and a Google Maps link to their last-known location.
>
> SMS is the alert delivery mechanism because (a) it works without an internet connection, which is important for elderly users who may not have stable Wi-Fi, (b) caretakers are often not tech-savvy and reliably check SMS, and (c) it does not require the caretaker to install any companion app.
>
> The user explicitly enters the recipient phone number during the first-run setup wizard. SafeMotion never sends SMS to any number the user has not explicitly configured. There is at most one alert SMS per detected fall, with a 3-minute cooldown to prevent re-firing.
>
> Demo video URL: `<TODO: short Loom or YouTube unlisted video showing setup wizard + fall test → SMS sent>`

### 2.2 Background location

> **Why does your app need to access location in the background?**

**Justification text (paste verbatim):**

> SafeMotion runs as a foreground service that monitors the phone's accelerometer and gyroscope to detect falls. When a fall is detected, the app reads the phone's last-known location once and includes it as a Google Maps link in the SMS alert sent to the user's pre-configured caretaker. This requires background location access because falls can happen with the screen off, the app in the background, or the phone in a pocket — exactly the situations where foreground-only location is not available.
>
> The app does not log, store, or transmit location anywhere except inside the alert SMS that the user authorized by configuring a caretaker number.

### 2.3 Foreground service type

Declared as: **`health|location`** in `AndroidManifest.xml`.

> **Justification:**
> The service continuously samples motion sensors to monitor for falls — a personal health-and-safety use case. Location is used at alert time to attach the user's location to the outgoing SMS.

### 2.4 High sampling rate sensors (`HIGH_SAMPLING_RATE_SENSORS`)

> **Justification:**
> Fall detection requires reading the accelerometer at 50 Hz to capture the brief impact spike (typically 50–150 ms) that distinguishes a fall from ordinary motion. Lower sampling rates miss the impact and degrade detection.

---

## 3. Data safety form (App content → Data safety)

This is the form Play Store users see in the listing as "Data safety." Answer it precisely; mismatches between this form and your privacy policy get apps rejected.

### Does your app collect or share any of the required user data types?

**Answer: No, my app does not collect or share any of the required user data types.**

> Reasoning: SafeMotion processes sensor data and reads location only on-device, transiently. The only data that leaves the device is the SMS the user explicitly authorized by setting up a caretaker. SMS sent to a user-chosen contact is **not** "collection" or "sharing" under Play's definitions — it's a user-authorized communication.

### Does your app use the required data types only for service ephemeral processing?

**Answer: Yes** (sensor data, location).

### All data is encrypted in transit?

**Answer: Yes** (the only "in transit" data is the SMS, which goes over the carrier network — Play counts this as "yes" for our purposes).

### Do users have a way to request data deletion?

**Answer: Yes** — uninstalling the app clears all `SharedPreferences`. Document this on the privacy-policy page.

---

## 4. Store listing (Main store listing)

### App name
**SafeMotion**

### Short description (≤ 80 chars)
> On-device fall detection. Sends SMS alert with your location when you fall.

(76 chars — check)

### Full description (≤ 4000 chars)
```
SafeMotion is a fall-detection app for older adults and their caretakers. When you fall, SafeMotion automatically sends an SMS alert with your location to a caretaker you choose during setup.

WHY SAFEMOTION

• 100% on-device. Your motion data never leaves your phone.
• No subscription, no account, no ads.
• Works without internet — alerts go out by SMS.
• No companion app needed for the caretaker.

HOW IT WORKS

SafeMotion runs a small machine-learning model directly on your phone, monitoring the accelerometer and gyroscope. When the app detects an event that looks like a fall, it goes through four checks:

1. Impact peak — was there a strong impact?
2. Free-fall — was there a brief weightless moment before impact?
3. ML model — does the motion pattern match a fall?
4. Stillness — did the person stop moving afterward?

Only when all four checks pass does SafeMotion fire an alert. Even then, you get a 15-second countdown with a CANCEL button before the SMS is sent — so a stumble that doesn't need help won't bother your caretaker.

WHAT THE ALERT CONTAINS

• Your name
• Your phone number
• A Google Maps link to your last-known location

The alert goes ONLY to the caretaker number you set up in the wizard.

PRIVACY

• No data leaves your phone except the SMS you authorized.
• No analytics, no tracking, no ads.
• See the privacy policy linked in the listing.

DISCLAIMER

SafeMotion is not a medical device. It is a best-effort assistive tool. Always have a backup emergency plan.

WHAT YOU NEED

• Android 8.0 or newer
• Phone with accelerometer + gyroscope (almost all phones)
• A SIM card that can send SMS
```

### Tagline / promo text
> Falls happen. SafeMotion makes sure someone knows.

### App category
**Health & Fitness**

### Tags
fall detection, elderly care, safety, emergency alert, on-device AI, accelerometer, caretaker, SMS alert

### Contact details
| Field | Value |
|---|---|
| Email | `<TODO: your email>` |
| Phone | optional; recommended to leave blank |
| Website | link to your GitHub Pages or repo |

---

## 5. Graphic assets you need to upload

| Asset | Spec | Tip |
|---|---|---|
| App icon | 512 × 512 PNG (already in `mipmap-*`) | export the launcher icon at 512 |
| Feature graphic | 1024 × 500 JPG/PNG | hero artwork — could be your shield logo on the teal gradient |
| Phone screenshots | 4–8 images, 1080 × 1920 (or similar 16:9 / 9:16) | take from a real device: dashboard idle, dashboard monitoring, alert banner with countdown, setup wizard, disclaimer dialog |
| 7-inch tablet screenshots (optional) | 1024 × 1600 | skip if not targeting tablets |
| 10-inch tablet screenshots (optional) | 1920 × 1200 | skip |

Pro tip: use Android Studio's **Logcat → screenshot** button or `adb exec-out screencap -p > shot.png` to grab screenshots.

---

## 6. Order of operations checklist

Do these in order. Each one unblocks the next.

```
[ ] 1. Generate release keystore (one-time, back it up off-device)
[ ] 2. Fill in 3 <TODO> placeholders:
        - PRIVACY_POLICY.md  (contact email × 2)
        - strings.xml        (privacy_policy_url)
        - PLAY_STORE_SUBMISSION.md (this file: contact email + demo video URL)
[ ] 3. Publish PRIVACY_POLICY.md on GitHub Pages
[ ] 4. Run ./gradlew clean bundleRelease — produces signed app-release.aab
[ ] 5. Test the .aab on a physical device:
        adb install-multiple ... (use bundletool to extract APKs from .aab)
[ ] 6. Real fall + ADL field test (4-hour wear test, 30 fall sims)
[ ] 7. Tune threshold/cooldown if false-alert rate > 1/hr
[ ] 8. Take 4-8 screenshots, design feature graphic
[ ] 9. Record demo video for SMS-permission justification (1-2 min Loom)
[ ] 10. Create Play Console developer account ($25 one-time)
[ ] 11. Create the app entry, fill in store listing
[ ] 12. Fill data-safety form (Section 3)
[ ] 13. Fill app-content questionnaire (Section 1)
[ ] 14. Submit sensitive permission declarations (Section 2)
[ ] 15. Upload .aab + screenshots, request internal-test track first
[ ] 16. Once internal test passes review, promote to production
```

Realistic timeline:
- Steps 1-4: a few hours
- Steps 5-7: 2-3 days (real-world testing is the slow part)
- Steps 8-9: half a day
- Steps 10-15: half a day
- Step 16 review: 24-72 hours typical for a first submission with sensitive permissions

---

## 7. Common rejection reasons (avoid these)

| Reason | Fix |
|---|---|
| Privacy policy URL 404 or doesn't mention SMS | Make sure GH Pages is live and PRIVACY_POLICY.md is published |
| Data-safety form contradicts privacy policy | Both should agree: "no data collected for analytics, location used only inside the SMS the user authorized" |
| Background-location use case unclear | Use the exact text in Section 2.2 above; record a 30-second demo video showing alert fires when phone is in pocket |
| `SEND_SMS` use case doesn't match a permitted policy | The "Other" use case with the text in Section 2.1 is acceptable for fall-detection apps |
| Foreground service type incorrect | Manifest must have `foregroundServiceType="health\|location"` (already done) |
| 16 KB page-size warning on `libtensorflowlite_jni.so` | TFLite was bumped to 2.16+ (already done) |
| Targets old SDK | targetSdk = 36 (already done) |
| App crashes on launch in review device | Test the release AAB on a real device first; R8 + missing proguard rule for TFLite is the usual culprit |

