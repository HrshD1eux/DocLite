# DocLite - Production & Store Publishing Checklist (TODO)

> [!CAUTION]
> **CRITICAL FOR GOOGLE PLAY STORE & F-DROID PUBLICATION**
> Failure to complete these items before uploading an APK / AAB to Google Play Console will result in **instant app rejection, policy strikes, or developer account suspension** under Google Play Device & Network Abuse and Malware policies.

---

## 🚨 Pre-Publish Checklist (Must Complete Before Submitting to Play Store)

### 1. Remove `REQUEST_INSTALL_PACKAGES` from AndroidManifest.xml
* **File**: `app/src/main/AndroidManifest.xml`
* **Action**: Delete the following line completely:
  ```xml
  <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
  ```
* **Why**: Google Play strictly bans apps from requesting package installation permissions unless the app is an enterprise device manager or an authorized app store (like F-Droid). An office suite or document viewer requesting this permission triggers automated rejection and malware classification.

---

### 2. Switch In-App APK Downloader to External Browser Link
* **Files**: 
  - `app/src/main/java/com/HrshD1eux/DocLite/core/update/UpdateManager.kt`
  - `app/src/main/java/com/HrshD1eux/DocLite/ui/screens/settings/SettingsViewModel.kt`
  - `app/src/main/java/com/HrshD1eux/DocLite/ui/screens/settings/SettingsScreen.kt`
* **Action**: 
  - Disable or remove the direct APK binary download and package installer execution (`installApk`).
  - Instead, when a new version is detected, provide a button that opens the update URL in the user's browser:
    ```kotlin
    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://github.com/HrshD1eux/DocLite/releases/latest")
    )
    context.startActivity(browserIntent)
    ```
  - For Google Play distribution, replace with the official **Google Play In-App Updates API** (`com.google.android.play:app-update-ktx`).
* **Why**: Google Play Developer Distribution Agreement (DDA) Section 4.5 prohibits apps from updating themselves outside of Google Play's update pipeline. Sideloading `.apk` files inside the app is flagged as Trojan Downloader behavior by Play Protect and security engines.

---

### 3. Google Play "All Files Access" (`MANAGE_EXTERNAL_STORAGE`) Declaration
* **File**: `app/src/main/AndroidManifest.xml`
* **Action**: 
  - Submit the Google Play Declaration Form for "All files access" (`MANAGE_EXTERNAL_STORAGE`).
  - Provide a clear video demonstration showing DocLite searching, opening, and saving `.docx`, `.xlsx`, `.pptx`, `.pdf`, and `.csv` files across device directories.
* **Why**: Google Play gates `MANAGE_EXTERNAL_STORAGE` exclusively for file managers, backup tools, and document management applications.

---

### 4. Release Signing & Credentials
* **Files**: `local.properties`, `*.jks`
* **Status**: Keystores are strictly excluded via `.gitignore`.
* **Action**: Never commit signing keys, store passwords, or key alias credentials to git history. For CI/CD builds, inject them through GitHub Actions Secrets (`RELEASE_STORE_FILE_BASE64`, `RELEASE_STORE_PASSWORD`, etc.).

---

### 5. Final Release Build & Verification
* Run tests:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* Generate release bundle (AAB for Google Play, or APK for direct distribution):
  ```bash
  ./gradlew bundleRelease
  ./gradlew assembleRelease
  ```
