# VersionUnbound — Handoff

State as of 2026-10-29. Phase 0 (workspace), Phase 1 (loader spike) and Phase 2 steps 1 (FML metadata and context), 2 (lifecycle ordering) and 3 (registries) are done. Phase 2 step 4 (game bus and common events) is in progress: **4a (server lifecycle, commands, ticks, levels) and 4b (player and interaction events) are done; 4c (living and entity events) has entity join/leave, death, drops, the damage pipeline, target changes, knockback, equipment changes and item use; next are item pickup and chunk events** (§8).

Git: step 3 is not committed yet (the user commits when asked). A snapshot of the tree at the end of step 3 is the unreferenced commit object `8576e2d114e434611d8a5fd0864838e5e00871a4` (not on any branch), so steps 3 and 4 can still become separate commits: commit that snapshot's tree first, then the rest.

The full approved plan is at `/home/username/.claude/plans/alright-our-goal-is-abstract-sparrow.md`. This file is self-contained, so you don't need the plan to continue.

---

## 1. Goal and agreed decisions

Run **unmodified NeoForge 1.21.1 mod jars on Fabric 26.2**. It works like Sinytra Connector in reverse, with a version jump added on top. Other source versions will be added later, one at a time.

Decisions the user made (don't re-ask):

| Decision | Choice |
|---|---|
| Architecture | **Two layers.** Layer A (`neoforge-api`) is the NeoForge **26.2** API on Fabric 26.2. Layer B (`upgrade-1.21.1`) upgrades 1.21.1 jars to the 26.2 API. Supporting another version later means adding another Layer B. |
| Loading | **Drop-in at runtime.** Our Fabric mod acts as FML in `preLaunch`. A CLI runs the same pipeline for debugging. |
| Rendering-engine mods | **Out of scope for v1.** Embeddium, Sodium, Iris, Immersive Portals, FirstPersonModel and NotEnoughAnimations are detected and refused with a clear message. |
| License | Porting NeoForge (LGPL-2.1) code is allowed. The project is **LGPL-2.1-only**. Connector is MIT and Fabric API is Apache-2.0, so both can be reused freely. |
| Lifecycle hooks | **Mixins at NeoForge's own call sites, not Fabric entrypoints** (chosen in step 2). Construction and registration (`begin`) run right after `Bootstrap.validate()`, before every Fabric mod's `onInitialize`. Setup (`load`) runs where NeoForge runs it, after Fabric's mod init and Fabric API's registry freeze. Reason: the same order on every launch, whatever mods are installed. Consequence: **nothing in `begin` may rely on a Fabric initializer** (Fabric API's or our own). The sequence is in §4. |

Phases:
- **0. Workspace** ✅
- **1. Loader spike** ✅
- **2. Layer A core** ← in progress (steps 1–3 done, step 4 in progress)
- **3. Layer B MVP.** Milestone: the unmodified Chunky 1.21.1 jar runs a pregen on a Fabric 26.2 server.
- **4. Content mods, server side:** Toms Storage → FarmersDelight → KaleidoscopeCookery → DynamicTrees.
- **5. Client shims.** GUI, renderers and models; the largest open-ended phase.
- **6. Libraries and heavy mods:** Zeta+Quark, Architectury+PowerGrid, Cobblemon (Kotlin for Forge), Create (bundles Flywheel, a render engine, so this is risky), Sable/Aeronautics.

---

## 2. Repository map

Repo root: `/home/username/Development/active/minecraft-modding/VersionUnbound` (git repo, branch `main`; each completed step is a commit, see `git log`).

```
flake.nix, flake.lock         dev shells + `mc-src` tool (decompiles vanilla)
nix/mc-src.sh                 the mc-src script
.gitignore                    ignores src/mc-*/, src/mod_Src/, src/mod_Jars_*/, build/, .gradle/, run/, IDE files
HANDOFF.md                    this file
VersionUnbound/versions/26.2/ the Gradle project (Fabric Loom), see §4
src/                          REFERENCE MATERIAL, all git-ignored
  mc-1.21.1/  {java,resources,asset-index.json}   decompiled 1.21.1, Mojang names + Parchment params/javadocs
  mc-26.2/    {java,resources,asset-index.json}   decompiled 26.2 (shipped unobfuscated by Mojang)
  mod_Src/main/
    SinytraConnector/    Connector, branch 26.2.x (MIT). The architecture template.
    fabric-api/          switched to branch 26.2 (was 26.3; it had no local changes)
    NeoForge-1.21.1/     shallow clone, branch 1.21.1
    NeoForge-26.2/       shallow clone, branch 26.2.x  ← main porting source for Layer A
    FML-11.0.16/         FML sources (unpacked loader-11.0.16-sources.jar): net/neoforged/{fml,neoforgespi}
  mod_Src/test/          source repos of the test mods (several are NOT on 1.21.1 branches; use the jars)
  mod_Jars_1-21-1_Neoforge/   THE TEST JARS (1.3 GB including .git dirs), see §7
```

Key files inside the NeoForge 26.2 clone:
- `src/main/java/net/neoforged/neoforge/` and `src/client/java/net/neoforged/neoforge/client/`: the API to port (1,158 Java files).
- `src/main/resources/META-INF/injected-interfaces.json`: maps vanilla classes to the NeoForge extension interfaces (`IBlockExtension`, …). Its format matches Loom's `loom:injected_interfaces` almost exactly, so it converts directly.
- `src/main/resources/META-INF/accesstransformer.cfg` (373 lines) and `accesstransformergenerated.cfg` (325 lines): NeoForge's own widening of vanilla members. Mods compiled against NeoForge assume these.
- `patches/` (850 `.patch` files): every vanilla hook NeoForge adds. Each one must be reproduced as a mixin or as a Fabric API callback.
- `gradle.properties`: library versions (eventbus 8.0.5, FML 11.0.16, NightConfig 3.9.0, mixin 0.17.4, ASM 9.10.1, AT 11.0.2, JST 2.0.11).

---

## 3. Environment and commands

- **NixOS.** Use the flake dev shell. Java isn't on PATH outside it, and neither is `zip`.
  - `nix develop` gives JDK 25 (for 26.x). `nix develop .#jdk21` gives JDK 21 (for 1.21.x).
  - Both shells set `LD_LIBRARY_PATH` for LWJGL and include `mc-src`, `git`, `jq`, `zip`/`unzip`, `xvfb-run`.
- **The flake only sees git-tracked files.** If you add a file the flake reads (e.g. under `nix/`), `git add` it first, or Nix won't find it.
- Build and run, from `VersionUnbound/versions/26.2` (`<repo>` is the repo root). Write logs to the session scratchpad; `*.log` isn't git-ignored.
  ```
  nix develop <repo> -c ./gradlew build
  # Server: sends `stop` once the server is up (or failed), so a run takes about 15 s.
  ( for i in $(seq 1 120); do sleep 2; grep -qE 'Done \(|Failed to start|BUILD FAILED' $LOG && break; done; sleep 3; echo stop ) \
    | timeout 300 nix develop <repo> -c ./gradlew :loader:runServer --console=plain > $LOG 2>&1
  # Client, headless:
  nix develop <repo> -c xvfb-run -a -s '-screen 0 1600x900x24' ./gradlew :loader:runClient --console=plain > $LOG 2>&1 &
  ```
  - `runServer`/`runClient` depend on `copyTestMods`, which copies testmod jars into `loader/run/mods/` and writes `eula=true`. It doesn't delete other jars there, so a jar dropped in by hand stays until you remove it.
  - Piping `stop` into Gradle works: stdin is forwarded to the server.
  - **The client doesn't exit on its own.** Once `[lifecycle] all N checks passed` (or `FAILED`) shows up, kill the game JVM: the `java` process whose `/proc/<pid>/cmdline` contains `net.fabricmc.devlaunchinjector.Main` (pick it with `pgrep -x java`). Gradle then reports exit value 143 and BUILD FAILED, which is expected. Don't use `pkill -f` with that pattern from a script: it also matches the shell running the script and kills it.
  - Under Xvfb the client renders with Mesa llvmpipe (OpenGL 4.6). Mesa's "vulkan: No DRI3 support" note is harmless.
  - **Client joining the dev server** (checks registry sync end to end, and step 4's player and client checks): start `runServer`, wait for "Done (", then run the client with `./gradlew :loader:runClient --args='--quickPlayMultiplayer localhost:25565'`. A script for it (run it in the background; `SLOG`/`CLOG` in the scratchpad):
    ```bash
    cd <repo>/VersionUnbound/versions/26.2; MARK=$SLOG.client-done; rm -f $MARK
    ( for i in $(seq 1 150); do sleep 2; grep -qE 'Done \(|Failed to start|BUILD FAILED' $SLOG && break; done
      for i in $(seq 1 150); do sleep 2; { grep -qE 'left the game|BUILD FAILED' $SLOG || [ -e $MARK ]; } && break; done; sleep 3; echo stop
    ) | timeout 900 nix develop <repo> -c ./gradlew :loader:runServer --console=plain > $SLOG 2>&1 &
    for i in $(seq 1 150); do sleep 2; grep -q 'Done (' $SLOG && break; done
    timeout 600 nix develop <repo> -c xvfb-run -a -s '-screen 0 1600x900x24' ./gradlew :loader:runClient \
      --args='--quickPlayMultiplayer localhost:25565' --console=plain > $CLOG 2>&1 & CLIENT=$!
    for i in $(seq 1 180); do sleep 2; grep -qE '\[events\] (all [0-9]+ client checks passed|[0-9]+ of [0-9]+ client checks FAILED)|BUILD FAILED' $CLOG && break; done; sleep 3
    for pid in $(pgrep -x java); do tr '\0' ' ' < /proc/$pid/cmdline | grep -q 'fabric.dli.env=client' && kill $pid; done
    wait $CLIENT; touch $MARK; wait
    ```
    The client run takes about a minute; the `events` testmod's client checks end with a disconnect, so the server logs "left the game" on its own. Two local settings in the git-ignored `loader/run/` make this work: `online-mode=false` and `enforce-secure-profile=false` in `server.properties` (the dev client is offline), and `onboardAccessibility:false` in `options.txt` (otherwise vanilla shows the accessibility onboarding screen first, and quick play waits until someone dismisses it). Stop the client by killing the JVM whose cmdline contains `fabric.dli.env=client`; the server's "left the game" line then tells the stop loop to send `stop`.
- **`mc-src <ver>...`** decompiles vanilla into `src/mc-<ver>/` and caches downloads in `~/.cache/mc-src/`.
  - **Obfuscated versions** (e.g. 1.21.1) go through four steps:
    1. AutoRenamingTool applies Mojang's mappings (`--reverse`).
    2. tiny-remapper applies Parchment's parameter names, converted from `parchment.json` to tiny v2 with jq, and names the remaining `$$N` locals from their types.
    3. Vineflower decompiles.
    4. JST adds Parchment javadocs (`--no-parchment-parameters`).
  - **26.x versions** go straight to Vineflower.
  - Pitfalls already worked around:
    - JST's parameter mode prefixes names with `p_` when a name clashes, so parameters are applied in bytecode instead.
    - Vineflower 1.12's `tiny` renamer crashes on some methods when `--rename-parameters` is on.
    - JST fails with "loaded from two sources" if the remapped jar is on its classpath. Only libraries go on JST's classpath.
- Useful cached jars:
  - `~/.cache/mc-src/jars/26.2-client.jar` (26.2, Mojang names)
  - `~/.cache/mc-src/jars/1.21.1-client-mojmap.jar` (1.21.1 remapped)
  - `~/.cache/mc-src/jars/1.21.1-classpath.txt` (library list)
- **Scratch files** go in the session scratchpad. Downloaded or extracted third-party content goes in its own directory. Run Python on it with `python3 -I`.
- **Git identity:** commits use the user's own identity `EroLightz <michelleyevon@protonmail.com>`, set in this repo's local git config (the global config is a placeholder). Never mention Claude in commits: no `Co-Authored-By` trailer. Don't commit unless asked.

---

## 4. The Gradle project (`VersionUnbound/versions/26.2/`)

Toolchain:
- Gradle 9.7.1 (wrapper copied from WildernessUnbound)
- Fabric Loom **1.18-SNAPSHOT** (resolves to 1.18.4), plugin id **`net.fabricmc.fabric-loom`**. This is the id for unobfuscated versions: there is no `remapJar` step, and `jar` is the final output.
- Fabric Loader **0.19.5**. The plan said 0.20.1; 0.19.5 was chosen because it's proven against 26.2 in sibling projects.
- Fabric API **0.161.0+26.2**
- Java 25 (`release = 25`)

All versions are in `gradle.properties`, including the NeoForge ones: `neoforge_version=26.2.0.93`, `neoform_version=2`, `fml_version=11.0.16`, `eventbus_version=8.0.5`, `typetools_version=0.6.3`, `mergetool_version=2.0.7`, `nightconfig_version=3.9.0`, `maven_artifact_version=3.9.16`. Group: `net.salutaryfish.versionunbound` (matches the user's other projects).

```
settings.gradle      includes neoforge-api, loader, testmods:{hello,metadata,lifecycle,registries,events,fabricprobe}
build.gradle         root: applies loom (apply false); subprojects get java, NeoForged maven, Java 25. Task
                     generateTestmodClassTweaker writes testmods/neoforge-compile.classtweaker (see testmods below).
neoforge-api/        Fabric mod id `versionunbound_neoforge` (Layer A)
  build.gradle       loom splitEnvironmentSourceSets(): client-only code goes in src/client, as in NeoForge, so common code
                     can't reference client classes. api+include: net.neoforged:bus, mergetool:api (Dist/OnlyIn),
                     night-config toml (+core), maven-artifact; include typetools. processResources expands
                     neoforge_version/neoform_version into fabric.mod.json (custom "versionunbound:implements"); the
                     loader reads it to build FML's VersionInfo. accessWidenerPath = the class tweaker below.
  src/main/java/net/neoforged/{fml,neoforgespi}/   FML 11.0.16: 102 files verbatim, 9 adapted (see below)
  src/main/java/net/neoforged/neoforge/            NeoForge 26.2: common/NeoForge (the game bus), common/NeoForgeMod (NeoForge's
                                                   own mod, a growing partial port), common/extensions/{IHolderExtension,
                                                   IHolderSetExtension}, internal/{NeoForgeBindings, CommonModLoader,
                                                   RegistrationEvents}, server/loading/ServerModLoader, and since step 3
                                                   registries/ (+ callback/, datamaps/ types only, holdersets/): 45 files,
                                                   31 verbatim (deviations below). Since step 4: event/ (verbatim event
                                                   classes: server/, tick/, level/LevelEvent, entity/{EntityEvent,
                                                   living/LivingEvent, player/PlayerEvent}, RegisterCommandsEvent,
                                                   CommandEvent, GameShuttingDownEvent) and the partial hook classes
                                                   event/EventHooks, common/CommonHooks, server/ServerLifecycleHooks
  src/client/java/net/neoforged/neoforge/          client/loading/ClientModLoader; since step 4 client/event/ClientTickEvent
                                                   (verbatim) and client/ClientHooks (partial)
  src/main/java/net/salutaryfish/versionunbound/neoforge/
    registries/MappedRegistryExtension   stands in for NeoForge's BaseMappedRegistry superclass (setSync, setMaxId, getDataMaps)
    patches/                             members NeoForge's patches add to vanilla classes: IdentifierExtension (compareNamespaced)
                                         and RegistryLookupExtension (+$Delegate: data map getters), injected as interfaces with
                                         default methods; RegistryOpsPatch (static retrieveRegistryLookup, called instead)
    mixin/registries/                    NeoForge's registry patches as mixins, in versionunbound_neoforge.mixins.json ("mixins"):
                                         MappedRegistryMixin, HolderReferenceMixin, HolderSetNamedMixin, RegistryMixin,
                                         RegistryLoadTaskMixin, PoiTypesMixin, BuiltInRegistriesMixin, BuiltInRegistriesAccessor
    mixin/server/MainMixin               versionunbound_neoforge.mixins.json ("server")
    mixin/events/                        NeoForge's event-firing patches (step 4): MinecraftServerMixin (server started,
                                         stopping, stopped, server and level ticks, level load and unload, world spawn),
                                         CommandsMixin (RegisterCommandsEvent, CommandEvent), ServerLevelMixin (entity ticks,
                                         level save), EntityMixin (passenger ticks), PlayerMixin (player ticks, entity
                                         interaction, attack), PlayerListMixin (login, logout, respawn position, respawn),
                                         ServerPlayerMixin (travel to dimension, changed dimension, clone),
                                         ServerPlayerGameModeMixin (left/right click block, right click item, block break),
                                         PersistentEntitySectionManagerMixin (entities joining), ServerLevelEntityCallbacksMixin
                                         (entities leaving), LivingEntityMixin (death, drops; ServerPlayerMixin: player
                                         death), EntityMixin (+ drop capture); and in "server": DedicatedServerMixin (about
                                         to start, starting, shutting down)
    patches/PersistentEntitySectionManagerExtension   NeoForge's addNewEntityWithoutEvent (for ServerLevel.addPlayer)
    patches/EntityDropsCapture           NeoForge's captureDrops methods, added to Entity by EntityMixin (LivingDropsEvent)
    patches/LivingEntityDamageContainers access to the damage container stack (and LivingEntity's protected members) for
                                         the player's actuallyHurt, implemented by LivingEntityMixin
    patches/WeightedListBuilderExtension + mixin/patches/WeightedListBuilderMixin   NeoForge's WeightedList.Builder patch
                                         (mutable entries, add/addAll/remove/removeIf/getList), needed by LevelEvent
    patches/TriStateExtension            NeoForge's isTrue/isDefault/isFalse on vanilla's TriState (interaction events)
  src/client/java/net/salutaryfish/versionunbound/neoforge/client/mixin/{MainMixin,MinecraftMixin}
                                                   versionunbound_neoforge.client.mixins.json; these 3 mixins are NeoForge's
                                                   loading hooks (the sequence is below)
  src/client/java/net/salutaryfish/versionunbound/neoforge/client/mixin/events/{MinecraftMixin,ClientLevelMixin,
                                         ClientLevelEntityCallbacksMixin,IntegratedServerMixin,MultiPlayerGameModeMixin,
                                         ClientPacketListenerMixin}   entities joining and leaving the client level, client
                                         ticks, client level ticks, load and unload, entity ticks, game shutting down,
                                         empty clicks, logging out; the integrated server's about to start and starting;
                                         the client's own interaction events (several inside startPrediction lambdas,
                                         targeted by their 26.2 names); logging in and clone
  src/main/resources/versionunbound_neoforge.classtweaker   un-seals Holder (extendable), injects NeoForge's extension
                                                   interfaces (IRegistryExtension, IHolderExtension, IHolderSetExtension) and the
                                                   patches/ interfaces, widens RegistryOps.lookupProvider and PoiTypes.TYPE_BY_STATE.
                                                   Loom applies it at compile time, Fabric Loader at run time.
  src/main/resources/META-INF/services/net.neoforged.fml.IBindingsProvider   → NeoForgeBindings (verbatim)
  src/main/resources/lang/*.json                   FML's translations for ModLoadingIssue keys (verbatim)
loader/              Fabric mod id `versionunbound`, entrypoint preLaunch only
  build.gradle       implementation+include :neoforge-api; loom.mods { versionunbound {...};
                     versionunbound_neoforge { sourceSet('main', ':neoforge-api'); sourceSet('client', ':neoforge-api') } }
                     copyTestMods task (→ run/mods, writes eula.txt) wired into runClient/runServer
  src/main/java/net/salutaryfish/versionunbound/loader/
    VersionUnboundPreLaunch.java   FMLLoader.create(Knot, dist, production, gameDir, versions) → NeoModDiscovery.findModJars
                                   → FMLLoader.discoverMods(...) → addToClassPath(accepted jars) → NeoMixinConfigs.register
                                   → FMLLoader.startModScan() (must come last, see §5 on Mixin's config set)
    NeoModDiscovery.java           lists mods/*.jar that contain META-INF/neoforge.mods.toml (reading them is FML's job)
    NeoMixinConfigs.java           FML's MixinFacade.addMixins, adapted: requiredMods, behaviorVersion, duplicate configs;
                                   decorates each config with FabricUtil.KEY_MOD_ID / KEY_COMPATIBILITY
testmods/hello/      a GENUINE NeoForge 26.2 mod: Loom only supplies MC classes; compileOnly against the real
                     net.neoforged:neoforge:26.2.0.93:universal, fancymodloader:loader:11.0.16, bus, mergetool:api.
                     neoforge.mods.toml + hello.mixins.json (JAVA_21) + @Mixin(MinecraftServer) @Inject loadLevel HEAD.
testmods/metadata/   genuine NeoForge 26.2 mod `metadata` (built like hello). Requires hello with ordering AFTER. 47 checks
                     of IModInfo, ModList, ModLoadingContext, FMLEnvironment/FMLLoader/FMLPaths, extension points, scan data
                     and IMC. Logs "[metadata] PASS|FAIL <check>" and, at Process IMC, "[metadata] all N checks passed".
testmods/lifecycle/  genuine NeoForge 26.2 mod `lifecycle` (built like hello). 11 checks on the server, 12 on the client: step
                     order, construction before / setup after Fabric's mod init, registries frozen by common setup, FML
                     config loading (a minimal IConfigSpec writes config/lifecycle-common.toml), the game bus via addListener
                     and @EventBusSubscriber (bindings provider), threads, and on the client that client setup sees the
                     window, options, resources and renderers. Logs "[lifecycle] all N checks passed" at load complete.
testmods/registries/ genuine NeoForge 26.2 mod `registries`. 45 registration checks (summary at load complete) and, on servers,
                     4 world checks (a loadLevel mixin). DeferredRegister/DeferredHolder/DeferredBlock/DeferredItem, data
                     components used by items, entities, POI types, RegisterEvent order and active container, NewRegistryEvent,
                     ModifyRegistriesEvent callbacks, a defaulted synced custom registry (add/bake callbacks, getKeyOrNull,
                     containsValue, ids, aliases), id limits, sync flags, NeoForge's own registries and holder sets, data map
                     types, datapack registries (synced, and with RegistryBuilder options), and what fabricprobe saw.
                     Logs "[registries] all N registration checks passed" and "[registries] all N world checks passed".
                     Its bake callback logs each bake with the ids, which shows client-side remaps after joining a server.
testmods/events/     genuine NeoForge 26.2 mod `events` (built like lifecycle), one check group per side. Since 4c the counts
                     are server 56, server player 25, client 16. LivingScenario (at server started, finish() at stopping):
                     a zombie's target set, canceled and redirected; a piglin (its brain loads the patched StartAttacking);
                     knockback and a canceled one; a zombie given a helmet (seen on its next tick); golden apples used
                     by chickens: start (32 ticks), release/stop, a canceled start, and a start cut to 2 ticks that ticks
                     and finishes before the server stops. DamageScenario (at server started): chickens hurt with
                     the incoming damage untouched, canceled, raised, doubled in Pre, the chicken made invulnerable, the
                     invulnerability ticks dropped, iframes blocking a second hit, and an armored zombie (armor reduction
                     in the container); the player takes one cactus hit (Player.actuallyHurt). Also: entities joining (once each, a canceled join keeps the
                     entity out) and leaving, chickens that die (drops added), have their death canceled (and are healed)
                     or their drops canceled, a canceled player death, the player joining and leaving levels on
                     teleports and respawn, and on the client the local player joining and the old one leaving on
                     respawn. Join counts are the evidence that an entity was added (see §5 on chunk visibility).
                     Server (30 checks in 4a,
                     summary at ServerStoppedEvent): the order of commands, SERVER config, about to start, level loads,
                     world spawn (new worlds only), saves, starting, started, ticks, stopping, game shutting down, unloads
                     and stopped (logged as "[events] server event order: [...]"); getCurrentServer; the SERVER config in
                     config/ with world/serverconfig/readme.txt; a command registered in RegisterCommandsEvent, a canceled
                     CommandEvent and replaced parse results; server, level and entity ticks, including a canceled entity
                     tick and a passenger (three armor stands in a forced chunk at 0,0, removed again at stopping). Server
                     players ("server player checks", 20, only once a player has played): PlayerScenario runs 20 ticks
                     after login and drives the server's own code paths for that player: left click and break (allowed
                     and canceled), right click on note blocks (used; use-block FALSE; use-block TRUE while sneaking with
                     a stick; canceled with a result), right click item (and canceled), entity interaction and attack (and
                     canceled), teleports (within the overworld, to the nether and back), then a respawn as from the End;
                     login, logout and player ticks are checked when the server stops. Client (15 checks): at 60 ticks
                     in the level it drives MultiPlayerGameMode (right click block and item, also canceled; start
                     breaking; a canceled destroyBlock) and the empty clicks (an invoker mixin, events.mixins.json); at
                     100 ticks it checks ticks, level load, LoggingIn and the respawn's Clone, then disconnects and checks
                     LoggingOut and the level unload. Logs "[events] all N server|server player|client checks passed".
testmods/neoforge-compile.classtweaker  generated (root task generateTestmodClassTweaker) from META-INF/injected-interfaces.json
                     in NeoForge's jar. The registries testmod sets it as Loom's accessWidenerPath, so javac sees NeoForge's
                     extension interfaces on vanilla classes and emits the same calls a NeoForge-built mod has (e.g.
                     invokeinterface Registry.addAlias). Compile-time only; validateAccessWidener is off because NeoForge's
                     json names two classes 26.2 doesn't have. Regenerate after changing neoforge_version.
testmods/fabricprobe/ a plain Fabric mod (with Fabric API). Its main entrypoint sets system properties for the NeoForge testmods:
                     versionunbound.testprobe.fabricInitialized (`lifecycle`); and, for `registries`, that it could register
                     an item and a POI type (through Fabric's PoiHelper) after NeoForge's registration, saw NeoForge's item,
                     and that the NeoForge custom registry has Fabric's SYNCED and MODDED attributes.
```

The built `loader-0.1.0.jar` nests `neoforge-api-0.1.0.jar` under `META-INF/jars/`, and that nests bus, typetools, mergetool, NightConfig toml/core and maven-artifact.

**How mods load (since step 2).** The hooks sit where NeoForge's patches put them, except that construction and registration move ahead of Fabric's mod initialization (the decision in §1):
```
preLaunch (loader)          FMLLoader.create → discovery and sorting → addToClassPath → mixin configs → start the mod scan
server Main / client Main,  ServerModLoader.begin(false) / ClientModLoader.begin() = CommonModLoader.begin:
  after Bootstrap.validate()  construct mods (parallel; `neoforge` too: NeoForgeMod) → "Registry initialization":
                                NewRegistryEvent + DataPackRegistryEvent.NewRegistry → ModifyRegistriesEvent → RegisterEvent
                                per registry (attributes, data components, particles, vanilla's order, then the rest by
                                namespace); registries stay open (GameData.freezeData is a no-op here)
                              → "Config loading" (CLIENT on the client, then COMMON) → NeoForge.EVENT_BUS.start()
Fabric                      every Fabric mod's onInitialize (+Client/Server); then Fabric API freezes the registries
                            (bake callbacks run; vanilla validation skips the NewRegistryEvent registries)
server: before `new DedicatedServerSettings` (NeoForge's spot) → ServerModLoader.load()
client: Minecraft.<init>, after registerPanoramaTextures (NeoForge's spot) → ClientModLoader.finish()
                            = CommonModLoader.load: Common setup → Sided setup → "Registration events" (data map types;
                              the rest are placeholders) → Enqueue IMC → Process IMC → FMLLoadCompleteEvent
                              → "Network registry lock" (placeholder)
```
`--initSettings` on the server skips mod loading, as in NeoForge. A `ModLoadingException` on the server propagates out of the hook to vanilla's "Failed to start the minecraft server", and the process exits. On the client, vanilla writes a crash report (description "Bootstrap" for errors in `begin`, "Initializing game" for errors in `finish`) and exits with -1. Both paths were checked with the `brokendeps` jar (client: `begin` only).

**Where Layer A deviates from upstream.** Everything else under `neoforge-api/src/{main,client}/java/net/neoforged` is byte-identical to `src/mod_Src/main/FML-11.0.16` or to the NeoForge 26.2 clone, which keeps future diffs cheap. Each deviation has a `VersionUnbound:` comment.

NeoForge (`NeoForge`, `NeoForgeBindings`, the services file and the two extension interfaces are verbatim):
- `internal/CommonModLoader`: only the network registry lock (step 7) is still a placeholder.
- `internal/RegistrationEvents.init()`: only `RegistryManager.initDataMaps()` runs; the other calls are commented out until their features land (steps 4, 5 and 9).
- `common/NeoForgeMod`: a partial port that grows with each step. Today: `MOD_ID`, the logger, the crash report callables, the holder set types (`HOLDER_SET_TYPES` and `ANY/AND/OR/NOT_HOLDER_SET`) and `NeoForgeRegistriesSetup.setup`. Its version comes from FML's `VersionInfo` (no `NeoForgeVersion`). The class comment lists what's missing.
- Registries (step 3). NeoForge's `MappedRegistry` extends `BaseMappedRegistry`; a mixin can't change a superclass, so `BaseMappedRegistry` isn't ported. `MappedRegistryMixin` adds its members, and code that cast to it casts to `MappedRegistryExtension`.
  - `RegistryBuilder.create`, `NeoForgeRegistriesSetup.modifyRegistries`: `setMaxId`/`setSync` through `MappedRegistryExtension`. `setSync(true)` sets Fabric API's `SYNCED` registry attribute, and `doesSync()` reads it.
  - `NewRegistryEvent.fill`: doesn't unfreeze or freeze the root registry. It's still open (Fabric API freezes after Fabric's mod init), and Fabric mods add registries in `onInitialize`.
  - `GameData`: `unfreezeData`/`freezeData` only log (see "How mods load"). `getBlockItemMap`, `getBlockStateIDMap` and `getBlockStatePointOfInterestTypeMap` return vanilla's `Item.BY_BLOCK`, `Block.BLOCK_STATE_REGISTRY` and `PoiTypes.TYPE_BY_STATE`: NeoForge's patches make those vanilla fields GameData's maps, and Fabric API keeps vanilla's up to date. The flower pot tables, `vanillaSnapshot` and the rollback to vanilla aren't ported. After `RegisterEvent`, the calls into entity attributes, spawn placements, `BlockEntityTypeAddBlocksEvent`, creative tab sorting, game rule categories and the tooltip handler are commented out (steps 4 and 5). The registration order reads `BuiltInRegistries.LOADERS` through an accessor.
  - `RegistryManager`: no snapshots or network parts (`takeSnapshot`, `applySnapshot`, `revertTo*`, `generateRegistryPackets`, the data map negotiation). Fabric API's registry sync does that job. Added `getNewRegistryKeys()` (the registries `NewRegistryEvent` added), for `BuiltInRegistriesMixin`.
  - `NeoForgeRegistryCallbacks`: only `PoiTypeCallbacks` (filling vanilla's map). Fabric API already does what the block, item and attribute callbacks do (block state ids, block items, shape caches, the debug world's states); attribute validation comes with entity attributes.
  - `NeoForgeRegistries`/`NeoForgeRegistriesSetup`: only `ENTITY_DATA_SERIALIZERS` and `HOLDER_SET_TYPES`. The other registries (and `Keys`) are commented out until their entry types are ported: loot modifiers, biome/structure modifiers (+ the datapack `BIOME_MODIFIERS`/`STRUCTURE_MODIFIERS` keys), fluid types, ingredient and fluid ingredient types, conditions, attachments.
  - `DataPackRegistryEvent`/`DataPackRegistriesHooks`: registration goes to Fabric API's `DynamicRegistries.register`/`registerSynced`, which do what NeoForge's patches to `RegistryDataLoader` and `RegistrySynchronization` do. The `RegistryBuilder` consumer of the four-argument `dataPackRegistry` can't live in vanilla's `RegistryData` record, so it's kept by registry key and `RegistryLoadTaskMixin` applies it. `getSyncedRegistry` searches vanilla's `SYNCHRONIZED_REGISTRIES`; `grabNetworkableRegistries` isn't ported.
  - `DeferredRegister.addEntries`: adds aliases after the entries, since Fabric's `addAlias` warns about targets that aren't registered yet.
  - `datamaps/DataMapType`: its javadoc names `neoforge:value` instead of linking `ConditionalOps` (not ported). Only the data map types and their event are ported, not the loader.
  - `holdersets/AnyHolderSet`, `NotHolderSet`: call `RegistryOpsPatch.retrieveRegistryLookup` (a static method NeoForge adds to `RegistryOps`; see §5 on static methods).
- What the mixins and class tweaker reproduce from NeoForge's patches: `MappedRegistry` (callbacks, `maxId`, data maps, `getId(key/name)`, `containsValue`, `resolve` through Fabric's aliases, `getKeyOrNull` without the default), `Holder` (un-sealed; `Reference` gets `getData`, the non-allocating `getKey`, key-based `equals`/`hashCode` and `unwrapLookup`), `HolderSet.Named` (invalidation listeners), `Registry.safeCastToReference` (unwraps `getDelegate()`), `RegistryLoadTask` (the builder consumer), `Identifier.compareNamespaced`, the data map getters on `HolderLookup.RegistryLookup`. Not reproduced: the explicit-id `MappedRegistry.register(int, …)`, `unfreeze`, `clear` and `registerIdMapping` (snapshot application only). NeoForge's patches drop two checks in `freeze` and keep intrusive holders around for refreezing; neither applies here, since registries freeze once.
- Two of NeoForge's `MappedRegistry` changes are Fabric API's own already and are left to it: holders get their value as soon as they're registered, and aliases (Fabric's `addAlias` has NeoForge's signature).
- Events (step 4). The event classes are verbatim. The hook classes are ported a method at a time, like `NeoForgeMod`: `EventHooks`, `CommonHooks` (empty so far; event javadoc links to it), `client/ClientHooks` (client ticks) and `server/ServerLifecycleHooks` (lifecycle, `getCurrentServer`, SERVER config load and unload; not biome/structure modifiers, `LanguageHook`, permissions, the JSON-RPC schema event). The mixins fire events at NeoForge's patch sites; where a site differs, the mixin says why:
  - `ServerStoppingEvent`: NeoForge posts it at the end of `runServer`'s try block. The nearest instruction (`stopped = true`) is inside the try around `stopServer()`, so `MinecraftServerMixin` logs a listener's exception instead of letting it skip `stopServer` and the world save.
  - Level load for the other dimensions wraps `Map.put` in `createLevels` (Fabric API wraps the same call for `ServerWorldEvents.LOAD`); level unload wraps `ServerLevel.close()`, so it fires only when the close succeeded, as in NeoForge.
  - Server about to start fires just before `loadLevel()`, after vanilla's "Preparing level" log line (NeoForge: just before it).
  - `CommandEvent` wraps `performCommand` with `@WrapMethod`.
  - Not ported from those patches: the dedicated server's LAN pinger and console handler, per-level tick times, `getWorldArray`, the status JSON cache, branding.
  - Interaction (4b). The use-block and use-item results of `RightClickBlock` override vanilla's choices by changing the values vanilla decides with (its `suppressUsingBlock` local, and the `isEmpty`/`isOnCooldown` checks before using the item), on both sides. `@Share` carries each event from the injector that fires it to the ones that apply it. The parts of these patches that call extension methods wait for step 5, with vanilla's behavior (what the defaults do) in their place: `onItemUseFirst`, `doesSneakBypassUse`, `onLeftClickEntity` (in `CommonHooks.onPlayerAttackTarget`, commented out), and in `destroyBlock` the removal through `onDestroyedByPlayer` after the tool is used with `canHarvestBlock`. Also waiting: `PlayerDestroyItemEvent`, the persisted part of the player's persistent data on clone, attachment sync at login/respawn/dimension change, `InputEvent.InteractionKeyMappingTriggered` around the clicks (Phase 5), the client block extensions' hit sound and `shouldCauseBlockBreakReset`.
  - The client's `startDestroyBlock` fires the survival left click in the method and applies it in its prediction lambda, so the event goes through a field (`startPrediction` runs the lambda right away).
  - `ClientPacketListenerMixin` uses `Minecraft.getInstance()` where NeoForge reads the listener's `minecraft` field: that field is declared in `ClientCommonPacketListenerImpl`, and `@Shadow` only finds fields declared on the target class.
  - `common/ItemAbilities` (needed by `BlockEvent`): its two imports of step 5's extension interfaces, used only by javadoc links, are commented out.
- `server/loading/ServerModLoader`: `load(boolean)` is split into `begin(boolean)` and `load()` for the two hook points. `LanguageHook` and the crash report dump (`CrashReportExtender`) aren't ported.
- `client/loading/ClientModLoader`: only `begin()` and `finish()`. Missing: the log4j shutdown hook (Fabric keeps log4j's own), the early loading screen, `LanguageHook`, `setupModResourcePacks` (step 8), the client network registry lock (step 7), update checks, the loading-warnings screen and FML's fatal error window.

FML 11.0.16:
- `fml/loading/FMLLoader` is rewritten. It keeps the public API: `getCurrent`/`getCurrentOrNull`, `getDist`, `isProduction`, `getGameDir`, `getLoadingModList`, `getVersionInfo`, `getModFileByClass`, `getCurrentClassLoader`, static `getLanguageLoadingProvider`, `getBindings` (ServiceLoader on Knot), `getGameLayer` (returns the boot layer), and the `backgroundScanHandler` field. Its internal entry points are `create(...)`, `discoverMods(minecraftPath, neoForgePaths, jars)` and `startModScan()`. `discoverMods` turns each jar into a `ModFile` (failures become `fml.modloadingissue.brokenfile` issues). It adds the in-memory system mods: `minecraft` (26.2: empty contents, the `minecraft` language loader, no event bus) and, since step 3, `neoforge` (26.2.0.93: `javafml`, its contents are Layer A's own jar or dev folders, so the scan finds `@Mod("neoforge")` on `NeoForgeMod` and it gets a mod bus, as in NeoForge). Then it runs `ModSorter.sort` and `identifyLanguage`. `startModScan` starts `BackgroundScanHandler`; the loader calls it last in `preLaunch`. Not ported: ProgramArgs, early window, class transformers, `close()`.
- `ModFile`: no `JarModuleInfo`/`ModuleDescriptor`. `getPackages()` replaces `getModuleDescriptor().packages()`; `LoadingModList`'s package index (one-line change there) is its only user. A file without mods takes its id from the file name.
- `IModFile`: the `create(...)` overloads that take a `JarModuleInfo` are dropped. `IModFileCandidateLocator.forFolder` is dropped (no locator plugins).
- `ModFileParser.MODS_TOML` replaces `JarModsDotTomlModFileReader.MODS_TOML` (the reader pipeline isn't ported).
- `LanguageProviderLoader` takes its providers as a list (`javafml`, `minecraft`) instead of loading services. `BuiltInLanguageLoader.version()` always returns `FMLVersion` (11.0.16), and mods' `loaderVersion` ranges are checked against that.
- `FMLModContainer`'s module is Knot's unnamed module (that keeps `AutomaticEventSubscriber.inject(..., Module)` intact), and it loads classes with `Class.forName(name, false, loader)`.

**Verified step 1 output** (dedicated server, `hello` + `metadata`):
```
(VersionUnbound) Found NeoForge mod file hello-0.1.0.jar with mods [hello (VersionUnbound Hello) @ 0.1.0]
(VersionUnbound) Found NeoForge mod file metadata-0.1.0.jar with mods [metadata (VersionUnbound Metadata) @ 1.2.3]
[modloading-worker-0] (HelloMod) [hello] constructed: mod=hello, dist=DEDICATED_SERVER
[modloading-worker-0] (MetadataMod) [metadata] mod list (4 mods, in load order): [minecraft, hello, metadata, neoforge]
[modloading-worker-0] (HelloMod) [hello] common setup
[modloading-sync-worker] (HelloMod) [hello] deferred work ran
[modloading-worker-0] (MetadataMod) [metadata] all 47 checks passed
[Server thread] (HelloMod) [hello] mixin applied: MinecraftServer.loadLevel
(Minecraft) Done (0.183s)!
```
Negative check (done by hand, not a testmod): a jar that requires a missing mod and a too-new `hello`. FML's ModSorter rejects the set, and `onInitialize` throws `ModLoadingException: Loading errors encountered: - Mod brokendeps requires missing_dep 2.0 or above / Currently, missing_dep is not installed ...`, so the server doesn't start. That matches NeoForge.

**Verified step 2 output.** Dedicated server, with `lifecycle`, `hello`, `metadata` and `fabricprobe`:
```
[modloading-worker-0] (LifecycleMod) [lifecycle] PASS constructed before Fabric initializes its mods
[modloading-sync-worker] (LifecycleMod) [lifecycle] PASS config handed to the spec
[main] (FabricProbe) [fabricprobe] Fabric is initializing its mods
[modloading-worker-0] (LifecycleMod) [lifecycle] PASS common setup after Fabric initializes its mods
[modloading-worker-0] (LifecycleMod) [lifecycle] PASS registries frozen by common setup
...
[modloading-worker-0] (MetadataMod) [metadata] all 47 checks passed
[modloading-worker-0] (LifecycleMod) [lifecycle] all 11 checks passed
[Server thread] (HelloMod) [hello] mixin applied: MinecraftServer.loadLevel
(Minecraft) Done (0.331s)!
```
Client (Xvfb): the same order, with `[fabricprobe]` on the Render thread inside `Minecraft.<init>`. `[lifecycle] all 12 checks passed` comes before "Reloading ResourceManager". The client then builds all its texture atlases and starts the sound engine, with no errors, until it's stopped. Negative check, repeated with the new hooks: the `brokendeps` jar now fails from `ServerModLoader.begin` inside `Main.main` (stack: `Main.handler$…$versionunbound$beginModLoading`). Vanilla logs "Failed to start the minecraft server" with the same FML message, and the process exits. On the client the same jar produces a "Bootstrap" crash report in `loader/run/crash-reports/`.

**Verified step 3 output.** Dedicated server, with `registries`, `lifecycle`, `hello`, `metadata` and `fabricprobe`:
```
[modloading-worker-0] (NeoForgeMod) NeoForge mod loading, version 26.2.0.93, for MC 26.2
[modloading-sync-worker] (Checks) [registries] PASS DeferredItem bound by its registry's RegisterEvent
[main] (FabricProbe) [fabricprobe] Fabric is initializing its mods
[main] (RegistriesMod) [registries] shapes registry baked, 1 time(s); ids [circle=0, square=1]
[modloading-worker-0] (MetadataMod) [metadata] all 47 checks passed
[modloading-worker-0] (LifecycleMod) [lifecycle] all 11 checks passed
[modloading-worker-0] (Checks) [registries] all 45 registration checks passed
[Server thread] (Checks) [registries] all 4 world checks passed
(Minecraft) Done (0.278s)!
```
The bake comes from Fabric API's freeze, between Fabric's mod init and common setup. Client (Xvfb): `metadata` 47, `lifecycle` 12 and `registries` 45 pass. The only new warnings are the missing model and sound for `registries:probe_block`/`probe_sound`: the testmod ships no assets, and NeoForge mods' resources aren't mounted yet (Known gap 7). **Registry sync, end to end** (client joining the dedicated server, §3): the client connects, and its bake callback runs again during the join (`baked, 2 time(s); ids [circle=0, square=1]`). That's Fabric API's registry sync remapping the NeoForge custom registry, with `MappedRegistryMixin` running NeoForge's clear and bake callbacks on the remap. The server logs the player joining, and nothing disconnects.

**Verified step 4a output.** Dedicated server with all testmods (`events`, `registries`, `lifecycle`, `hello`, `metadata`, `fabricprobe`), on the existing dev world and on a freshly generated one:
```
[Server thread] (ServerEvents) [events] server event order: [commands, config, aboutToStart, load:overworld, spawn,
    load:the_nether, load:the_end, save:overworld, save:the_nether, save:the_end, starting, started, tick, stopping,
    shuttingDown, save:overworld, save:the_nether, save:the_end, unload:overworld, unload:the_nether, unload:the_end, stopped]
[Server thread] (Checks) [events] all 30 server checks passed
[Server thread] (Checks) [events] all 1 server player checks passed      (with a client joined)
[Render thread] (Checks) [events] all 5 client checks passed             (client joined to the dev server, §3)
```
`spawn` (CreateSpawnPosition) appears only for a new world. The other testmods still pass. The client run used the script described in §3 (start the server, wait for "Done (", join with `--quickPlayMultiplayer`, wait for the client summary, kill the client JVM, send `stop` once the server logs "left the game"; also send `stop` if the client dies first, or the server waits five minutes for a player who never comes).

**Verified step 4c so far** (client joined to the dev server, all testmods): server `[metadata] all 47`, `[lifecycle] all 11`, `[registries] all 45` + `4 world checks`, `[events] all 56 server checks passed`, `all 25 server player checks passed`; client `[metadata] all 47`, `[lifecycle] all 12`, `[registries] all 45`, `[events] all 16 client checks passed`.

**Verified step 4b output** (client joined to the dev server):
```
[Render thread] (Checks) [events] all 15 client checks passed
[Server thread] (Checks) [events] all 30 server checks passed
[Server thread] (Checks) [events] all 20 server player checks passed
```

### Known gaps (updated after step 3)
1. ~~`ModContainer(String)`~~ **Fixed.** `ModContainer` is verbatim, including `registerConfig`, which goes to FML's `ConfigTracker` (ported verbatim). Since step 2, COMMON and CLIENT configs load at NeoForge's point in `begin` (the `lifecycle` testmod checks this with its own minimal `IConfigSpec`). Since step 4a, SERVER configs load at server about to start and unload after server stopped (`ServerLifecycleHooks`; the `events` testmod checks it). Still missing: NeoForge's `ModConfigSpec` and config sync to clients (step 6).
2. ~~Errors wrapped in `RuntimeException`~~ **Fixed:** `ModLoadingException`/`ModLoadingIssue` and FML's translations, all verbatim.
3. ~~`DeferredWorkQueue` without active container~~ **Fixed** (verbatim).
4. ~~Lifecycle timing~~ **Fixed in step 2:** NeoForge's full sequence at NeoForge's hook points (see "How mods load" above). Registry initialization is real since step 3. Still placeholders: most of "Registration events" (steps 4, 5 and 9) and the network registry locks (step 7).
5. **Discovery.** FML's `ModSorter` now does dependency/version resolution and ordering, including `config/fml.toml` `dependencyOverrides`. `[[mixins]]` are handled per file, with `requiredMods`/`behaviorVersion`. Still missing:
   - JarJar.
   - The refuse list.
   - Applying `[[accessTransformers]]`: `ModFile.getAccessTransformers()` parses them, but nothing applies them.
   - `enumExtensions` (FML's `RuntimeEnumExtender`; `EnumProxy` is used by 3 of the test jars).
6. **No transformation or cache yet.** Jars are loaded as-is. The `transformer`, `cli` and `upgrade-1.21.1` subprojects don't exist yet.
7. **Fabric doesn't know about NeoForge mods.** They aren't Fabric mods, so they're missing from `FabricLoader.getAllMods()`, ModMenu and so on, and their jar resources aren't mounted as resource or data packs. That's needed as soon as a mod ships assets or data.
8. ~~No `IBindingsProvider`~~ **Fixed in step 2:** `NeoForge.EVENT_BUS`, `NeoForgeBindings` and the services file, all verbatim. `@EventBusSubscriber` methods for game-bus events register, and the bus starts at the end of `begin`. Step 4 posts game events (4a: server lifecycle, commands, ticks, levels; see §8 for what's left).
9. **Crash reports.** `CrashReportCallables` are collected (ModList registers "Mod List"), but nothing writes them into Minecraft's crash reports. NeoForge's `CrashReportExtender` (including the mod loading crash dump the server writes on errors) isn't ported.
10. **ModList lists only NeoForge mods plus `minecraft` and `neoforge`**, by design. If a NeoForge mod saw a Fabric mod's id it would enable integration code that expects the NeoForge build of that mod. Revisit if a specific integration needs it.
11. ~~Only the dedicated server has run the loader~~ **Fixed in step 2:** the client runs under Xvfb (§3).
12. **Only two launch paths are hooked:** the dedicated server's `Main` and the client's `Main`/`Minecraft`. NeoForge also loads mods from the GameTest server (`GameTestMainUtil`, `ServerModLoader.load(true)`) and from datagen. Neither is needed to run mods.
13. **For Phase 5: `ClientBootstrap.bootstrap()`.** Vanilla calls it in `client/main/Main` before `Bootstrap.validate()`, so before mods are constructed. NeoForge moves it into `Minecraft.<init>`, after `setupModResourcePacks`, so that the item-model registration events it fires reach mods. Do the same when those events are ported.
14. **No server-side translations (`LanguageHook`).** NeoForge loads its own and mods' `en_us` into the server's language table, so server-side `Component.translatable` text and translated loading errors work without a client.
15. **Registries, still missing after step 3:**
   - Six of NeoForge's own registries (and two datapack keys) wait for their entry types; see the `NeoForgeRegistries` deviation in §4. A mod using `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` and the like gets `NoSuchFieldError` until then.
   - `NeoForgeMod` holds only the holder set types; its other fields (attributes such as `SWIM_SPEED`, milk, vanilla fluid types, ingredient types, …) come with their features. The corpus uses several of them (§7).
   - Data maps: types, `RegisterDataMapTypesEvent` and the getters work, but nothing loads data map files (`DataMapLoader` needs conditions and reload listeners, step 8), nothing syncs them (step 7), and NeoForge's built-in data maps (`NeoForgeDataMaps`: compostables, furnace fuels, waxables, …) aren't ported.
   - Holder sets: the four custom types are registered and work as objects, but codecs don't know them yet (NeoForge's `HolderSetCodec` patch).
   - `ClearCallback`s run only on Fabric's remaps (with `full = false`); NeoForge also runs them, with `full = true`, when applying snapshots, which don't exist here.
   - Aliases are Fabric API's. Unlike NeoForge, registering a name that is already an alias throws, and `resolve` of an alias whose target isn't registered returns the alias itself.
   - Fabric API initializes a block's shape cache as soon as the block is registered. NeoForge waits for the registry bake, because a mod's block can depend on entries registered after it. Watch for this if a mod's block misbehaves.
   - Static methods NeoForge adds to vanilla classes (like `RegistryOps.retrieveRegistryLookup`, `BuiltInRegistries.getVanillaRegistrationOrder`) can't be added by a mixin (Mixin rejects public static methods). Our own code calls a stand-in, but mods calling them get `NoSuchMethodError`. Step 5 needs a way to add them, e.g. a mixin config plugin that adds forwarding methods in `postApply`.
16. **Testmods see NeoForge's extension interfaces, but not its access transformers or the methods its patches add directly** (e.g. `Holder.Reference.getData`, which they reach through the `Holder` interface instead). The compile class tweaker is generated from `injected-interfaces.json` only; adding NeoForge's AT files to it is part of step 5.

---

## 5. Facts learned (verified)

- Since 1.20.5, NeoForge 1.21.1 jars use **Mojang names**, and 26.2 is unobfuscated. **No name remapping between 1.21.1 and 26.2 is needed**, only API upgrading.
- **Late mixin registration works.** `Mixins.addConfiguration` in a Fabric `preLaunch` entrypoint, after `addToClassPath`, applies to classes loaded afterwards. Plain NeoForge mixin JSON works with no Fabric-specific decoration.
- `FabricLauncherBase.getLauncher().addToClassPath(Path, String... allowedPrefixes)` is the call to use. `fabric.addMods` (`SystemProperties.ADD_MODS`) exists but isn't needed.
- Fabric Loader tolerates non-Fabric jars in `mods/` and only warns about them.
- The event bus is the **same major version**: NeoForge 1.21.1 used bus 8.0.2, 26.2 uses 8.0.5. Bundle `net.neoforged:bus` as-is; it needs typetools, ASM and log4j-api, and Minecraft already provides the last two. `GenericEvent` is used in 1.21.1 but not 26.2 (a Layer B item).
- **FML is a major jump**: 4.0.45 (1.21.1) → 11.0.16 (26.2). FML is the part we reimplement, so port from `src/mod_Src/main/FML-11.0.16`.
- `Dist`/`OnlyIn` live in `net.neoforged:mergetool:2.0.7:api`, not in FML.
- In FML, a mod class needs exactly one public constructor. Its arguments are injected by type from `{IEventBus, ModContainer, FMLModContainer, Dist}`.
- Loom accepts `sourceSet('main', ':other-project')` inside `loom.mods {}` for multi-project dev runs.
- Mixin target that exists on both sides in 26.2: `MinecraftServer.loadLevel()` (protected). Also `runServer()` and `tickServer(BooleanSupplier)`; `DedicatedServer.initServer()`.
- **Knot doesn't implement `ClassLoader.findClass`**, so `Class.forName(Module, String)` returns null for mod classes. Use `Class.forName(name, false, knotLoader)`.
- **FML's mod order:** `UniqueModListBuilder` groups mod files in a `HashMap` keyed by mod id. `TopologicalSort` (Kahn's algorithm, ties broken by that order) then applies the `ordering` edges, so ties come out in HashMap order (`[minecraft, hello, metadata, neoforge]`). Real NeoForge gives the same order for the same ids.
- **NeoForge 26.2's startup sequence** (`neoforge/internal/CommonModLoader`):
  1. `ModLoader.gatherAndInitializeMods(ModWorkManager.syncExecutor(), parallelExecutor(), periodic)`: construction.
  2. `runInitTask` "Registry initialization": `RegistryManager.postNewRegistryEvent`, `GameData.unfreezeData`, `postRegisterEvents`, `freezeData`.
  3. "Config loading": CLIENT on the client, COMMON on both.
  4. `NeoForge.EVENT_BUS.start()`.

  Then `load()`: Common setup → Sided setup (`FMLClientSetupEvent`/`FMLDedicatedServerSetupEvent`) → "Registration events" → Enqueue IMC → Process IMC → `FMLLoadCompleteEvent` → "Network registry lock". `ModWorkManager.syncExecutor()` is its own single daemon thread (`modloading-sync-worker`), not the game's main thread. Mod constructors and parallel events run on `modloading-worker-N` ForkJoin threads.
- **FML's library needs:** MC 26.2 already ships commons-lang3 3.20.0 (it still has `text.StrSubstitutor`), commons-io 2.20.0, Guava 33.6 and Gson 2.14. Only NightConfig and maven-artifact must be bundled, and maven-artifact's `versioning` package doesn't need plexus-utils at runtime.
- **Fabric's Mixin** (sponge-mixin 0.17.4) has `FabricUtil.COMPATIBILITY_0_17_1` through `COMPATIBILITY_LATEST` (17004). FML uses the same decoration keys (`fabric-modId`, `fabric-compat`), so FML's mixin handling ports 1:1.
- Maven: NeoForge artifacts at `https://maven.neoforged.net/releases/`, Parchment at `https://maven.parchmentmc.org/` (1.21.1 latest: 2024.11.17), Fabric at `https://maven.fabricmc.net/`.
- **Where Fabric initializes mods, and when it freezes registries** (`src/mod_Src/main/fabric-api`, branch 26.2, `fabric-registry-sync-v0`):
  - `BootstrapMixin` redirects vanilla's `BuiltInRegistries.bootStrap()` to `createContents()` only. Vanilla registries stay open from bootstrap until Fabric API freezes them after Fabric's mod init.
  - Server: Fabric Loader calls `onInitialize`/`onInitializeServer` in `server/Main.main` just before `Util.startTimerHackThread()`. `MainMixin` freezes the registries at that call.
  - Client: the `main` and `client` entrypoints run in `Minecraft.<init>` just before `this.gameThread = Thread.currentThread()` (decompiled line 417). That's after `instance = this`, but before `Options`, the window, the resource and texture managers and the renderers exist. The client `MinecraftMixin` freezes at the same point.
- **NeoForge's loading call sites** (patches): the server calls `ServerModLoader.load(false)` between `Paths.get("server.properties")` and `new DedicatedServerSettings`. The client calls `ClientModLoader.begin()` after `Bootstrap.validate()` in `client/main/Main`, and `finish()` after `gameRenderer.registerPanoramaTextures(...)` in `Minecraft.<init>` (decompiled line 701).
- **Fabric API's own initializers don't need to run before NeoForge mods.** All 16 `ModInitializer`s in Fabric API 26.2 register only Fabric's own payload types, registry sync attributes, ingredient and resource-condition types, reload listeners and listeners for later phases. Fabric's menu API walks existing registry entries before it listens for new ones, so it also sees entries registered before Fabric's mods initialize.
- Mixin 0.17.4 accepts `compatibilityLevel` up to `JAVA_25`. MixinExtras (`@Local`, …) comes with Fabric Loader 0.19.5.
- Loom 1.18's `splitEnvironmentSourceSets()` works with the unobfuscated plugin, and another project's `loom.mods` can reference it with `sourceSet('client', ':neoforge-api')`.
- FML's mod order changes as mod ids are added (it comes from HashMap order, see above). With `lifecycle` installed it's `[lifecycle, minecraft, hello, metadata, neoforge]`.
- **Learned in step 3 (registries):**
  - **`Holder` is sealed in vanilla 26.2** (permits `Direct` and `Reference`); NeoForge's patch un-seals it for `DeferredHolder`. A class tweaker's `extendable class` drops `PermittedSubclasses` (class-tweaker's `AccessWidenerClassVisitor.visitPermittedSubclass`).
  - **Fabric Loader 0.19.5 applies mods' class tweakers at run time**, including `inject-interface` and `extend-enum` (its `ClassTweakerImpl.createClassVisitor` chains the access widener, interface injection and enum extension visitors). Loom applies the same file at compile time. That makes the class tweaker the tool for NeoForge's injected interfaces and ATs, and for FML's `enumExtensions` later. Generic syntax: `inject-interface<TAB>net/minecraft/core/Registry<TAB>net/neoforged/.../IRegistryExtension<TT;>`.
  - A class tweaker can't add methods. **Instance methods** NeoForge's patches add to vanilla classes go in an injected interface as default methods: `invokevirtual` and `invokeinterface` from mod code resolve to a superinterface's default method. **Mixin rejects public static methods** (`MixinApplicatorStandard.checkMethodVisibility`: "contains non-private static method"), so static additions need another route.
  - **When two injected interfaces declare the same method** (Fabric API's `FabricRegistry.addAlias` is a default, NeoForge's `IRegistryExtension.addAlias` is abstract), `invokeinterface Registry.addAlias` resolves to the default one and dispatches to the class's own implementation. A class compiled against both must declare the method itself (javac's "inherits abstract and default"), so our mixins don't `implements Registry`; they add the methods without `@Override`.
  - **Fabric API's `MappedRegistryMixin` already binds a holder's value at registration** (NeoForge's patch does the same) and **implements aliases** with NeoForge's `addAlias` signature. It runs `RegistryEntryAddedCallback`s, and its trackers (block states, block items, shape caches via `BlocksMixin`, the debug world's states) are registered during `Bootstrap.bootStrap`, so they also see entries registered in `begin`.
  - **Fabric registry sync** syncs a registry only if it has both `RegistryAttribute.SYNCED` and `MODDED`. Attributes are kept per `ResourceKey` (`RegistryAttributeHolder.get(key)`), so they can be set before the registry exists. `MODDED` is set automatically when an entry is registered after bootstrap. On the client, Fabric remaps ids in place and fires `RegistryIdRemapCallback` (on joining, and on leaving to restore local ids).
  - **Fabric's `DynamicRegistries.register`/`registerSynced`** are plain static calls without an initializer, so they work in `begin`.
  - **Fabric API calls `BuiltInRegistries.bootStrap()` a second time** when it freezes, which runs vanilla's `validate`: it logs an error for an empty registry and throws for a defaulted one without its default. NeoForge's modded registries never go through that on NeoForge.
  - **Mixin's `MixinProcessor.selectConfigs` removes configs from `Mixins.getConfigs()`** as it selects them, on whatever thread is loading a class. Every class Knot loads goes through it, including FML's scanner classes on the background scan threads. So mods' mixin configs must be added and decorated before any other thread can load classes (hence `startModScan` last), or they may be selected undecorated or throw `ConcurrentModificationException`.
  - **Interface mixins can hold injectors** with private handler methods (Fabric API's `ListBackedContainerMixin` does too); used for `Registry.safeCastToReference`.
  - **Item components are bound when datapacks load** (`ReloadableServerResources`; on the client in `RegistryDataCollector`). Before that, `new ItemStack(item)` throws "Components not bound yet", on NeoForge too, so stacks can't be made in setup events.
  - NeoForge's `injected-interfaces.json` (also in its universal jar) names two classes 26.2 doesn't have: `DeadBushBlock` (now `DryVegetationBlock`) and `IntrinsicHolderTagsProvider$IntrinsicTagAppender`.
  - Fabric's `PoiHelper` registers through vanilla's `PoiTypes.register`, which maps the block states itself after `Registry.register`. NeoForge maps them in a registry callback, so the two together map them twice (`PoiTypesMixin` skips states already mapped to the same type).
- **Learned in step 4 (events):**
  - **javac doesn't find abstract methods of an injected interface on a concrete class.** `Resolve.findMethod` skips superinterfaces' abstract methods when the receiver's class is concrete (it expects the class to implement them) and only searches interfaces that declare a default method. So an interface injected into a concrete vanilla class must give its methods default bodies (as `IdentifierExtension` and `WeightedListBuilderExtension` do); a mixin can supply the state through a second interface (`WeightedListBuilderExtension.Entries`). Abstract methods work only on interfaces like `Registry`.
  - **`MinecraftServer.runServer`'s bytecode:** the outer try ends right before the inlined finally block (`stopped = true; stopServer(); ... onServerExit()`), which javac copies three times (normal exit, catch, exception path), each with its own nested try. `@At(value = "FIELD", target = "...stopped:Z", opcode = PUTFIELD, ordinal = 0)` is the normal exit of the tick loop; `@At(value = "INVOKE", target = "...onServerExit()V")` without an ordinal hits every copy.
  - **`ClientTickEvent.Post` can fire without a `Pre`** on the tick where the game finishes loading: `LoadingOverlay.tick()` runs inside `Minecraft.tick()` and sets `gameLoadFinished`, which both of NeoForge's tick hooks check. NeoForge behaves the same.
  - **Commands are built when the server's resources load**, before the server object exists (dedicated: in `Main`, after `ServerModLoader.load()`; integrated: when the world opens), so `RegisterCommandsEvent` comes before `ServerAboutToStartEvent`. It fires again on `/reload`.
  - 26.2 has no spawn chunks to keep entities ticking on an empty server (no spawn chunk radius game rule); testmods force-load a chunk (`ServerLevel.setChunkForced`) and load it with `getChunk`. Entity type constants moved to `EntityTypes`. `net.minecraft.util.TriState` is vanilla in 26.2 (NeoForge's interaction events use it).
  - Fabric API fires its own lifecycle events at nearby points: `SERVER_STARTING` before `initServer`, `SERVER_STARTED` after the status is built (after NeoForge's started), `SERVER_STOPPING`/`STOPPED` at the head and tail of `stopServer`, server ticks before `tickChildren` and at the tail, `ServerWorldEvents.LOAD` on the `Map.put` in `createLevels`, and `CommandRegistrationCallback` at the same `setConsumer` call as `RegisterCommandsEvent`. They coexist with ours; none of them carries the `BooleanSupplier` NeoForge's tick events need.
  - In 26.2 NeoForge's block break event is `level.block.BreakBlockEvent` (1.21.1: `BlockEvent.BreakEvent`, a Layer B rename), fired by `CommonHooks.fireBlockBreak`. 26.2 also merged `EntityInteractSpecific` into `EntityInteract` (with a location), as vanilla merged `interactAt` into `interactOn`.
  - NeoForge's patches add methods to vanilla classes that its own events and mods call, beyond the extension interfaces: `TriState.isTrue/isDefault/isFalse`, `WeightedList.Builder.add/addAll/remove/removeIf/getList`. Look for `// Neo:` helper methods in a class's patch when a ported file doesn't compile.
  - **`@Shadow` only finds members declared on the target class**, neither a superclass's field (e.g. `ClientPacketListener`'s `minecraft` lives in `ClientCommonPacketListenerImpl`; `invulnerableTime` lives in `Entity`) nor an inherited method (`Player` doesn't declare `getDamageAfterArmorAbsorb`). The mixin fails to apply at class load, which crashes the game (on the client, Fabric's networking init). Use the member through a cast when it's public, `Minecraft.getInstance()`, or an interface that the superclass's mixin implements (`LivingEntityDamageContainers`).
  - **Synthetic lambdas are stable targets in the unobfuscated jar** (`lambda$startDestroyBlock$1`, `lambda$useItem$0`); list them with `javap -p`. The numbering follows the order of lambdas in the method, so recheck after a Minecraft update.
  - **`@ModifyVariable(at = @At("STORE"), ordinal = n)`** counts locals of that type in scope just after the store, so a boolean declared right after another is ordinal 1. The 26.2 jar keeps its LocalVariableTable (names and types), which `@Local` and these ordinals rely on.
  - Calling `PlayerList.respawn` directly, as the testmod does, leaves `connection.player` on the old player; vanilla's packet handler assigns respawn's result to it.
  - **`Entity.equals`/`hashCode` compare entity ids**, and a respawned `ServerPlayer` takes over the old player's id (`setId`), so maps keyed by entity must be identity maps.
  - **A chunk force-loaded this tick isn't visible to entity lookups yet**: `getChunk` brings it to FULL at once, but the entity manager learns the chunk's status a tick later, so `ServerLevel.getEntity(uuid)` (the visible entity storage) can't see entities added there in the same tick.
  - Server-side interaction methods (`ServerPlayerGameMode.handleBlockBreakAction`/`destroyBlock`/`useItemOn`/`useItem`, `Player.interactOn`/`attack`) can be called directly for a connected player; spawn protection only applies in the network handler and in `handleBlockBreakAction` after NeoForge's hook.

---

## 6. Connector as a template

`src/mod_Src/main/SinytraConnector` (Fabric mods on NeoForge, MIT):
- `src/main/java/org/sinytra/connector/locator/`: finds foreign jars (`ConnectorLocator`, `ConnectorModFileReader`, `SplitPackageMerger`).
- `transformer/src/main/java/.../transformer/`: the jar transformation pipeline.
  - `jar/JarTransformer`, `JarTransformInstance` (cached transforms)
  - `transform/MixinPatchTransformer`, `AccessorRedirectTransformer`, `FieldToMethodTransformer`, `InheritingClassWriter`, `JarSignatureStripper`
  - `patch/` (refmaps, environment stripping)
  - `plugin/` (a plugin API for per-mod patches)
- `transformer/src/runner/`: `cli/Main`, `PortableTransformerFrontend`, and the "probe" runtime. This is the template for our `cli`.
- `src/mod/.../mixin/`: runtime fix-up mixins.
- External libraries do most of the heavy lifting: Forgified Fabric API (the reverse of our Layer A), Adapter (auto-patches mixins), class-tweaker (AW/AT handling), ART.

---

## 7. Test corpus and measurements

Jars live in `src/mod_Jars_1-21-1_Neoforge/<Mod>/`. "MC refs" counts the distinct vanilla classes a jar references; "missing in 26.2" counts those that exist in 1.21.1 but not 26.2. That's a class-level lower bound and ignores changed members.

| Jar | Classes | MC refs | Missing in 26.2 | NeoForge classes | Notes |
|---|---|---|---|---|---|
| Chunky-NeoForge-1.4.23 | 130 | 47 | **1** | 8 | **First Layer B target** |
| toms_storage-1.21-2.4.2 | 252 | 207 | 25 | 63 | no mixins |
| FarmersDelight-1.21.1-1.3.4 | 292 | 489 | 76 | 111 | |
| kaleidoscopecookery-1.6.0 | 476 | 427 | 66 | 120 | |
| dynamictrees-neoforge-1.21.1-1.7.2 | 725 | 375 | 55 | 85 | |
| Zeta-1.1-40 / Quark-4.1-488 | 616 / 974 | 325 / 900 | 53 / 138 | 114 / 83 | Quark needs Zeta |
| create-1.21.1-6.0.10 | 2692 | 841 | 108 | 202 | JarJar: Flywheel, Ponder, Registrate… |
| architectury-13.0.11 / powergrid-0.6.2 | 382 / 1057 | 221 / 367 | 22 / 52 | 111 / 75 | PowerGrid also needs Create |
| sable-neoforge-2.0.6 (+ create-aeronautics-bundled) | 826 | 433 | 39 | 46 | the bundled jar is all JarJar |
| Cobblemon-neoforge-1.8.4 (+ kotlinforforge-5.12.0-all) | 15743 | 784 | 87 | 81 | Kotlin language provider |
| cloth-config-15.0.140 | 633 | — | — | — | library (for Immersive Portals) |
| *out of scope:* embeddium, sodium, iris, immersive_portals, firstperson, notenoughanimations | | | | | refuse list |
| SinytraConnector/connector…, forgified-fabric-api… | | | | | reference only, not test targets |

The vanilla gap: 1,441 of 8,269 classes in 1.21.1 are gone or renamed in 26.2, which has 10,952 classes. The **most-referenced missing classes** (number of jars) and their 26.2 successors:
- **Renames or moves** (rename map):
  - `ResourceLocation` (18) → `net/minecraft/resources/Identifier`
  - `Util` (13) → `net/minecraft/util/Util`
  - `FastColor` (9) → `util/ARGB`
  - `GameRules` (9) → `level/gamerules/GameRules`
  - `MobSpawnType` (6) → `entity/EntitySpawnReason`
  - `UseAnim` (6) → `item/ItemUseAnimation`
  - `RenderType` (15) → `client/renderer/rendertype/RenderType` (also changed semantically)
- **Semantic replacements** (shims):
  - `InteractionResultHolder` (11) and `ItemInteractionResult` (9) → `InteractionResult`
  - `DirectionProperty` (12) → `EnumProperty<Direction>`
  - `Tier` / `ArmorItem` / `ArmorMaterial` → the components-based tool and armor system
  - `LootContextParam`, `SimpleCraftingRecipeSerializer`, the predicates under `advancements/critereon`, `VillagerProfession`, `DimensionDataStorage`
- **Client redesign** (Phase 5):
  - `GuiGraphics` (14) → `GuiGraphicsExtractor`
  - `MultiBufferSource` (16) / `RenderType` / `RenderStateShard` → `SubmitNodeCollector` and feature renderers
  - `BakedModel` (13) / `BakedQuad` / `ModelResourceLocation` → the new block/item model pipeline
  - `ShaderInstance` (11) → removed (render pipelines)
  - `ItemProperties` / `ItemColor` / `BlockColor` → item model definitions and tints
  - `LightTexture`, `ItemRenderer`, `BlockRenderDispatcher`

**NeoForge packages by number of jars referencing them** (Layer A priority):
1. `fml.common` 18, `fml.loading` 17, `api.distmarker` 17, `bus.api` 16
2. `neoforge.client.event` 15, `fml` 15, `neoforge.common` 14, `registries` 13, `event` 13, `fml.event.lifecycle` 12
3. `event.level` 11, `event.entity.player` 11, `event.server` 10, `common.util` 10, `client.gui` 10, `client` 10
4. `network.registration` / `.handling` / `.event` 9 each, `common.world` 9, `capabilities` 9, `neoforgespi.language` 8
5. `items` 8, `event.tick` 8, `common.extensions` 8, `fml.config` 8, then fluids, `entity.living`, `client.model.data`, `client.extensions.common`, `neoforgespi.locating`, `server`, `data.event`, `client.model.generators`, `fml.loading.moddiscovery`, `fml.event.config`

**NeoForge events and hooks the corpus uses** (constant-pool scan of the 14 in-scope jars, nested jars included; number of jars, 1.21.1 names):
- Game bus: `PlayerInteractEvent` 10 (`RightClickBlock` 9, `EntityInteract` 6, `LeftClickBlock` 5, `RightClickItem` 4, `LeftClickEmpty` 3, `EntityInteractSpecific` 2, `RightClickEmpty` 1), `AddReloadListenerEvent` 9, `ItemTooltipEvent` 9, `BlockEvent` 9 (`BreakEvent` 5, `EntityPlaceEvent` 4, `NeighborNotifyEvent` 3, `BlockToolModificationEvent` 3, `FarmlandTrampleEvent` 2), `RegisterCommandsEvent` 8, `EntityJoinLevelEvent` 6, `ServerAboutToStartEvent` 6, `ServerTickEvent` 6, `PlayerLoggedInEvent` 5, `ServerStoppingEvent` 5, `LevelTickEvent` 5, `LevelEvent.Unload` 5, `VillagerTradesEvent` 5, then 4 each: `LivingChangeTargetEvent`, `LivingDeathEvent`, `LivingIncomingDamageEvent`, `PlayerLoggedOutEvent`, `ChunkEvent`, `LevelEvent.Load`, `ServerStoppedEvent`, `EntityTickEvent`, `WandererTradesEvent`, `BuildCreativeModeTabContentsEvent`; 3: `EntityLeaveLevelEvent`, `FinalizeSpawnEvent`, `ChunkDataEvent`, `ServerStartingEvent`, `PlayerTickEvent`, `LootTableLoadEvent`, `OnDatapackSyncEvent`, `AddPackFindersEvent`.
- Hook methods called directly: `ServerLifecycleHooks.getCurrentServer()` 6, `CommonHooks.canCropGrow`/`fireCropGrowPost` 3, `EventHooks.onNeighborNotify` 2, then single uses (`CommonHooks.fireBlockBreak`, `onRightClickBlock`, `onLeftClickBlock`, `onInteractEntity`, `onPlayerAttackTarget`, `EventHooks.onBlockPlace`, `getBreakSpeed`, `doPlayerHarvestCheck`, ...).

Reproduce the measurement (read-only). Set `C=~/.cache/mc-src/jars`, then:
```bash
m26=$(unzip -Z1 $C/26.2-client.jar '*.class' | sed 's/\.class$//' | sort -u)
m121=$(unzip -Z1 $C/1.21.1-client-mojmap.jar '*.class' | sed 's/\.class$//' | sort -u)
unzip -p JAR '*.class' | grep -aoE 'net/minecraft/[A-Za-z0-9_/$]+' | sed 's/\$.*//' | sort -u \
  | comm -23 - <(echo "$m26") | comm -12 - <(echo "$m121")      # missing vanilla classes
unzip -p JAR '*.class' | grep -aoE 'net/neoforged/[a-z0-9_/]+/' | sort -u   # NeoForge packages used
```
Phase 3 replaces this with the proper ASM **reference scanner** in `cli`, which checks member-level references against MC 26.2, Fabric API, Layer A and the Layer B shims.

---

## 8. Next steps — Phase 2 (Layer A core)

Work in this order. Each step gets a **genuine NeoForge 26.2 testmod** under `testmods/`, compiled against the real NeoForge artifacts like `hello` is, and a log line or assertion checked on `runServer` and `runClient` (§3). Add each testmod to `settings.gradle` and to `copyTestMods` in `loader/build.gradle`. Remember the rule from §1: nothing that runs in `begin` (construction, `RegisterEvent`, config loading) may rely on a Fabric initializer.

1. ✅ **FML metadata and context** (done 2026-10-28; see §4 and Known gaps). Original task: port `neoforgespi` `IModInfo`/`IModFileInfo`/`IModFile` (only what mods use), `ModList` (`get()`, `isLoaded`, `getModContainerById`, `getModFileById`, `getMods`), `ModLoadingContext`, `FMLEnvironment`/`FMLLoader` (`getDist`, `isProduction`, `getCurrent()` as in 11.x), `FMLPaths` (`CONFIGDIR`, `GAMEDIR`, `MODSDIR`), `ModLoadingException`/`ModLoadingIssue`, and `InterModComms` with the IMC events. Restore the real `ModContainer` signatures (Shortcut 1). Make `NeoModDiscovery` read the full TOML.
2. ✅ **Lifecycle ordering** (done 2026-10-28; see "How mods load" in §4). It went in through mixins at NeoForge's call sites rather than Fabric entrypoints (the decision in §1), so `loader` lost its `main` entrypoint. Ported NeoForge's `CommonModLoader`/`ServerModLoader`/`ClientModLoader`, plus `NeoForge.EVENT_BUS` and the bindings provider (Known gap 8). Neoforge-api got a client source set. Testmods `lifecycle` and `fabricprobe`. The client runs under Xvfb.
3. ✅ **Registries** (done 2026-10-28; see §4 "Where Layer A deviates", Known gap 15 and the facts in §5). Ported the registries package (deferred registers and holders, the registry events, `RegistryBuilder`, callbacks, data map types, holder sets, datapack registries) with NeoForge's `MappedRegistry`/`Holder`/`HolderSet`/`Registry` patches as mixins plus a class tweaker. Registries stay open until Fabric API freezes them, sync goes through Fabric's registry sync (checked with a client joining the server), and datapack registries go through Fabric's `DynamicRegistries`. The `neoforge` system mod became a real `javafml` mod (`NeoForgeMod`). New testmod `registries`; `fabricprobe` checks from Fabric's side; testmods compile with NeoForge's extension interfaces (generated class tweaker).
4. **Game bus and common events.** `NeoForge.EVENT_BUS` exists since step 2 and starts at the end of `begin`. For each event, read the NeoForge patch that fires it (`NeoForge-26.2/patches/...`) and write the equivalent mixin at the same site (Fabric API's events don't carry what NeoForge's need, e.g. the tick `BooleanSupplier`, and fire at slightly different points). Port the hook method into the partial `EventHooks`/`CommonHooks`/`ClientHooks` verbatim. Corpus usage is listed in §7; the top ones are `PlayerInteractEvent.RightClickBlock` (9), `RegisterCommandsEvent` (8), `EntityJoinLevelEvent` (6), `PlayerInteractEvent.EntityInteract` (6), server tick (6), login (5), break (5), left click block (5).
   - ✅ **4a** (done 2026-10-28): server lifecycle (about to start, starting, started, stopping, stopped; dedicated and integrated), `ServerLifecycleHooks.getCurrentServer` (6 jars), SERVER configs per world, `RegisterCommandsEvent`, `CommandEvent`, `GameShuttingDownEvent`, server/level/entity/player/client ticks, level load/unload/save and `CreateSpawnPosition`, plus NeoForge's `WeightedList.Builder` patch (used by `LevelEvent.PotentialSpawns`). Testmod `events`.
   - ✅ **4b** (done 2026-10-28): player and interaction events. Server: login, logout, `PlayerRespawnPositionEvent`, respawn, clone, `EntityTravelToDimensionEvent` (players), changed dimension, `PlayerInteractEvent` (left click block, right click block and item, entity interaction) with cancellation and use-block/use-item results, `BreakBlockEvent`, `AttackEntityEvent`. Client: the same interactions in `MultiPlayerGameMode`, left click hold, empty clicks, `ClientPlayerNetworkEvent` (logging in and out, clone). Plus `BlockEvent` (+`ItemAbility`, `ItemAbilities`, `BlockSnapshot`) and NeoForge's `TriState` helpers. Left for later (see the deviations in §4): the extension-method parts (step 5), `PlayerDestroyItemEvent`, and block placement (`EntityPlaceEvent`, 4 jars), which needs NeoForge's block snapshot capture in `Level` and `ItemStack.useOn` (`CommonHooks.onPlaceItemIntoWorld`); probably with step 5. Not started either: the rarer player events (`BreakSpeed`, `HarvestCheck`, `StartTracking`, `ItemCrafted`/`ItemSmelted`, `PlayerContainerEvent`, sleeping, XP, ...).
   - **4c, in progress:** living and entity events.
     - ✅ `EntityJoinLevelEvent` (server: every add through `PersistentEntitySectionManager`, players at `ServerLevel.addPlayer` with NeoForge's `addNewEntityWithoutEvent`; client: `ClientLevel.addEntity`), `EntityLeaveLevelEvent` (both levels' `EntityCallbacks.onTrackingEnd`), `LivingDeathEvent` (`LivingEntity.die`, `ServerPlayer.die`), `LivingDropsEvent` (NeoForge's drop capture: `captureDrops` on `Entity`, `spawnAtLocation` adds to it, `dropAllDeathLoot` fires the event).
     - ✅ The damage pipeline: `EntityInvulnerabilityCheckEvent` (wrapping `Entity.isInvulnerableToBase`), `LivingIncomingDamageEvent`, `LivingDamageEvent.Pre`/`Post`, with `common/damagesource` (`DamageContainer`, `IReductionFunction`). `hurtServer` keeps vanilla's code with NeoForge's additions injected (container pushed and incoming event at the `isSleeping()` call, the container as the source of the damage amount, invulnerability-branch setup, invulnerability ticks from the container, side effects from the container, `Post` at the end); a `@WrapMethod` pops whatever a call pushed, whichever way it returns (Fabric API's `ALLOW_DAMAGE` cancels at the same `isSleeping()` call). `actuallyHurt` in `LivingEntity` and in `Player` are `@Overwrite`s with NeoForge's patched bodies, so NeoForge mods' mixins into them find NeoForge's shape. `getDamageAfterMagicAbsorb` records the mob effect and enchantment reductions. The stack is a real protected field named `damageContainers` on `LivingEntity` (NeoForge's name). Deviations: in the normal branch vanilla sets `lastHurt` and the invulnerability ticks before `actuallyHurt` (NeoForge after); `actuallyHurt` and the magic-absorb reductions tolerate an empty stack (NeoForge throws `EmptyStackException`). Not yet: `onDamageTaken` (step 5), the shield event (`LivingShieldBlockEvent` is ported, but its hook in `applyItemBlocking` needs NeoForge's six-argument `BlocksAttacks.hurtBlockingItem`), the armor durability event (`ArmorHurtEvent`, `doHurtEquipment`), `LivingHealEvent`.
     - ✅ `LivingChangeTargetEvent` (`Mob.setTarget`, and `StartAttacking`'s lambda `lambda$create$3` for brain-driven mobs), `LivingKnockBackEvent` (`@WrapMethod` on the six-argument `knockback`), `LivingEquipmentChangeEvent` (`collectEquipmentChanges`), `LivingEntityUseItemEvent` (start before `useItem` is set, with NeoForge's early return on a negative duration; tick through an `@Overwrite` of `updateUsingItem`, NeoForge's version; finish around `finishUsingItem`; stop as a condition on `releaseUsing`).
     - **Next:** `ItemEntityPickupEvent` (2; `ItemEntity.playerTouch`, which NeoForge rewrites around the pre event's result), chunk events (`ChunkEvent.Load`/`Unload` 4, `ChunkDataEvent.Load`/`Save` 3). Sites: `ChunkMap` (unload before `level.unload`, data load after `read`, data save after `SerializableChunkData.copyOf`), `ChunkStatusTasks` (load, at the end of the full-chunk task), `ClientChunkCache` (load after `onChunkLoaded`, unload before `drop`). `ChunkEvent.Load` on the server needs NeoForge's deadlock guard with it: a public `currentlyLoading` field on `GenerationChunkHolder`, set around `runPostLoad` and around the block entity registration plus the event in `ChunkStatusTasks`, and returned early by `ServerChunkCache`'s `getChunk` paths, so a listener asking for the loading chunk doesn't wait on its own future. The same patches add `CommonHooks.onChunkUnload` (POI) and `ChunkTicketLevelUpdatedEvent`/`ChunkWatchEvent` hooks in `ChunkMap`.
     - Also next: `EntityTravelToDimensionEvent` for non-player entities (`Entity.teleport`), `PlayerDestroyItemEvent` (2), `LivingHealEvent`, `LivingFallEvent`, `LivingEvent.LivingJumpEvent`, then the rarer ones by corpus count (§7).
     - Waiting for the entity extension (step 5): `onAddedToLevel`/`onRemovedFromLevel`/`isAddedToLevel` around these events, the entering-section hook, multipart entities.
5. **Extension interfaces and ATs.** The mechanism exists since step 3: `neoforge-api`'s class tweaker (applied by Loom and, at run time, by Fabric Loader) and the `patches/` package.
   - Add the rest of NeoForge's `META-INF/injected-interfaces.json` to the class tweaker as `inject-interface` lines, porting each `I*Extension` interface (most methods are defaults; abstract ones need a mixin implementing them, as `MappedRegistryMixin` does for `IRegistryExtension`). Skip the two stale targets (§5).
   - Convert NeoForge's AT files (`accesstransformer.cfg`, `accesstransformergenerated.cfg`) into class tweaker lines, for `neoforge-api` and for the testmods' compile class tweaker (Known gap 16).
   - Methods NeoForge's patches add straight to vanilla classes: instance methods as injected interfaces (`patches/`), static ones need a way to be added (Known gap 15). That includes `BuiltInRegistries.getVanillaRegistrationOrder` and `RegistryOps.retrieveRegistryLookup`.
   - Mod-provided ATs (`[[accessTransformers]]` in the TOML) need runtime application. Decide between that and transforming the jar.
6. **Configs.** FML's side (`ModConfig`, `ConfigTracker`, `ModConfigEvent`, `ModContainer.registerConfig`) was ported verbatim in step 1. Remaining: NeoForge's `ModConfigSpec`, and calling `ConfigTracker.loadConfigs` at the right times (see the CommonModLoader sequence in §5). Server configs load per world; check NeoForge's `ConfigTracker` for how.
7. **Networking.** Port `RegisterPayloadHandlersEvent`/`PayloadRegistrar`/`IPayloadContext`/`PacketDistributor` on top of Fabric `fabric-networking-api-v1` payload types (`PayloadTypeRegistry` + `ServerPlayNetworking`/`ClientPlayNetworking`).
8. **Resources.** Mount NeoForge jars as mod resource and data packs. Look at Fabric's `ModResourcePack`/`fabric-resource-loader-v1` and how built-in mod packs are injected.
9. **Basic capabilities and attachments.** `Capabilities`, `RegisterCapabilitiesEvent`, block/entity/item capability lookup (Fabric has `fabric-api-lookup-api-v1`, which is a close match), and `AttachmentType` (Fabric has `fabric-data-attachment-api-v1`).
10. **Discovery hardening.** JarJar extraction (`META-INF/jarjar/metadata.json` lists nested jars) and the refuse list (dependency and version checks already work through FML's `ModSorter`, since step 1) for render engines (by mod ID: `embeddium`, `sodium`, `iris`, `immersive_portals`, `firstperson`, `notenoughanimations`). Optionally register NeoForge mods with Fabric Loader's mod list (reflection on `FabricLoaderImpl`) so ModMenu and `isModLoaded` see them.

Bonus check for step 4 and later: Chunky's `master` branch (`src/mod_Src/test/Chunky`) has a NeoForge module. If it builds for 26.2, it's a native test of Layer A before Layer B exists.

## 9. Phase 3 preview (Layer B MVP)

Create these subprojects:
- `transformer`: an ASM pipeline that hashes each jar and caches the result in `.cache/versionunbound/`. Model it on Connector's `JarTransformer`.
- `upgrade-1.21.1`: the rename map, generated by diffing `src/mc-1.21.1/java` against `src/mc-26.2/java` (and NeoForge 1.21.1 against 26.2) and then curated by hand. Apply it with `ClassRemapper`. Shim classes go under `compat.v1_21_1...`, with static bridge methods for calls whose signatures changed. Mixin target and descriptor retargeting soft-fails: a mixin that can't be fixed is disabled with a warning.
- `cli`: runs the transform offline, plus the **reference scanner**, the main progress metric (unresolved references per test jar, trending toward 0).

**FML 4 → 11 changes the 1.21.1 test jars hit.** Measured with a constant-pool scan of the 15 in-scope jars; the number is how many jars use each API.
- `FMLEnvironment.dist`/`production` static fields (8) → `getDist()`/`isProduction()`.
- Static `FMLLoader.isProduction()` (6), `getDist()` (3), `getLoadingModList()` (1) → `FMLLoader.getCurrent().…`.
- `EventBusSubscriber.bus`/`EventBusSubscriber$Bus` (5): removed in 11, which picks the bus by event type.
- `IModFile.findResource(String...)` (4) → `getContents().findFile(...)`.
- `IModFile.getSecureJar()` (1) and `IModFileInfo.moduleName()` (1): gone.
- `LoadingModList.get()` (3) still exists in 11 (deprecated).

Hook the transformer into `VersionUnboundPreLaunch` before `addToClassPath`: detect 1.21.1 jars (the TOML `loaderVersion`/NeoForge dependency range, or the presence of 1.21.1-only references), transform them, and load the cached copy. Milestone: `Chunky-NeoForge-1.4.23.jar` unmodified, `chunky start` running a pregen on `runServer`. A data and asset JSON upgrader comes in Phase 4.

## 10. Risks and open questions

- ~~Registry freeze timing on Fabric~~ Settled in step 3: registration runs in `begin` while the registries are open, Fabric API freezes them after Fabric's mod init, and bake callbacks run then.
- **Two implementations of one patch.** Where Fabric API already does what a NeoForge patch does (holder binding, aliases, registry sync, dynamic registries, vanilla's block/item maps), Layer A uses Fabric's, and the behavior differs slightly at the edges (Known gap 15). Prefer that over a second implementation in the same vanilla methods, but check each edge case a mod hits.
- **Mixin conflicts.** Our hook mixins and the mods' own mixins hit the same vanilla methods; NeoForge mods assume NeoForge's patched code shape. Expect per-mod adjustments.
- **Client rendering (Phase 5)** is the largest unknown. Create's Flywheel is a render engine, so Create's client side may need a fallback rather than a translation.
- **Kotlin for Forge** (Cobblemon) is an FML language provider (`neoforgespi.language`); it needs a language-loader shim.
- **Scale.** Connector, Forgified Fabric API and Adapter took years for a same-version bridge. Keep scope phased and measured with the reference scanner.
