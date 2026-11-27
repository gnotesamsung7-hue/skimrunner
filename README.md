# SkimRunner

FPV starfighter gap-runner. You fly the AGILA-7 split-wing skimfighter through the **Bakal Trench** and the **Bato Belt**.
It's built for Android landscape, with a RadioMaster Pocket (EdgeTX) plugged in over USB-C OTG, or touch sticks.

- `index.html`: the whole game (Three.js). Also plays in a browser through GitHub Pages.
- `android/`: the WebView wrapper. Package `com.markdave.skimrunner`, with native USB joystick input.
- `.github/workflows/build.yml`: builds the signed APK on GitHub and publishes it as the latest release.
- `qr-card.html`: printable QR card that points to the latest APK.
- `make-keystore.ps1`: makes the signing key (run it once only).
- `ART_PROMPTS.md`: prompts for an image generator, for adding real art later.

---

## First-time setup (about 15 minutes)

### 1. Create the GitHub repo
1. Go to github.com and click **New repository**.
2. Name it **skimrunner**, set it to **Public**, and click **Create repository**.
3. Click **uploading an existing file**. Drag in **everything inside** the `skimrunner` folder, including the
   `.github` and `android` folders. (In GitHub Desktop: add the folder as a repo and push.)
   - The web uploader can hide folders that start with a dot. If `.github/workflows/build.yml` doesn't show up after
     the upload, click **Add file > Create new file**, type `.github/workflows/build.yml` as the name, paste the file's
     contents and commit.
4. Click **Commit changes**.

### 2. Make the signing key (once, ever)
1. Open the `skimrunner` folder on the laptop, then **right-click `make-keystore.ps1` > Run with PowerShell**.
   (Or in PowerShell: `powershell -ExecutionPolicy Bypass -File .\make-keystore.ps1`)
2. It creates `Documents\skimrunner-signing` with three files.
3. **Back up that folder now** (OneDrive or a USB stick). If this key is lost, the app can never be updated on phones again.
4. On GitHub, open the repo, then **Settings > Secrets and variables > Actions > New repository secret**:
   - Name `KEYSTORE_BASE64`: paste the full contents of `KEYSTORE_BASE64.txt`
   - Name `KEYSTORE_PASSWORD`: paste the contents of `KEYSTORE_PASSWORD.txt`
5. Never upload the `.p12` or the `.txt` files to the repo itself. The repo is public.

### 3. Build the APK
1. On GitHub, open the **Actions** tab. If it asks, click **I understand my workflows, go ahead and enable them**.
2. Click **Build APK > Run workflow > Run workflow**. It takes about 4 minutes.
3. When it's green, open the repo's **Releases** (right side of the main page). `skimrunner.apk` is there.
4. Every later upload to `main` builds a new version automatically (v1.0.2, v1.0.3...). The version shows on the title screen.

### 4. Turn on the web version (optional)
**Settings > Pages > Branch: main / (root) > Save**. The game is then at
https://gnotesamsung7-hue.github.io/skimrunner/

### 5. Share
- In the app: **Share / QR**. On a PC: open `qr-card.html` and print it.
- The QR always points to the **latest** release, so it never needs reprinting.

---

## Plugging in the RadioMaster Pocket
1. Connect the radio to the phone with a **USB-C OTG** cable (the phone side must be the OTG/host end).
2. On the radio, pick **USB Joystick (HID)** when it asks.
3. Android may ask "Open SkimRunner?". Tap OK. The title screen shows *Radio connected*.
4. Open **Radio & Rates** and move the sticks. The bars should follow.
   - The default mapping is the one you asked for: Throttle = CH2, Yaw = CH1, Pitch = CH4, Roll = CH3, Mode = CH5, Wings = CH6, Fire = CH7.
   - A standard EdgeTX model uses **AETR**, so tap **Preset: EdgeTX AETR**, or use **Detect** on each line and move that stick.
   - Put the radio's 3-position switch on CH5, a 2-position switch on CH6, and the momentary button on CH7 in the model's Mixes.

## If a build fails
Open **Actions**, click the red run, then the red step. Copy the last ~30 lines (or take a screenshot) and paste them to Claude.
