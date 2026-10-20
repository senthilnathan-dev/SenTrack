# Google Play listing and declarations (draft)

Text and answers to paste into Play Console. Check each answer against Play's wording on the day, since the forms change.

## Basics
| Field | Value |
|---|---|
| App name (max 30) | `SenTrack` |
| Short description (max 80) | `Private, encrypted tracker for your money, life, work and career.` |
| Category | Productivity |
| Tags | Productivity, Finance tracking, Notes, Habits, Planner |
| Contact email | libertariansenthil@gmail.com |
| Privacy policy URL | the public address of `docs/site/privacy-policy.html` |
| Price | Free, no ads, no in-app purchases |
| Target audience | 18 and over (avoids the Families programme) |
| Ads | No |

## Full description (max 4000)
SenTrack keeps the important parts of your life in one private place, on your phone. Money, personal goals and habits, your
job, and your career, all encrypted, all working without an internet connection.

PRIVATE BY DESIGN
- Everything is stored encrypted on your phone. No account, no ads, no analytics, no tracking.
- Locks itself, hides its screens from screenshots and the recent-apps view, and unlocks with your passphrase or fingerprint.
- Optional backup to a hidden area of your own Google Drive, encrypted before it leaves the phone.

FINANCE
- Accounts, spending, income, transfers and investments with real gains and losses.
- Dashboard with net worth, monthly trends and where the money went.
- Budgets, savings goals, repeating entries for salary, rent and SIPs, and tax-year folders for your documents.

PERSONAL
- Notes with formatting, folders, tags, attachments and a modern editor.
- Habits with streaks, plans and goals with steps, a wishlist and bucket list, achievements and important dates.
- A timeline of your own story.

WORK
- Punch in and out, with the place noted only when you tap and only if you allow it.
- Leave balances and calendar for each company, tasks, appreciation records with proof, and permissions.
- Changing jobs? Companies are kept separate, and leaving one keeps its history.

CAREER
- Learning, certificates with renewal reminders, job applications with interview reminders, resume versions.
- A workspace of the apps and sites you use, with joining and leaving checklists.

REMINDERS THAT WORK
- Reminders ring even while the app is locked, and show a generic message on the lock screen.

OPTIONAL AI HELP, OFF BY DEFAULT
- Run a small model on your own phone so nothing leaves it, or use your own key with a cloud service.
- Sparkle buttons fix, summarize, plan and explain. Choose Fast, Normal or Accurate. SenTrack checks answers for invented numbers.

Built for one person, on one phone, offline first. Not financial, tax, legal or employment advice.

## What's new text (max 500 characters)
Copy the newest entry of `docs/legal/changelog.txt`, shortened. For 0.5.0:
`First test build: finance with repeating entries, personal notes/habits/plans, work attendance and leave, career tools, optional on-device AI, and a user guide in the app.`

## Graphics to prepare
- App icon 512 x 512 PNG (render the adaptive icon: teal background `#0B3C49`, three white bars, amber spark).
- Feature graphic 1024 x 500.
- 2 to 8 phone screenshots. **Debug builds allow screenshots** (the release build blocks them on purpose), so take them from a
  debug install with sample data, never real data.

## App content declarations
| Form | Suggested answer |
|---|---|
| Content rating (IARC) | Answer no to violence, sexual content, language, controlled substances, gambling, user-to-user communication. Expect "Everyone". |
| News app / COVID / Government | No |
| Financial features | The app tracks the user's own money records; it provides no loans, payments, banking or trading. Choose the option matching personal finance tracking; if Play asks for a licence, none applies. |
| Health | No health data or features |
| Permissions: exact alarms | For reminders the user sets themselves (the app's core purpose) |
| Permissions: location | Foreground only, when the user taps Punch in/out, to note where they were; stored on the device |

## Data safety form
Play's form asks what is **collected** (sent off the device) and **shared**. SenTrack sends nothing to the developer.
Suggested answers, to confirm against the form's current definitions:

* Does the app collect or share any of the required user data types? **No data collected or shared with the developer.**
* Optional, user-started features that move data off the device, to mention if the form asks or if you prefer to be
  conservative (declare as optional, encrypted in transit, user can delete):
  * Google Drive backup: an already-encrypted file goes to the user's own Drive.
  * Cloud AI with the user's own key: text the user confirms is sent to the provider they chose.
  * One-off AI model download from Hugging Face (no personal data sent).
* Is all data encrypted in transit? **Yes** (HTTPS only).
* Can users request deletion? **Yes**: they delete data in the app or uninstall; backups are in their own Drive.
* Security practices: data encrypted at rest on the device (AES-256, passphrase-derived key).

If in doubt, be conservative and over-declare the three optional features rather than answer "no data".

## Testing tracks
1. **Internal testing** (0.5.0): up to 100 testers by email, no review wait. Start here.
2. **Closed testing**: needed before production on newer personal accounts (12 testers, 14 days).
3. **Production** (1.0.0), then 1.x updates.
