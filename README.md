# 📱 HabitWidget

A premium, high-performance, and ultra-energy-efficient Android home screen widget and setup companion designed to help you balance digital wellness and physical habits. Specifically optimized for **iQOO Z9x running Origin OS**.

This repository is fully configured with a **GitHub Actions CI build pipeline** to automatically compile, optimize, and distribute testing APKs.

---

## ✨ Core Features

* **🔋 0% Foreground/Background Battery Drain:** The app utilizes an **on-demand query architecture**—meaning no persistent background services or ongoing notification pills are run. Telemetry is updated strictly when the home widget refreshes or you open the setup shell.
* **🚶 Hardware Step Integration:** Reads steps on-demand using Android's hardware `Sensor.TYPE_STEP_COUNTER` and automatically handles system reboots by establishing a base reference offset.
* **📱 Digital Wellbeing Pacing:** Fetches Instagram screen time directly from `UsageStatsManager` (Midnight to current moment).
* **💪 Keyboard-Less Incremental Logs:** Log your reps instantly using interactive `+` and `-` buttons on the widget or setup screen. Button steps are dynamically adjusted based on today's workout task!
* **🔥 Motivational Streak Badges:** Displays your current active streak using a high-contrast Neon Orange-Red gradient badge on your home widget featuring a motivational `🔥 X Days` badge.
* **⏳ WorkManager Alerts:** A low-impact periodic WorkManager monitor runs every 15 minutes to check Instagram screen time and pushes standard notification reminders once a day when you cross **80%**, **90%**, and **100%** limits.

---

## 📅 Weekly Workout Schedule & Steps

Today's workout targets are statically mapped to the day of the week:

| Day | Workout Task | Target | Increment Step |
| :--- | :--- | :---: | :---: |
| **Monday** | Pushups | 50 | `+1` / `-1` |
| **Tuesday** | Squats | 100 | `+2` / `-2` |
| **Wednesday** | Plank | 240 seconds | `+10` / `-10` seconds |
| **Thursday** | Pushups | 50 | `+1` / `-1` |
| **Friday** | Lunges | 100 (50 per leg) | `+1` / `-1` |
| **Saturday** | Pull-ups | 40 | `+1` / `-1` |
| **Sunday** | Plank | 240 seconds | `+10` / `-10` seconds |

---

## ⚡ The Perfect Day: Streak Rules

Your motivational streak increases for consecutive days where **all three** of these rules are fully satisfied:
1. **Active Steps:** $\ge 10,000$ steps.
2. **Instagram Usage:** $< 2$ hours (7,200 seconds).
3. **Workout Completed:** $\ge 80\%$ of today's target reps/seconds (e.g. 80+ squats, 40+ pushups, 192s+ plank).

---

## 🛠️ Folder Structure

* `app/src/main/java/com/abi/widgettracker/`
  * `MainActivity.kt`: Sleek Jetpack Compose dashboard, permissions helper, and manual rep logger.
  * `data/DatabaseHelper.kt`: Local SQLite database manager pre-populated with your static routines and streak logic.
  * `utils/StepTrackerHelper.kt`: Hardware steps query telemetry.
  * `utils/UsageStatsHelper.kt`: Deep-link settings navigator and `UsageStatsManager` queries.
  * `utils/InstagramMonitorWorker.kt`: Periodic WorkManager screen time alerts.
  * `widget/TrackerWidgetProvider.kt`: Programmatic AppWidgetProvider to refresh layout remote views and intercept click actions.
* `app/src/main/res/`
  * `layout/widget_layout.xml`: Dark glassmorphic widget interface.
  * `xml/widget_info.xml`: Homescreen widget configuration parameters.
  * `drawable/`: Rounded widget shapes and gradients.
* `.github/workflows/android.yml`: GitHub Actions build pipeline.

---

## 🚀 How to Build, Install, and Run

Since local Java/Android SDK compilation is not required, the building process runs entirely on GitHub:

### 1. Push to your GitHub Repository
Commit all files in your workspace and push them to your repository:
```bash
git add .
git commit -m "feat: Add full HabitWidget codebase and CI pipeline"
git push origin main
```

### 2. Download compiled APK
1. Navigate to your repository page on GitHub.
2. Click on the **Actions** tab at the top.
3. Select the active workflow run **"Compile and Package HabitWidget"** triggered by your push.
4. Once completed, scroll to the **Artifacts** section at the bottom and download the **`HabitWidget-Test-APK`** zip folder.
5. Extract the zip folder to retrieve `app-debug.apk`.

### 3. Deploy on your iQOO Z9x
1. Transfer the `app-debug.apk` to your phone and install it.
2. Launch the app once to grant permissions:
   * **Steps Sensor:** Click "Grant" to authorize physical activity recognition.
   * **Instagram Access:** Click "Authorize" to redirect to vivo's system settings. Toggle "Usage Access" to **Active** for HabitWidget.
3. Long-press your home screen launcher (or pinch-in), open your widget selector, find **HabitWidget**, drag it onto your screen, and enjoy tracking!
