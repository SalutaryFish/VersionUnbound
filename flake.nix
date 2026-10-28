{
  description = "VersionUnbound - Fabric mod for Minecraft 26.2 and 1.21.1";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});

      mkTools =
        pkgs:
        let
          # Decompiler (not packaged in nixpkgs).
          vineflower = pkgs.fetchurl {
            url = "https://repo1.maven.org/maven2/org/vineflower/vineflower/1.12.0/vineflower-1.12.0.jar";
            hash = "sha256-Hfz+l0OVc0+kZ85iBmHHYj0FuoNnDeBSmx+9Y/9Ui50=";
          };
          # Applies Mojang's ProGuard mappings to obfuscated (pre-26.x) jars.
          autoRenamingTool = pkgs.fetchurl {
            url = "https://maven.neoforged.net/releases/net/neoforged/AutoRenamingTool/2.0.18/AutoRenamingTool-2.0.18-all.jar";
            hash = "sha256-ZOzpcG5bERzry2YtTqtWumubmztL7hdmOCNS/gKmp/M=";
          };
          # Gives Mojang's $$N locals/parameters type-derived names, as Loom does.
          tinyRemapper = pkgs.fetchurl {
            url = "https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.15.0/tiny-remapper-0.15.0-fat.jar";
            hash = "sha256-ypP1YqsF2uqSjHuJo1UYjAaNYVTJ9/SF6HBNuvNb/UQ=";
          };
          # Applies Parchment parameter names and javadocs to the decompiled source.
          javaSourceTransformer = pkgs.fetchurl {
            url = "https://maven.neoforged.net/releases/net/neoforged/jst/jst-cli-bundle/2.0.12/jst-cli-bundle-2.0.12.jar";
            hash = "sha256-UedGxjYiNBcoL2tvOX5u3cp8m6YgbfInpO3C5NmbXH8=";
          };
        in
        {
          # Downloads and decompiles vanilla versions into ./src/mc-<ver> (see nix/mc-src.sh).
          mc-src = pkgs.writeShellApplication {
            name = "mc-src";
            runtimeInputs = with pkgs; [
              jdk25
              curl
              jq
              unzip
              coreutils
              findutils
              gnused
            ];
            text = ''
              VINEFLOWER_JAR=${vineflower}
              ART_JAR=${autoRenamingTool}
              TINY_REMAPPER_JAR=${tinyRemapper}
              JST_JAR=${javaSourceTransformer}
            ''
            + builtins.readFile ./nix/mc-src.sh;
          };
        };
    in
    {
      packages = forAllSystems (pkgs: rec {
        inherit (mkTools pkgs) mc-src;
        default = mc-src;
      });

      apps = forAllSystems (pkgs: {
        mc-src = {
          type = "app";
          program = "${self.packages.${pkgs.stdenv.hostPlatform.system}.mc-src}/bin/mc-src";
        };
      });

      devShells = forAllSystems (
        pkgs:
        let
          # Native libs LWJGL/GLFW dlopen when `./gradlew runClient` launches Minecraft.
          runtimeLibs = with pkgs; [
            libGL
            glfw3-minecraft
            openal
            libpulseaudio
            alsa-lib
            flite # narrator
            udev # controllers
            wayland
            libxkbcommon
            libx11
            libxcursor
            libxext
            libxi
            libxrandr
            libxxf86vm
            vulkan-loader
          ];
          mkShell =
            jdk: label:
            pkgs.mkShell {
              packages = [
                jdk
                pkgs.git
                pkgs.jq
                pkgs.unzip
                pkgs.zip
                pkgs.xvfb-run # headless client runs: xvfb-run -a ./gradlew :loader:runClient
                self.packages.${pkgs.stdenv.hostPlatform.system}.mc-src
              ];
              JAVA_HOME = jdk.home;
              # /run/opengl-driver/lib holds the system's GPU vendor libs that libglvnd dispatches to.
              LD_LIBRARY_PATH = "${pkgs.lib.makeLibraryPath runtimeLibs}:/run/opengl-driver/lib";
              shellHook = ''
                export ORG_GRADLE_JAVA_INSTALLATIONS_PATHS="${jdk.home}"
                echo "VersionUnbound dev shell (${label})"
                echo "  mc-src 26.2 1.21.1   decompile vanilla into ./src/mc-<ver>"
              '';
            };
        in
        {
          # Minecraft 26.x needs Java 25; 1.21.x needs Java 21 (`nix develop .#jdk21`).
          default = mkShell pkgs.jdk25 "JDK 25 - MC 26.x";
          jdk21 = mkShell pkgs.jdk21 "JDK 21 - MC 1.21.x";
        }
      );

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
