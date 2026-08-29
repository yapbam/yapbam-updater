# Yapbam Updater

Small Java utility that installs Yapbam updates.

Basically, it extracts a zip file (`update.zip`) into the application's launch
directory, then cleans up the temporary update folder. It is intended to be
launched by the Yapbam desktop application once an update has been downloaded.

## How it works

The `Updater` main class (`net.yapbam.updater.Updater`):

1. Reads the `update.zip` file located in the update directory returned by
   `Portable.getUpdateFileDirectory()` (usually `<data directory>/update`).
2. Extracts each entry of the zip into the launch directory
   (`Portable.getLaunchDirectory()`, i.e. the current `user.dir`).
   - Directories are created (replacing any file with the same name).
   - Files are written (replacing any directory with the same name).
   - If a target file is locked (e.g. `Yapbam.exe` still running on Windows),
     it is first renamed to `<name>.old` so the new file can be written.
     Windows allows renaming a running executable, but not overwriting it.
   - Entries whose name ends with `.sh` are made executable.
3. Shows a success or error dialog (localized messages).
   On error, the exception stack trace is written to `updater.log` in the
   launch directory (see [Diagnostics](#diagnostics) below).
4. Deletes the temporary update directory.

The `Portable` utility class (`net.yapbam.util.Portable`) determines whether the
application runs in portable mode (the launch directory is writable) or
installed mode, and resolves the data directory accordingly. This class is
duplicated from the main Yapbam project.

### ⚠️ Never write to stdout or stderr

The updater is launched by `MainFrame` in Yapbam via a `ProcessBuilder`, and
the launching JVM consumes the child's **stderr** in a blocking `readLine()`
loop in order to stay alive until the update completes (see `MainFrame`'s
`windowClosing` listener). **stdout is not consumed at all.**

Consequences:
- Writing to **stdout** can fill the OS pipe buffer (~4 KB on Windows) and
  **deadlock** the updater process forever.
- Writing to **stderr** is "tolerated" today, but any substantial output would
  keep the parent JVM alive longer than necessary and is not the intended
  communication channel.

For these reasons, the updater must **never** write to `System.out` or
`System.err`. User-facing feedback is done via `JOptionPane` dialogs, and
diagnostic information is written to `updater.log` (see below).

## Diagnostics

When the update fails with an `IOException`, the full stack trace is appended
to `updater.log` in the launch directory. This file is the only way to
diagnose failures on end-user machines, since stdout/stderr are not usable
(see above).

## Localization

Messages are externalized in `messages.properties` (default, English) and
`messages_fr.properties` (French), loaded through `Messages`.

## Build

This is a Maven project.

```
mvn package
```

### Dependency

- [`com.fathzer:ajlib`](https://github.com/fathzer/ajlib) — provides
  `FileUtils` (used for directory deletion).

## Project layout

```
src/main/java/net/yapbam/
├── updater/
│   ├── Updater.java          # Main class: extracts update.zip
│   └── Messages.java         # ResourceBundle wrapper
└── util/
    └── Portable.java         # Portable/installed mode detection
src/main/resources/net/yapbam/updater/
├── messages.properties       # Default (English) messages
└── messages_fr.properties    # French messages
```
