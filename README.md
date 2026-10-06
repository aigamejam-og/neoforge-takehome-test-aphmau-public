# README

CamKey is a **client-side camera tool for Minecraft 1.21.1** built with **NeoForge 21.1.x** and **Java 21**.

It allows a user to save camera positions as keyframes and play them back as smooth cinematic camera movements without moving the player or changing server state.

The project currently contains two mods in the same JAR:

- `examplemod` — the original NeoForge MDK example/template.
- `camkey` — the actual camera keyframe and playback feature.

## Build and Run

### Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.x
- Gradle wrapper included with the repository

### Build

```bash
./gradlew build
```

Compiles the project and creates the mod JAR in:

```text
build/libs/
```

This is also the command run by CI for every push and pull request using JDK 21.

### Run the Development Client

```bash
./gradlew runClient
```

Launches Minecraft with the mod loaded in a local development environment.

Development files are stored under:

```text
run/
```

### Run a Dedicated Server

```bash
./gradlew runServer
```

Starts a NeoForge dedicated server without the graphical interface.

CamKey itself is client-only and does not add server-side behavior.

### Other Useful Commands

```bash
./gradlew runData
```

Generates Minecraft resource data into:

```text
src/generated/resources/
```

```bash
./gradlew --refresh-dependencies
```

Refreshes downloaded libraries and can resolve missing or stale dependencies.

```bash
./gradlew clean
```

Deletes generated build files so the project can be rebuilt from a clean state.

### Automated Tests

```bash
./gradlew runGameTestServer
```

The project uses Minecraft's GameTest framework rather than JUnit.

There are currently **no registered GameTests**, so this command currently fails because the configured `examplemod` test namespace contains no tests.

GameTests can also be run from a development client or server using Minecraft's `/test` command.

## Using CamKey

CamKey provides the client command:

```text
/camkey
```

Available operations are:

```text
/camkey add
/camkey pop
/camkey delete
/camkey list
/camkey play
/camkey stop
```

Playback uses:

```text
/camkey play <name> <seconds> [linear|smooth]
```

At least two saved keyframes are required.

An optional literal `seconds` word is also accepted by the command.

## Key Architectural Decisions

### CamKey Is Client-Only

CamKey runs entirely on the Minecraft client.

It does not send camera information to the server or modify server state. This keeps the feature isolated from gameplay and avoids requiring server support.

During playback, CamKey creates a temporary camera entity and tells Minecraft to render the world from that camera instead of the player's normal viewpoint.

The actual player remains untouched.

### Capture, Storage, and Playback Are Separate Responsibilities

The camera system is divided into several focused components rather than placing everything in one large class.

`CamKeyClient` handles user commands and Minecraft events.

`SequenceStore` manages saved camera sequences.

`Playback` controls cinematic playback.

This separation makes the system easier to understand and gives future features a clearer place to live.

### Camera Sequences Are Saved Locally

Sequences are stored in:

```text
<game directory>/camkey/sequences.json
```

In the development environment this is normally:

```text
run/camkey/sequences.json
```

Each keyframe stores:

- X position
- Y position
- Z position
- yaw
- pitch

Changes are saved immediately whenever a sequence is modified.

The file is written through a temporary file and then moved into place, reducing the chance of leaving a partially written save file.

### Playback Is Render-Based Rather Than Tick-Based

Minecraft normally updates gameplay at a fixed tick rate, which can make camera motion appear less smooth.

CamKey instead recalculates the camera position on every rendered frame using elapsed real time.

This allows camera movement to remain smooth even when the game's rendering frame rate is higher than its gameplay tick rate.

### Two Interpolation Modes Are Supported

`linear` moves evenly between keyframes.

`smooth` applies easing within each section of the path so movement accelerates and slows around each keyframe.

Camera yaw follows the shortest rotation direction rather than unnecessarily spinning the long way around.

### Playback Temporarily Controls Camera Presentation

While a sequence is playing, CamKey:

- switches Minecraft to the temporary camera
- forces first-person camera mode
- hides the normal GUI

When playback stops, the previous camera entity, camera mode, and GUI visibility are restored.

Playback also stops automatically when appropriate, such as when the player changes worlds or opens another screen after a short startup grace period.

### NeoForge Responsibilities Are Kept Separate

NeoForge uses separate systems for initialization and live game events.

The project keeps registration and lifecycle work on NeoForge's mod event bus, while gameplay events use `NeoForge.EVENT_BUS`.

Client-only Minecraft classes are kept out of common server-compatible code so the project can still load correctly in dedicated-server environments.

### Configuration and Generated Resources Follow NeoForge Conventions

The original `examplemod` configuration is registered using NeoForge's `ModConfigSpec`.

Generated resources are stored in:

```text
src/generated/resources/
```

Mod metadata is generated from the template:

```text
src/main/templates/META-INF/neoforge.mods.toml
```

Most project identity and version settings are defined in:

```text
gradle.properties
```

CamKey's own metadata is currently defined separately in the same NeoForge metadata template.

## Known Limitations

CamKey is a working first version, but **it is not yet ready to rely on during an important recording session**. The main improvements needed involve protecting saved work, recovering the camera safely, and making failures clear to the user.

### Saved Work Can Be Lost

If a damaged sequence file fails to load, later saves could overwrite previously recorded work. Damaged files need to be preserved, with clear warnings and a recovery process.

Save failures are also not clearly reported to the user. Recent changes may be lost when the game closes without the user realizing they were never saved.

### Malformed Saved Data Can Crash the Client

Some invalid sequence data can cause the game to crash. Saved files need validation before playback, with invalid entries safely skipped and explained to the user.

### Camera Recovery Is Not Reliable in Every Scenario

Deaths, portals, dimension changes, teleports, or another system taking control of the camera can leave the player's view stuck or incorrectly restored.

Playback needs to detect these changes and verify that the previous camera is still valid before restoring it.

### Recording Controls Need Improvement

Recording camera positions through chat commands is cumbersome. Keyboard shortcuts for adding positions and starting or stopping playback have not yet been added.

Command results also do not consistently distinguish success from failure, and error messages need to be clearer.

### Player Actions Remain Active During Playback

Normal player input remains enabled, including attack and interaction controls. These actions can still affect gameplay while the cinematic camera is running.

### Camera Motion Has Some Rough Edges

A small vertical camera movement can occur at the beginning or end of playback because the virtual camera's height may not match the active viewpoint.

Smooth mode eases separately between each pair of keyframes, making the camera slow down and accelerate again at every keyframe. More polished shots would benefit from individual keyframe timing and smoothing across the entire path.

### The Save Format Is Not Versioned

Saved files depend on the current keyframe structure. Changing stored fields could break older sequences.

A versioned format is needed before expanding the feature, along with support for sequence settings such as default duration and movement style.

### Playback Supports One Global Session

Multiple named sequences are supported, but playback uses one shared global session. Supporting multiple independently managed camera sessions would require a larger structural change.

### User Guidance and Project Cleanup Remain

A complete recording guide is still needed, covering the workflow, controls, playback modes, stopping playback, storage location, backups, and current risks.

Minor project cleanup also remains, including the original `examplemod` template bundled alongside CamKey.

## What I Would Do Differently With More Time

### 4 High Level Action Items

- Gather feedback from the team to influence priority of changes.
- Fix all bugs related to the robustness of the feature.
- I would improve the Camera movement algorithm plus provide more options.
- I would create hotkeys for enhanced usability of the tool.