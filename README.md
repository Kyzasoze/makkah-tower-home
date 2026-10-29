# Makkah Tower Home

**A custom home screen for Android TV and Google TV, with prayer times built in.**

It replaces the busy stock home screen (ads, sponsored rows) with one calm screen: a live Makkah Royal Clock
Tower, prayer times with an alert at each salah, animated weather, world clocks, match-day scores and line-ups,
and hundreds of free photos as wallpapers.

![Home](screenshots/1-home.jpg)

---

## Already installed it?

**You don't need to do anything.** This update only improves these instructions; the apps are the same.
Keep using Makkah Tower Home as it is. There is nothing to reinstall or set up again.

---

## Will it work on my TV?

| Works | Doesn't work |
|---|---|
| Google TV and Android TV sets: TCL, Sony, Hisense (Google TV models), Philips, Xiaomi and others | Hisense VIDAA, LG webOS, Samsung Tizen, Roku TVs (they are not Android) |
| Chromecast with Google TV, Google TV Streamer | Amazon Fire TV and Fire TV Stick (Amazon blocks custom home screens) |
| Xiaomi TV Box / Mi TV Stick, Nvidia Shield, other Android TV boxes | Apple TV |
| Android TV **8.0 or newer** | Phones and tablets (it is made for a TV and remote) |

**How to check:** on your TV open **Settings → System → About** (or **Device Preferences → About**). If you see
"Android TV OS" with a version of 8 or higher, it will work. 2 GB of memory or more is recommended.

---

## Install it (new users) — about 10 minutes, no computer needed

### 1. Get the Downloader app
On the TV, open the **Google Play Store**, search for **Downloader** (by AFTVnews) and install it.

### 2. Allow Downloader to install apps
- Open **Settings → Apps → Security & restrictions → Unknown sources** and switch **Downloader** on.
- On newer Google TV models this option only appears after you turn on Developer options: open
  **Settings → System → About**, click **Android TV OS build** 7 times until it says "You are now a developer",
  then go back to Unknown sources.
- The TV may also ask you this the first time Downloader installs something: choose **Settings → allow**, then
  go back.

### 3. Open the download page
Open **Downloader**, select the address box, type the code **`8076512`** and select **Go**.
The Makkah Tower Home download page opens inside Downloader.

### 4. Install the three apps — in this order

| Order | File | What it is |
|---|---|---|
| **1** | **SalahTimes.apk** | Prayer times, the alert at each salah, the Qur'an player |
| **2** | **Weather.apk** | The weather app used by the weather widget |
| **3** | **MakkahTowerHome.apk** | The home screen itself |

Scroll down to **Assets**, select **SalahTimes.apk**, then **Install** and **Done**. Go back to the page and do
the same for **Weather.apk**, then **MakkahTowerHome.apk**. When Downloader offers to delete the downloaded file,
you can choose **Delete**; the app stays installed.

---

## Set it up

### 1. First-time setup (3 short screens)
Open **Makkah Tower Home** from your apps list.
1. **Your city:** select the box, type your city, select **Search**, then choose your city from the list.
   This sets the weather and the prayer times.
2. **Prayer times:** choose the calculation your community follows (for example *Muslim World League* or
   *Umm al-Qura, Makkah*). If your mosque's timetable is on **MasjidBoardLive**, choose
   *My mosque is on MasjidBoardLive…* and type the name at the end of its address.
3. **Football teams:** tick the clubs you follow (or none), then select **Done**.

The first wallpapers download straight away; the rest keep arriving in the background over the next hour.

### 2. Make the Home button open it
Open **Settings → Accessibility** (on some TVs **Settings → System → Accessibility** or
**Device Preferences → Accessibility**), select **Home button** and switch it on. From now on the remote's
Home button opens Makkah Tower Home.

### 3. Let the prayer alert appear over other apps
Open **Settings → Apps → Special app access → Display over other apps**, select **Salah Times** and switch it on.
Without this, the salah alert can't appear while you are watching something.

### Change settings later
- **City, prayer times or teams:** on the home screen, hold **OK** on the **gear** (top right), then choose
  **City, prayer times & teams**.
- **Alert sound, its volume, waking the TV for Fajr:** open the **Salah Times** app.
- **Wallpaper category:** the **picture** button at the top right.

**About the alert sound:** for copyright reasons the app plays a gentle chime, not an adhan recording. If you build
the app yourself you can add your own adhan (see *Build it yourself*).

---

## Go back to the original Google TV home screen

