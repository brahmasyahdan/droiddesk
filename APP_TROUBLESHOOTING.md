# App Troubleshooting Guide

Welcome to the DroidDesk troubleshooting guide. This document explains how to fix common issues when installing and running Linux applications inside the DroidDesk container.

## The "Running as root without --no-sandbox is not supported" Error

Because DroidDesk utilizes a PRoot environment, you are logged in as the `root` user by default. Modern Electron and Chromium-based applications have strict security sandboxes that refuse to run as root.

Apps known to have this issue include:
- Visual Studio Code
- Google Chrome / Chromium
- Discord
- Brave Browser
- Microsoft Edge
- Obsidian
- Cursor

### 1. Automatic Fixes (Recommended)
DroidDesk includes an **Auto-Patcher** that automatically fixes these applications. It hooks into the package manager so you don't have to do anything manually.

If you install an app using:
- `apt install <package>`
- `dpkg -i <package>.deb`

The auto-patcher will instantly and silently patch the app in the background so it launches perfectly from the desktop menu.

### 2. Manual Fixes (For Tarballs and Standalone Binaries)
If you download an application as a standalone binary (e.g., a `.tar.gz`, an `.AppImage`, or extracting a zip file) without using `apt` or `dpkg`, the auto-patcher will **not** trigger automatically. 

If your manually installed application refuses to launch, follow these steps:

#### Method A: Run the Global Patch Script
If you manually copied the application into a system directory (like `/usr/share` or `/opt`), you can run the auto-patcher manually:

```bash
/usr/local/bin/patch-root-binaries.sh
```
This script will scan standard installation directories, rename the real binary, and create a transparent shell wrapper in its place that automatically passes the `--no-sandbox` flag.

#### Method B: Manual Terminal Wrapper (For Custom Directories)
If you extracted the app into a custom folder (e.g., `~/Downloads/My-App`), you can create a simple bash wrapper yourself so you don't have to type the flag every time:

```bash
# Rename the real binary
mv ~/Downloads/My-App/app ~/Downloads/My-App/app.real

# Create a wrapper script in its place
cat << 'EOF' > ~/Downloads/My-App/app
#!/bin/bash
exec ~/Downloads/My-App/app.real --no-sandbox "$@"
EOF

# Make it executable
chmod +x ~/Downloads/My-App/app
```

#### Method C: Launching from the Terminal
If you are launching the standalone binary directly from the terminal, simply pass the flag manually when you run the command:
```bash
./my-electron-app --no-sandbox
```

## Phones with 4 GB of RAM or Less

On small phones Android ends the whole Linux session when memory runs out, so the desktop suddenly closes or turns black.

### Low memory mode

**Settings > Low memory mode** is on by default for phones with about 4 GB of RAM or less. It applies on the next desktop start and:

- turns off the XFCE window compositor (it keeps an off-screen copy of every window),
- turns off thumbnails in the file manager,
- syncs the clipboard less often,
- on rooted phones, limits glibc malloc arenas (`MALLOC_ARENA_MAX=2`) for Ubuntu apps.

Switching it off restores the XFCE defaults once on the next start. XFCE settings you change by hand afterwards are left alone. While the desktop is open, DroidDesk also warns you when the phone is almost out of memory.

### Keep Android from killing Linux processes (Android 12+)

Android 12 and later stop apps that run more than 32 child processes ("phantom process killer"). A desktop with a browser easily goes over that limit. Turn the limit off once:

- **Android 14 and later:** Developer options > **Disable child process restrictions**, or
  ```bash
  adb shell settings put global settings_enable_monitor_phantom_procs false
  ```
- **Android 12L and 13:**
  ```bash
  adb shell "/system/bin/device_config set_sync_disabled_for_tests persistent"
  adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
  ```

Also set **Settings > Battery optimization** to *Allowed* in DroidDesk.

### Everyday tips

- Run one large app at a time. A browser or Code OSS can use 1 GB or more each.
- In Firefox, open `about:config` and set `dom.ipc.processCount` to `2` so it starts fewer content processes.
- Close Android apps you are not using before you open the desktop.

## "VLC is not supposed to be run as root" Error

VLC Media Player also has a hardcoded block preventing it from running as root. 
If you install VLC via `apt`, our auto-patcher handles it. 

If you compile or install VLC manually, you can bypass the root check by patching the binary with this command:
```bash
sed -i 's/geteuid/getppid/g' /path/to/your/vlc/binary
```
