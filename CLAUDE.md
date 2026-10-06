# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A NeoForge mod for Minecraft 1.21.1 (NeoForge 21.1.x), built from the NeoForged MDK using the ModDevGradle plugin (`net.neoforged.moddev`). Targets Java 21. The single jar contains **two mods**:

- `examplemod` (`com.example.examplemod`): the untouched MDK template. Its identity comes from `gradle.properties`.
- `camkey` (`dev.camkey`): a client-only mod for camera keyframe capture and cinematic playback. This is where the real feature work happens. Its metadata is a second, hardcoded `[[mods]]` block at the bottom of `src/main/templates/META-INF/neoforge.mods.toml`. It does not use `gradle.properties` placeholders, and it is not listed in the `mods {}` block of `build.gradle`.

## Commands

```bash
./gradlew build                # Compile and produce the mod jar in build/libs/ (this is what CI runs)
./gradlew runClient            # Launch a dev Minecraft client with the mod loaded
./gradlew runServer            # Launch a dedicated server (--nogui)
./gradlew runGameTestServer    # Run all registered GameTests, then exit
./gradlew runData              # Run data generators; output goes to src/generated/resources/
./gradlew --refresh-dependencies   # Fix missing libraries / stale dependency cache
./gradlew clean
```

There is no JUnit setup. Testing uses Minecraft's GameTest framework: `runGameTestServer` only loads GameTests from the `examplemod` namespace (`neoforge.enabledGameTestNamespaces`). It **crashes if no GameTests are registered**, and none exist yet. You can also run GameTests in `runClient`/`runServer` with the in-game `/test` command. Run directories go in `run/`.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on JDK 21 (Temurin) for every push and PR.

## Architecture

- **Mod identity lives in `gradle.properties`.** `mod_id`, `mod_name`, `mod_version`, `mod_group_id`, and the Minecraft/NeoForge versions are defined there. The `generateModMetadata` task fills `${...}` placeholders in `src/main/templates/META-INF/neoforge.mods.toml` from those values and writes to `build/generated/sources/modMetadata`, which is added as a resource dir. Edit the template, not a generated copy. If you change `mod_id`, also update the `MODID` constant in `ExampleMod.java`, which must match. Also rename `src/main/resources/assets/<modid>/`.
- **Two `@Mod` entry points share the same mod id.**
  - `ExampleMod` is the common entry point (both sides). It gets the mod `IEventBus` and `ModContainer` through its constructor. Registration happens through `DeferredRegister`s (blocks, items, creative tabs), which must be `.register(modEventBus)`'d in the constructor. Lifecycle events (`FMLCommonSetupEvent`, `BuildCreativeModeTabContentsEvent`) are added as listeners on the mod bus. Game events (e.g. `ServerStartingEvent`) go through `NeoForge.EVENT_BUS.register(this)` plus `@SubscribeEvent` methods.
  - `ExampleModClient` is `@Mod(dist = Dist.CLIENT)` and `@EventBusSubscriber(value = Dist.CLIENT)`, so it never loads on dedicated servers. Anything that touches `net.minecraft.client.*` belongs here or in other client-only classes, never in common code. It also registers the NeoForge auto-generated config screen.
- **Two event buses:** the mod event bus handles registration and lifecycle, and `NeoForge.EVENT_BUS` handles in-game events. Subscribing to the wrong bus fails silently.
- **Config:** `Config.java` builds a `ModConfigSpec`, registered as `ModConfig.Type.COMMON` in the `ExampleMod` constructor. Config option translation keys go in `assets/examplemod/lang/en_us.json` so the config screen can show them.
- **Mappings:** Mojang official names with Parchment parameter names and javadoc (versions are in `gradle.properties`).
- **Access transformers:** `src/main/resources/META-INF/accesstransformer.cfg` is picked up automatically if you add it.
- **Generated resources:** `src/generated/resources/` (the `runData` output) is on the main resource path. `.bbmodel` files and datagen `.cache` files are left out of the jar.
- **Runtime-only dependencies:** use the `localRuntime` configuration instead of `runtimeOnly` for optional dependencies that should be present only at dev runtime. That keeps them out of the published POM.

## CamKey (`dev.camkey`)

Client-only (`@Mod(dist = Dist.CLIENT)`, and its dependencies are declared `side="CLIENT"`), so all of it can touch `net.minecraft.client.*` freely. There is no networking or server state.

- **`CamKeyClient`**: an `@EventBusSubscriber` on the game bus. It registers the `/camkey add|pop|delete|list|play|stop` client command tree (`RegisterClientCommandsEvent`) and passes `RenderFrameEvent.Pre`, `ClientTickEvent.Post`, and logout events to `Playback`. `play` takes `<name> <seconds> [linear|smooth]` (an optional literal `seconds` word is also accepted) and requires at least 2 keyframes.
- **`SequenceStore`**: a static in-memory `TreeMap<String, List<Keyframe>>`. It loads once in the `CamKeyMod` constructor and saves **on every mutation** to `<gameDir>/camkey/sequences.json` (in dev that's `run/camkey/sequences.json`), using Gson with a tmp-file and atomic move. `Keyframe` is a record (x, y, z, yaw, pitch in degrees) that is serialized directly, so renaming its fields breaks saved files.
- **`Playback`**: a static state machine. Playback creates a never-spawned `Marker` subclass (`CamEntity`, constant eye height 1.62), makes it the camera entity through `mc.setCameraEntity`, forces first person, and hides the GUI. The player and the server are left alone. The pose is recomputed **every render frame from `System.nanoTime()`**, not per tick. Old and current positions/rotations are set to the same value, so vanilla partial-tick interpolation does nothing. Keyframes are interpolated piecewise-linearly, yaw takes the shortest arc, and "smooth" applies smoothstep within each segment, so the camera eases in and out of every keyframe. `tick()` aborts playback when any screen opens (after a 250 ms grace period) or the level changes. `stop()` restores the previous camera entity, camera type, and `hideGui`.
