# VersionUnbound — Handoff

State as of 2026-10-28. Phase 0 (workspace), Phase 1 (loader spike) and Phase 2 step 1 (FML metadata and context) are done. **Next: Phase 2 step 2, lifecycle ordering** (§8).

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

Phases:
- **0. Workspace** ✅
- **1. Loader spike** ✅
- **2. Layer A core** ← in progress (step 1 done, step 2 next)
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

- **NixOS.** Use the flake dev shell. Java isn't on PATH outside it.
  - `nix develop` gives JDK 25 (for 26.x). `nix develop .#jdk21` gives JDK 21 (for 1.21.x).
  - Both shells set `LD_LIBRARY_PATH` for LWJGL and include `mc-src`, `git`, `jq`, `zip`/`unzip`.
- **The flake only sees git-tracked files.** If you add a file the flake reads (e.g. under `nix/`), `git add` it first, or Nix won't find it.
- Build and run, from `VersionUnbound/versions/26.2`:
  ```
  nix develop /home/username/Development/active/minecraft-modding/VersionUnbound -c ./gradlew build
  (sleep 150; echo stop) | timeout 300 nix develop <repo> -c ./gradlew :loader:runServer --console=plain > server.log 2>&1
  ```
  - `runServer`/`runClient` depend on `copyTestMods`, which copies testmod jars into `loader/run/mods/` and writes `eula=true`.
  - Piping `stop` into Gradle works: stdin is forwarded to the server.
  - For a client without a display: `xvfb-run -a ./gradlew :loader:runClient`. `xvfb-run` isn't in our shell yet; the sibling WildernessUnbound flake has it, so add `pkgs.xvfb-run` when needed.
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
settings.gradle      includes neoforge-api, loader, testmods:hello, testmods:metadata
build.gradle         root: applies loom (apply false); subprojects get java, NeoForged maven, Java 25
neoforge-api/        Fabric mod id `versionunbound_neoforge` (Layer A)
  build.gradle       api+include: net.neoforged:bus, mergetool:api (Dist/OnlyIn), night-config toml (+core), maven-artifact;
                     include typetools. processResources expands neoforge_version/neoform_version into fabric.mod.json
                     (custom "versionunbound:implements"); the loader reads it to build FML's VersionInfo.
  src/main/java/net/neoforged/{fml,neoforgespi}/   FML 11.0.16: 102 files verbatim, 9 adapted (see below)
  src/main/resources/lang/*.json                   FML's translations for ModLoadingIssue keys (verbatim)
loader/              Fabric mod id `versionunbound`, entrypoints preLaunch + main
  build.gradle       implementation+include :neoforge-api;
                     loom.mods { versionunbound {...}; versionunbound_neoforge { sourceSet('main', ':neoforge-api') } }
                     copyTestMods task (→ run/mods, writes eula.txt) wired into runClient/runServer
  src/main/java/net/salutaryfish/versionunbound/loader/
    VersionUnboundPreLaunch.java   FMLLoader.create(Knot, dist, production, gameDir, versions) → NeoModDiscovery.findModJars
                                   → FMLLoader.discoverMods(...) → addToClassPath(accepted jars) → NeoMixinConfigs.register
    NeoModDiscovery.java           lists mods/*.jar that contain META-INF/neoforge.mods.toml (reading them is FML's job)
    NeoMixinConfigs.java           FML's MixinFacade.addMixins, adapted: requiredMods, behaviorVersion, duplicate configs;
                                   decorates each config with FabricUtil.KEY_MOD_ID / KEY_COMPATIBILITY
    NeoModLoader.java              follows NeoForge's CommonModLoader. begin() = ModLoader.gatherAndInitializeMods;
                                   load() = Common setup, Enqueue IMC, Process IMC (ModWorkManager sync/parallel executors)
    VersionUnboundInit.java        ModInitializer → NeoModLoader.begin(); NeoModLoader.load()
testmods/hello/      a GENUINE NeoForge 26.2 mod: Loom only supplies MC classes; compileOnly against the real
                     net.neoforged:neoforge:26.2.0.93:universal, fancymodloader:loader:11.0.16, bus, mergetool:api.
                     neoforge.mods.toml + hello.mixins.json (JAVA_21) + @Mixin(MinecraftServer) @Inject loadLevel HEAD.
testmods/metadata/   genuine NeoForge 26.2 mod `metadata` (built like hello). Requires hello with ordering AFTER. 47 checks
                     of IModInfo, ModList, ModLoadingContext, FMLEnvironment/FMLLoader/FMLPaths, extension points, scan data
                     and IMC. Logs "[metadata] PASS|FAIL <check>" and, at Process IMC, "[metadata] all N checks passed".
```

The built `loader-0.1.0.jar` nests `neoforge-api-0.1.0.jar` under `META-INF/jars/`, and that nests bus, typetools, mergetool, NightConfig toml/core and maven-artifact.

**Where Layer A deviates from upstream FML 11.0.16.** Everything else under `neoforge-api/src/main/java/net/neoforged` is byte-identical to `src/mod_Src/main/FML-11.0.16`, which keeps future diffs against FML cheap. Each deviation has a `VersionUnbound:` comment.
- `fml/loading/FMLLoader` is rewritten. It keeps the public API: `getCurrent`/`getCurrentOrNull`, `getDist`, `isProduction`, `getGameDir`, `getLoadingModList`, `getVersionInfo`, `getModFileByClass`, `getCurrentClassLoader`, static `getLanguageLoadingProvider`, `getBindings` (ServiceLoader on Knot), `getGameLayer` (returns the boot layer), and the `backgroundScanHandler` field. Its internal entry points are `create(...)` and `discoverMods(minecraftPath, neoForgePath, jars)`. `discoverMods` turns each jar into a `ModFile` (failures become `fml.modloadingissue.brokenfile` issues). It adds in-memory `minecraft` (26.2) and `neoforge` (26.2.0.93) system mods, which have empty contents and use the `minecraft` language loader (no event bus). Then it runs `ModSorter.sort` and `identifyLanguage`, and starts `BackgroundScanHandler`. Not ported: ProgramArgs, early window, class transformers, `close()`.
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

### Known gaps (updated after step 1)
1. ~~`ModContainer(String)`~~ **Fixed.** `ModContainer` is verbatim, including `registerConfig`, which goes to FML's `ConfigTracker` (ported verbatim). But nothing loads configs yet. That (COMMON/CLIENT at startup, SERVER per world) and NeoForge's `ModConfigSpec` are step 6.
2. ~~Errors wrapped in `RuntimeException`~~ **Fixed:** `ModLoadingException`/`ModLoadingIssue` and FML's translations, all verbatim.
3. ~~`DeferredWorkQueue` without active container~~ **Fixed** (verbatim).
4. **Lifecycle timing** (step 2). Today everything runs inside Fabric's `onInitialize`: construct (parallel, on FML's executors) → common setup → enqueue IMC → process IMC. Still missing: `RegisterEvent`, sided setup, `FMLLoadCompleteEvent` and config loading. NeoForge's exact order is in §5.
5. **Discovery.** FML's `ModSorter` now does dependency/version resolution and ordering, including `config/fml.toml` `dependencyOverrides`. `[[mixins]]` are handled per file, with `requiredMods`/`behaviorVersion`. Still missing:
   - JarJar.
   - The refuse list.
   - Applying `[[accessTransformers]]`: `ModFile.getAccessTransformers()` parses them, but nothing applies them.
   - `enumExtensions` (FML's `RuntimeEnumExtender`; `EnumProxy` is used by 3 of the test jars).
6. **No transformation or cache yet.** Jars are loaded as-is. The `transformer`, `cli` and `upgrade-1.21.1` subprojects don't exist yet.
7. **Fabric doesn't know about NeoForge mods.** They aren't Fabric mods, so they're missing from `FabricLoader.getAllMods()`, ModMenu and so on, and their jar resources aren't mounted as resource or data packs. That's needed as soon as a mod ships assets or data.
8. **No `IBindingsProvider` yet.** `@EventBusSubscriber` classes work for mod-bus events. Game-bus events throw "Could not find bindings provider" until Layer A ships `NeoForge.EVENT_BUS`, a bindings class and `META-INF/services/net.neoforged.fml.IBindingsProvider` (step 2 or 4).
9. **`CrashReportCallables` aren't in crash reports yet.** They're collected (ModList registers "Mod List"), but nothing writes them into Minecraft's crash reports.
10. **ModList lists only NeoForge mods plus `minecraft` and `neoforge`**, by design. If a NeoForge mod saw a Fabric mod's id it would enable integration code that expects the NeoForge build of that mod. Revisit if a specific integration needs it.
11. **Only the dedicated server has run the new loader.** Step 2 adds the client entrypoint; add `pkgs.xvfb-run` to the flake then.

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

Work in this order. Each step gets a **genuine NeoForge 26.2 testmod** under `testmods/`, compiled against the real NeoForge artifacts like `hello` is, and a log line or assertion checked on `runServer` (and later `runClient`). Add each testmod to `settings.gradle` and to `copyTestMods` in `loader/build.gradle`.

1. ✅ **FML metadata and context** (done 2026-10-28; see §4 and Known gaps). Original task: port `neoforgespi` `IModInfo`/`IModFileInfo`/`IModFile` (only what mods use), `ModList` (`get()`, `isLoaded`, `getModContainerById`, `getModFileById`, `getMods`), `ModLoadingContext`, `FMLEnvironment`/`FMLLoader` (`getDist`, `isProduction`, `getCurrent()` as in 11.x), `FMLPaths` (`CONFIGDIR`, `GAMEDIR`, `MODSDIR`), `ModLoadingException`/`ModLoadingIssue`, and `InterModComms` with the IMC events. Restore the real `ModContainer` signatures (Shortcut 1). Make `NeoModDiscovery` read the full TOML.
2. **Lifecycle ordering.** Match NeoForge's sequence on Fabric:
   - Construct mods early in `onInitialize`, before other mods register content.
   - `RegisterEvent` (step 3).
   - `FMLCommonSetupEvent`.
   - `FMLClientSetupEvent` (Fabric client entrypoint) or `FMLDedicatedServerSetupEvent`.
   - `InterModEnqueue`/`Process`.
   - `FMLLoadCompleteEvent`.

   Add a `client` entrypoint to `loader`. `@EventBusSubscriber` auto-registration (`AutomaticEventSubscriber`) is already ported and runs in `FMLModContainer.constructMod`; it needs a bindings provider for game-bus events (Known gap 8). Test the client here (Known gap 11).
3. **Registries.** Port `DeferredRegister`/`DeferredHolder`/`DeferredBlock`/`DeferredItem`, `RegisterEvent`, `NewRegistryEvent`/`RegistryBuilder`, and `NeoForgeRegistries` (custom registries such as attachment types, fluid types, …).
   - Fire `RegisterEvent` once per vanilla registry **before Fabric freezes them**. Find the freeze point in Fabric API `fabric-registry-sync-v0`.
   - Custom registries must sync through Fabric registry sync.
4. **Game bus and common events.** Create `NeoForge.EVENT_BUS` (a `BusBuilder` without the mod-bus marker). Implement in this order:
   - Server lifecycle events: `ServerStarting`, `ServerStarted`, `ServerStopping`, `RegisterCommandsEvent`.
   - Tick events, which map onto Fabric's lifecycle callbacks.
   - Level load/unload.
   - Player events: login/logout, clone, interact, break.
   - Living and entity events, through our own mixins where Fabric has no event.

   For each, read the NeoForge patch that fires it (`NeoForge-26.2/patches/...`) and write the equivalent mixin, unless Fabric API already has the hook.
5. **Extension interfaces and ATs.**
   - Convert `META-INF/injected-interfaces.json` into Loom interface injection (`custom.loom:injected_interfaces` in `fabric.mod.json`). Port the `I*Extension` interfaces; most methods are defaults.
   - Convert NeoForge's AT files into a class-tweaker/access widener for `neoforge-api` (Connector uses `net.fabricmc:class-tweaker`).
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

- **Registry freeze timing on Fabric** vs NeoForge's `RegisterEvent` (step 3). This is the most likely structural snag in Phase 2.
- **Mixin conflicts.** Our hook mixins and the mods' own mixins hit the same vanilla methods; NeoForge mods assume NeoForge's patched code shape. Expect per-mod adjustments.
- **Client rendering (Phase 5)** is the largest unknown. Create's Flywheel is a render engine, so Create's client side may need a fallback rather than a translation.
- **Kotlin for Forge** (Cobblemon) is an FML language provider (`neoforgespi.language`); it needs a language-loader shim.
- **Scale.** Connector, Forgified Fabric API and Adapter took years for a same-version bridge. Keep scope phased and measured with the reference scanner.
