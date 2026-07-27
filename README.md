<div align="center">

# DocLite
**The Ultimate Offline Office Suite for Android**

![Android Minimum API](https://img.shields.io/badge/Minimum%20API-24%20(Nougat)-brightgreen)
![Target API](https://img.shields.io/badge/Target%20API-36-blue)
![Offline First](https://img.shields.io/badge/Privacy-100%25%20Offline-success)
![License](https://img.shields.io/badge/License-MIT-orange)

</div>

---

## 📌 Overview
**DocLite** is a fast, lightweight, and fully offline office productivity suite built natively for Android. Designed with privacy and performance at its core, DocLite allows users to read, edit, and analyze various document formats without ever requiring an internet connection or compromising their sensitive data.

Whether you are drafting a Word document, crunching numbers in Excel, presenting a PowerPoint, or performing complex financial analytics on your bank statements, DocLite handles it entirely on-device using industry-standard, robust parsing engines.

## 🚀 Key Features

### 📄 Document Editors & Viewers
* **Word Processing (`.docx`)**: Seamlessly read and edit documents with full support for text styling (bold, italics, underline), paragraph alignment, and color formatting.
* **Spreadsheets (`.xlsx`, `.csv`)**: View complex spreadsheets, inspect cell values, and evaluate formulas instantly.
* **Presentations (`.pptx`)**: Review slides, read text boxes, and edit presentation content on the go.
* **PDF Reader (`.pdf`)**: Native, high-fidelity PDF rendering with built-in, deep-text search capabilities.

### 🏦 Intelligent Bank Statement Analyzer
A powerful financial tool built right into the app. Import your `.xlsx` or `.csv` bank statements, and DocLite will automatically:
* **Identify transaction structures** (Date, Description, Debit, Credit, Balance).
* **Extract clean party names** by stripping out UPI/NEFT/IMPS transaction identifiers.
* **Generate a comprehensive analytics dashboard** showing total inflows, outflows, and top transaction parties.

### 🔒 100% Offline & Private
DocLite operates entirely offline. Your documents, bank statements, and private notes never leave your device. There is no telemetry, no tracking, and no cloud syncing. 

### 🔄 Built-in Auto-Updater
Stay up to date automatically. DocLite features a native, lightweight GitHub release updater that checks for the latest APK directly from the repository, downloads it securely, and seamlessly prompts the user for installation.

---

## 🛠️ Technology Stack
* **Language**: Kotlin 
* **UI Framework**: Jetpack Compose (Material Design 3)
* **Architecture**: MVVM with Coroutines & StateFlow
* **Engines**: 
  * [Apache POI](https://poi.apache.org/) - Robust parsing & writing for Office Open XML formats.
  * [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) - Native PDF text extraction.
  * [OpenCSV](https://opencsv.sourceforge.net/) - High-performance CSV parsing.
* **Networking (Updater Only)**: Standard HttpURLConnection / Android DownloadManager

---

## 💻 Build & Run Locally

### Prerequisites
* **[Android Studio](https://developer.android.com/studio)** (Koala Feature Drop or newer recommended)
* **JDK 17+**

### Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/HrshD1eux/DocLite.git
   ```
2. Open the project directory in Android Studio.
3. Allow Gradle to sync and download the required dependencies (such as Apache POI and PDFBox).
4. Click **Run 'app'** (`Shift + F10`) to deploy to your emulator or physical Android device.

> **Note on Signing**: The project is configured with a release signing key directly in the repository to ensure reproducible, easily installable release APKs.

---

## 🤝 Contributing
Contributions, issues, and feature requests are always welcome! 
Feel free to check the [issues page](https://github.com/HrshD1eux/DocLite/issues) if you want to contribute.

## 📝 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
