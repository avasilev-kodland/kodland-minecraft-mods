# Kodland Course

Delivers the course library `kodland.py` to Minescript. A student's `.pyj` program writes
`import kodland` and calls short functions instead of long game commands.

Manifest id: `kodland-course`. Client only. Minescript is optional: without it the mod logs one
line and does nothing.

## What it does on every start of the game

1. Creates `minescript/system/pyj/` if it is not there.
2. Writes every file from `pyj/` that is missing there or differs (SHA-256).
3. Deletes files it wrote before that the new version no longer has. Its own files are listed in
   `minescript/system/pyj/.kodland-files.json`; nothing else in the folder is touched, and
   `minescript/config.txt` is never read or written.
4. Logs one line, for example:
   `kodland library 0.1.0 in minescript/system/pyj: wrote kodland.py`
   (or `up to date (kodland.py)`, or `...; removed old kodland_x.py`).

Why `system/pyj/` and not `minescript/`: Pyjinn imports from both, but a `.py` file in
`minescript/` is also a chat command (`\kodland`) that Minescript runs with CPython. In
`system/pyj/` it is only a module. Pyjinn imports `.py` files only, never `.pyj`.

## Changing the library

The only folder to edit is [`pyj/`](pyj/).

1. Edit `pyj/kodland.py` (more modules go next to it as `kodland_<name>.py`).
2. Raise `VERSION = "x.y.z"` in `pyj/kodland.py`. That line is the version of the mod too.
3. Build: `./gradlew :course:build` from the repository root. It prints the jar name and its sha512.
4. Try it: `./run-dev.sh course -Pworld=<world>` starts the game with Minescript straight in
   `course/run/saves/<world>` (`-PnoMinescript` starts it without Minescript).
5. Release: push the tag `course-v<VERSION>`; put the release url and sha512 into the pack in the
   Sparks admin.

The build stops before packing if `pyj/` has a file that is not `kodland.py` / `kodland_<name>.py`,
or a character that is not English ASCII anywhere in a library file - function names,
parameters, docstrings, comments and texts. The library is the same for the course in all nine
languages.

The jar is reproducible: the same `pyj/` and code give the same sha512.

## Later: Java helpers

If something cannot be done with game commands, a public class goes into
[`com.kodland.course.helper`](src/main/java/com/kodland/course/helper/package-info.java) and the
library calls it with `JavaClass("com.kodland.course.helper.Name")`. There are none yet.
