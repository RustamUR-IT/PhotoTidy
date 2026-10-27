# System Optimizer

(formerly Photo Tidy)

An Android app that sorts your photos, files, apps and home screen, and suggests junk to delete.
Runs entirely on the phone with free, offline AI models. Nothing is uploaded.

## What it does

- **Smart widgets** for your normal home screen: an App dashboard (most-used + all apps by type)
  and Smart folders (one category each, most-used first). They never move your other widgets.
- **Auto-sorted home screen** (optional – pick it as your home app):
  every app goes into a matching folder, new apps too; dock with your most-used apps;
  widgets are only ever moved by you; long-press an app to move it (your choice sticks)

- **Sorts** camera, download and messenger photos into `Pictures/Sorted/…`
  - Categories: People, Pets & Animals, Food & Drinks, Nature, City & Travel, Vehicles, Documents
  - **Events**: 15+ photos taken close together (a trip, a party) go into one dated folder you can rename
  - Photos it's unsure about stay where they are
- **Finds probable junk**: blurry, too dark, blank, near-duplicates from bursts (keeps the sharpest),
  exact copies, old screenshots, tiny images. Favourites are never suggested.
- **You review everything.** Nothing moves or gets deleted without your tap.
  Deleted photos go to the system trash and can be restored for 30 days.
- **Tidies files too** (optional, needs "All files access"):
  - Loose files in Download, Documents and the top of your storage go into `Documents/Sorted/…`
  - Topic folders found from the file name *and the text inside PDFs* (German + English):
    Invoices & Receipts, Tickets & Travel, Contracts & Official, Bank & Insurance, Health, Work & School
  - Everything else by type: Documents, Spreadsheets, Presentations, Ebooks, Archives, Audio, Videos
  - Junk: old APK installers, unfinished downloads, empty files, exact duplicate downloads,
    zips you already unpacked, old calendar invites
  - Deleted files go to the app's own trash and can be restored for 30 days
  - Folders you made yourself are never touched
- **Tidies apps** (optional):
  - Every app with a one-line "what it does"
  - Badges for **Important** (banking, login-code apps, password managers, chats, email, official ID)
    and **Essential** (your default phone/SMS/home screen/keyboard, device admins, system parts)
  - With "Usage access": which apps you haven't opened in months and how much space they take
  - Uninstall one or many (Android confirms each); important apps get a reminder first
  - Pre-installed apps: a shortcut to Android's Disable / Force stop page
    (Android doesn't let an app switch off other apps by itself)
- **Every night while charging** it checks new photos and notifies you if there's something to review.

## Getting the app onto your phone (free, no computer tools needed)

The APK is built by GitHub for free.

1. Create a free account at github.com and make a **new private repository** called `PhotoTidy`.
2. Upload every file and folder from this project into it (keep the folder structure,
   including the `.github` folder – it contains the build instructions).
3. Open the **Actions** tab. A build starts automatically and takes about 5–8 minutes.
4. When it's green, open the repository's **Releases** page on your phone and tap `PhotoTidy.apk`.
5. Android asks to allow installing from your browser – allow it, then install.

Every time the code changes, a new build appears on the Releases page and installs as an update.

## First run

1. Allow photo access ("Allow all").
2. Tap **Start first scan**. A few thousand photos take a few minutes; you can leave the app.
3. Review **Ready to sort** and **Possible junk**.
4. Optional: turn on **media management** (button in the app) so Android doesn't ask you to
   confirm every batch.

## Settings

- **Junk detection**: Careful / Normal / Thorough
- **Group trips & events**: on/off
- **Check every night**: on/off
- **Forget my decisions**: resets "Not junk" and "Never suggest"

## Notes

- Android 11 or newer. Built for the Nothing Phone 3a (Android 15).
- Images inside other apps' private folders are never moved, so those apps keep working.
- `app/phototidy.keystore` is the signing key that lets updates install over the old version.
  Keep the repository private.
