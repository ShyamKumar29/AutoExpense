# ZORS – AI Powered Personal Finance Assistant

> An intelligent Android application that automatically tracks expenses and income from payment notifications, manages budgets, detects subscriptions, and provides insightful financial analytics.

---

## Overview

ZORS is a modern Android personal finance application designed to simplify expense tracking without requiring manual entry.

The application automatically detects payment notifications, extracts transaction details, categorizes expenses, tracks budgets, identifies recurring bills, and provides users with meaningful financial insights—all while keeping data stored locally on the device.

---

## Features

- Automatic Expense Detection
- Automatic Income Detection
- Dashboard with Financial Summary
- Budget Management
- Bills & Subscription Tracking
- Transaction History
- Category-wise Analytics
- Export Transactions (PDF & CSV)
- Backup & Restore
- Local Offline Storage
- Modern Material Design UI

---

## Tech Stack

### Android

- Kotlin
- Jetpack Compose
- MVVM Architecture
- Repository Pattern
- Room Database
- Android Notification Listener
- Material Design 3

### Storage

- Room Database
- Local Backup & Restore

### Architecture

- MVVM
- Repository Pattern
- StateFlow
- Compose Navigation

---

# Project Architecture

```
Notification Listener
        │
        ▼
Payment Parser
        │
        ▼
FinancialTransaction
        │
 ┌──────┴─────────┐
 ▼                ▼
Room Database   Repository
        │
        ▼
     ViewModel
        │
        ▼
 Jetpack Compose UI
```

---

# Project Structure

```
app/
 ├── ui/
 ├── data/
 ├── database/
 ├── models/
 ├── repository/
 ├── notifications/
 ├── backup/
 ├── export/
 ├── analytics/
 └── budgets/
```

---

# How to Run

## Requirements

- Android Studio
- Android SDK 35+
- Kotlin

## Steps

1. Clone the repository

```bash
git clone https://github.com/ShyamKumar29/Zors.git
```

2. Open in Android Studio

3. Sync Gradle

4. Run on an Android device

5. Grant:

- Notification Access
- Storage Permission (if required)

---

# AI Development

This project was developed with the assistance of **OpenAI Codex** and **GPT-5.5** throughout the development lifecycle.

## How Codex was used

OpenAI Codex was primarily used for implementation tasks, including:

- Building Android features
- Refactoring Kotlin code
- Improving project architecture
- Fixing bugs
- Optimizing performance
- Code reviews
- UI refinements
- Debugging Gradle issues
- Improving notification handling
- Repository cleanup before release

Most production code changes were generated, reviewed, and iteratively refined using Codex.

---

## How GPT-5.6 was used

GPT-5.6 served as the planning and engineering assistant throughout development.

It was used for:

- System architecture design
- Feature planning
- Application flow design
- MVVM guidance
- Database design discussions
- Security recommendations
- UI/UX improvements
- Documentation
- README preparation
- Demo planning
- Hackathon submission preparation
- Technical explanations

GPT-5.6 was also used to validate implementation decisions before they were executed in Codex.

---

# Privacy

- All financial data remains on the user's device.
- No personal financial information is uploaded to external servers.
- Backup files remain under the user's control.

---

# Future Improvements

- Cloud synchronization
- Multi-device support
- AI spending insights
- OCR receipt scanning
- Voice-based expense logging
- Smart recurring payment prediction

---

# Author

**Shyam Kumar J**

GitHub:
https://github.com/ShyamKumar29

---

# License

MIT License
