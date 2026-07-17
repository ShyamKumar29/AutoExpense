# AutoExpense SMS Permission Notes

Use this text as the basis for the Play Console SMS permissions declaration,
privacy policy, and store listing disclosure.

## Core Feature

AutoExpense is a money-management and budgeting app that helps users track
online payments they may otherwise forget. Payment detection is a core feature.

## Why SMS Access Is Requested

AutoExpense primarily detects payments from Android notifications from payment
apps, SMS apps, and Truecaller. Some devices or SMS apps do not post a visible
notification for every bank transaction SMS. Optional SMS access lets
AutoExpense detect bank debit/payment SMS messages directly on the device when
no notification is available.

Requested permissions:

- `READ_SMS`: scans recent bank transaction SMS messages after the user enables
  SMS fallback.
- `RECEIVE_SMS`: detects new bank transaction SMS messages as they arrive.

## Data Handling

SMS processing happens locally on the user's device. AutoExpense parses messages
only to identify outgoing payment/debit transactions. It stores only the parsed
transaction fields needed for expense tracking:

- amount
- merchant or recipient
- bank/source label
- transaction timestamp
- masked notification/SMS excerpt
- duplicate-detection fingerprint such as UTR/reference number when available

AutoExpense does not store full SMS bodies, upload SMS contents, sell SMS data,
use SMS data for advertising, or process unrelated personal SMS messages beyond
local rejection checks.

## User Control

SMS fallback is optional and is shown as a separate consent step in payment
detection setup. Users can deny or revoke SMS permission and continue using
notification-based payment detection.
