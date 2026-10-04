# Alien Roots (Fabric, Minecraft 1.21.1)

**Alien Seed**: right-click while looking at any block (up to 128 blocks away).
It's in the creative inventory in its own "Alien Roots" tab (also in Tools & Utilities),
or run `/give @s alienroots:alien_seed`.
Survival recipe: amethyst shards (corners) + mangrove roots (edges) + ender eye (center).

What happens:
1. A tear opens in the sky and a red warning ring tightens on the ground.
2. A glowing alien asteroid streaks in with a comet tail.
3. Explosion, cosmetic lightning, and a shockwave that rolls along the terrain.
4. An alien core forms, and its heart beats: glowing pulses race across the land.
5. Roots crawl over ANY surface (ground, cliffs, overhangs, tree trunks), forking as they go,
   with stalks reaching up into the air.
6. The colony builds: a giant Heart (helix spire + great trees), plus alien trees, helix spires,
   arches, amethyst spikes, and glowing egg pods wherever roots end.

---------------------------------------------------------------------

# INSTALL GUIDE

NOTE: Fabric's new project template only offers Mojang mappings now (Yarn was dropped for
Minecraft 26.1+). This project is written for 1.21.1 with Yarn, so DON'T use the template.
Build this project directly using the steps below.

## Part 1: Install the tools (one time)

1. Install Java 21 (JDK): go to https://adoptium.net, download "Temurin 21", run the installer.
   (On Windows, tick the option to set JAVA_HOME if the installer offers it.)
   Check it worked: open a terminal (Windows: search "cmd") and run `java -version`.
   It should say 21.
2. Download Gradle 8.10.2 (binary-only zip):
   https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
   Unzip it somewhere easy, for example:
   - Windows: C:\gradle   (so you end up with C:\gradle\gradle-8.10.2\bin\gradle.bat)
   - Mac/Linux: ~/gradle   (so you end up with ~/gradle/gradle-8.10.2/bin/gradle)
3. (Optional) IntelliJ IDEA Community Edition, only if you want to edit the code later:
   https://www.jetbrains.com/idea/download

## Part 2: Build the mod

1. Unzip alien-roots-fabric-1.21.1.zip. You should have a folder containing build.gradle.
2. Open a terminal inside that folder.
   - Windows: open the folder in File Explorer, click the address bar, type `cmd`, press Enter.
   - Mac: right-click the folder > New Terminal at Folder.
3. Run the build (the first time downloads Minecraft and takes several minutes):
   - Windows:   C:\gradle\gradle-8.10.2\bin\gradle.bat build
   - Mac/Linux: ~/gradle/gradle-8.10.2/bin/gradle build
4. When it says BUILD SUCCESSFUL, open the `build/libs` folder.
   Your mod is the .jar WITHOUT "-sources" in its name (alien-roots-1.0.0.jar).

## Part 3: Install it in Minecraft

1. Install Fabric: download the installer from https://fabricmc.net/use/installer,
   run it, choose the Client tab, Minecraft version 1.21.1, latest loader, click Install.
   (Close the Minecraft launcher first.)
2. Download Fabric API for 1.21.1 from https://modrinth.com/mod/fabric-api
   (Versions tab, filter to 1.21.1, Fabric). Don't unzip it.
3. Open your mods folder (create a folder named `mods` if it doesn't exist):
   - Windows: press Win+R, type `%appdata%\.minecraft\mods`, Enter
   - macOS: ~/Library/Application Support/minecraft/mods
   - Linux: ~/.minecraft/mods
4. Put BOTH jars in it: the Fabric API jar and your alien-roots jar.
5. Open the Minecraft launcher, pick the "fabric-loader-1.21.1" profile at the bottom-left, press Play.
6. Make a new world (Creative, cheats ON), then run: `/give @s alienroots:alien_seed`
7. Look at a block, right-click, and enjoy the meteor.

For a server: install the Fabric server for 1.21.1, put both jars in the server's `mods` folder.

## Troubleshooting
- "Could not resolve ..." during build: check your internet connection, then try again.
  If it names a specific version, send me the message.
- Build says the Java version is wrong: make sure `java -version` says 21 in the same terminal.
- Game crashes at startup mentioning "fabric-api": the Fabric API jar is missing or is for the wrong version.
- Item has no texture or name: the jar wasn't built from the full project (check assets folder is in `src/main/resources`).
- Compile errors: copy the full error message and send it to me.

## Tweak
- `AsteroidStrike`: SPEED, EXPLOSION_POWER
- `Colony`: STEP_INTERVAL, MAX_ROOT_BLOCKS, MAX_TIPS, MAX_RADIUS, STARTING_TIPS,
  MAX_STRUCTURES, MAX_STALKS, STRUCTURE_BUDGET (lower these if your game lags)
- `AlienSeedItem`: RANGE, COOLDOWN_TICKS

## Easiest way to get the jar: let GitHub build it
1. Make a free account at github.com, click + > New repository, name it alien-roots, Create.
2. Click "uploading an existing file", unzip this project, and drag EVERYTHING inside the
   alien-roots folder into the page (including the .github folder). Click Commit changes.
3. Open the Actions tab, click the latest "Build mod" run, wait for the green check (a few minutes).
4. Scroll to Artifacts at the bottom and download "alien-roots". Unzip it: the jar inside
   (the one without "-sources") is your mod.

## Updating the GitHub build after a new version
Unzip the new project, then in your repo use Add file > Upload files and drag everything in again
(it overwrites files with the same name). Then delete the old file
src/main/java/com/alienroots/RootGrowth.java: open it on GitHub, click the trash-can icon, commit.
