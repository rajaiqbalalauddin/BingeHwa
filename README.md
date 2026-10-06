# BingeHwa

An Android manhwa reader with no ads. It reads chapters from MangaDex, alerts you when a followed
series gets a new chapter, suggests new series from your taste, and lets you and your friends
rate, comment and share.

Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Firebase (Auth, Firestore, Cloud Messaging).

## Status: read this first

The code was written on a machine that could not reach Google's Android build servers, so the
**app has not been compiled yet**. What has been checked:

| Part | Checked how |
| --- | --- |
| Chapter and recommendation logic (Kotlin) | Compiled with the real Kotlin compiler, 17 tests pass |
| Chapter worker (Node) | 13 tests pass |
| Compose screens, Room, Firebase code | Not compiled yet. The CI job below does it |
| Firestore rules | Tests written, run in CI on the Firebase emulator |

Push this repo to GitHub. The **CI** workflow builds the app, runs the tests and lint, and
uploads a debug APK you can install. If the build is red, open the run log and fix or send me
the first error. Most first builds of a fresh project have one or two small ones.

You can also open the folder in Android Studio (Ladybug or newer, JDK 17) and press Run.

## What works without any setup

Browsing, searching, Discover, the Series page, the Reader (webtoon scroll or paged), saved
progress, downloads and offline reading, the library, Updates feed, AniList import, and the
Profile stats. Without Firebase, ratings, comments and chapter alerts show a plain message
saying they are not set up. Nothing crashes.

## Turn on ratings, comments and alerts (about 15 minutes, free)

Everything stays on the free Spark plan. Do these yourself, because they need your Google login.

1. Go to the Firebase console and create a project.
2. Add an Android app with package name `com.raja.bingehwa`. Download `google-services.json`
   and put it in the `app/` folder. It is git-ignored on purpose.
3. Build > Authentication > Sign-in method > turn on **Anonymous**.
4. Build > Firestore Database > Create database (production mode, pick a nearby region).
5. Firestore > Rules tab > paste the contents of `firebase/firestore.rules` > Publish.
   (Or with the CLI: `cd firebase`, `firebase login`, `firebase deploy --only firestore:rules --project YOUR_PROJECT_ID`.)
6. Rebuild the app. Ratings, comments and the bell on the Series page now work.

### Chapter alerts

A GitHub Actions job (`.github/workflows/chapters.yml`) checks MangaDex about every 15 minutes
and sends a push for each new chapter of any series someone has the bell on for.

1. Firebase console > Project settings > Service accounts > **Generate new private key**.
2. GitHub repo > Settings > Secrets and variables > Actions > New secret named
   `FIREBASE_SERVICE_ACCOUNT`, value is the whole JSON file. Never commit this file.
3. Actions tab > "Check new chapters" > Run workflow, with **Dry run** ticked first.
   It prints what it would do and sends nothing.

Good to know:
- The first time the job sees a series it only remembers the latest chapter. It does not alert,
  so you are not spammed with an old chapter.
- GitHub can delay scheduled runs by several minutes. Alerts are "about every 15 minutes".
- A private repo gets 2000 free Actions minutes a month. A 15 minute schedule uses roughly
  that much. If you hit the limit, change the cron to `*/30` or make the repo public.
  A public repo is unlimited and your secrets stay secret.
- On a public repo GitHub pauses scheduled jobs after 60 days with no commits. Push a small
  change to wake it.

## Tests

```
./gradlew testDebugUnitTest     # app logic
cd worker && npm test           # chapter worker
cd firebase && npm ci && npm test   # Firestore rules (starts the emulator, needs Java 17)
```

## Where things are

```
app/src/main/java/com/raja/bingehwa/
  data/remote   MangaDex client and JSON shapes
  data/local    Room database (library, progress, downloads)
  data/repo     Repo: the one door between screens and data. Also AniList import
  data/cloud    Firebase ratings and comments (or NoCloud when Firebase is not set up)
  domain        Pure logic: chapter ordering, recommendations, stats, quiet hours
  download      Background chapter downloads
  push          Notification and the Firebase message service
  ui            Theme, navigation, components, one file per screen
firebase/       Firestore rules and their tests
worker/         The chapter checker that GitHub Actions runs
```

## Content and legal

Chapters come from the MangaDex API, which is the official public route. Some series are
licensed and removed from MangaDex; for those the app opens the publisher's official page
instead (when MangaDex lists one). The app does not scrape any site. Be considerate with
sharing and do not point the worker at lots of series: MangaDex asks clients to stay near
5 requests per second, and both the app and the worker already space their calls.

## Known gaps

- Sign-in is anonymous with a display name. There is no Google sign-in. Reinstalling the app
  gives you a new identity.
- Share sends text and a link through the system share sheet. There is no image card.
- No unread badge on the Updates tab yet.
- Fonts are the system fonts for now. Bricolage Grotesque and Plus Jakarta Sans from the
  mockup are not bundled yet.
- Replies are one level deep. Moderation is report-only, reviewed by you in the Firebase console.
- `app/src/main/java/com/raja/bingehwa/ui/screens/PlaceholderScreen.kt` is an empty leftover.
  Delete it.