You can undo everything at any time.

1. **Switch off the Home button helper:** **Settings → Accessibility → Home button → Off.** The remote's Home
   button opens the TV's original home screen again straight away.
2. **Remove the apps (optional):** **Settings → Apps**, select **Makkah Tower Home → Uninstall**. Do the same for
   **Salah Times** and **Weather** if you don't want them.
3. **If the original home screen doesn't come back** (only if you or someone else disabled it with a computer):
   **Settings → Apps → See all apps → Show system apps**, select **Google TV Home** (or **Android TV Home**) and
   select **Enable**. Then restart the TV.

Uninstalling removes the downloaded wallpapers too, and leaves your other apps and settings untouched.

---

## What it does

- **Makkah clock tower** in the centre: a rendered night view with a live dial and a glowing "الله أكبر" band,
  the time, the date, the Hijri date and the next Islamic day ("Day of Arafah in 9 days").
- **Prayer times** for your city or your mosque, an alert at each salah, a countdown to jamaah, Fajr wake-up,
  **Ramadan mode** (suhoor and iftar countdowns), **Jumu'ah** reminders, an **Eid** greeting and a
  **Qur'an player** (Sheikh Abdul Basit, verse by verse, Arabic and English).
- **Animated weather**: sun, rain, lightning and wind drawn live, severe-weather warnings, a 5-day forecast.
- **World clocks** that float out of the tower, each showing that city's sky right now.
- **Match day**: live scores for your teams, an alert when line-ups are announced, an animated pitch with both XIs.
- **Wallpapers**: up to 300 freely licensed Wikimedia Commons photos in 8 categories, changing every minute, with a
  night mode after 19:00.
- **Idle screen**: Qur'an verses, hadith and morning/evening adhkar.
- A glass **dock** that slides away and comes back when you use the remote.

| | |
|---|---|
| ![](screenshots/2-world-clocks.jpg) | ![](screenshots/3-match-day-lineups.jpg) |
| ![](screenshots/4-wallpaper-categories.jpg) | ![](screenshots/5-quran-player.jpg) |

---

## Questions

- **The Home button still opens the old home screen.** Check step 2 of *Set it up* (the **Home button** helper).
- **No prayer alert while watching TV.** Check step 3 of *Set it up* (**Display over other apps**).
- **No wallpapers yet.** They need the internet; leave the TV on the home screen for a few minutes.
- **Wrong prayer times.** Hold OK on the gear → **City, prayer times & teams** and choose another calculation, or
  your mosque's board.
- **Downloader says the code doesn't work.** Type the full address instead:
  `github.com/Kyzasoze/makkah-tower-home/releases/latest`

---

## Build it yourself

Needs JDK 17 and the Android SDK (see [README-CleanYourTV.md](README-CleanYourTV.md)). The Clash Display and
Cabinet Grotesk fonts come from [Fontshare](https://www.fontshare.com) (free, ITF Free Font License): put
`clash_display_bold.otf`, `cabinet_grotesk_bold.otf` and `cabinet_grotesk_medium.otf` in
`launcher/app/src/main/res/font/`. To use an adhan, put your own recording at
`addons/prayer-times/app/src/main/res/raw/adhan.mp3` (and `adhan_fajr.mp3` for Fajr). Then run
`build-release.ps1`; the apps land in `release/`.

## Credits

- Built on the open-source [CleanYourTV](README-CleanYourTV.md) launcher by Gathin and contributors (MIT), with
  the Prayer Times add-on (majnioui) and the Weather add-on (sumyabazar1).
- Prayer times: [MasjidBoardLive](https://masjidboardlive.com) or [Aladhan](https://aladhan.com).
  Weather: [Open-Meteo](https://open-meteo.com). Qur'an text and audio: [alquran.cloud](https://alquran.cloud).
  Hadith: [fawazahmed0/hadith-api](https://github.com/fawazahmed0/hadith-api). Scores: ESPN's public scoreboard.
- Wallpapers: Wikimedia Commons featured pictures under free licences (CC0, public domain, CC BY, CC BY-SA); each
  photographer is credited on screen, and the full list is in `launcher/app/src/main/assets/wallpapers.tsv`.
- Fonts: Clash Display and Cabinet Grotesk (Indian Type Foundry, Fontshare); Unbounded, Manrope, Sora and Outfit
  (SIL Open Font License).

Not affiliated with Google, TCL or any streaming service. MIT License (see [LICENSE](LICENSE)).
