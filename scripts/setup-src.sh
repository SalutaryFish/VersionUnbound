#!/usr/bin/env bash
# setup-src: rebuild the reference material under src/ (HANDOFF.md §2) from where it came
# from, pinned to the exact commits, files and hashes the project was built with.
#
# The repo commits everything but the clones (src/mc-*, the FML sources, the test jars, with
# jars over GitHub's 100 MB limit as <jar>.part-NN pieces), so in a checkout the other parts
# only check hashes and join pieces; they still download whatever is missing.
#
#   scripts/setup-src.sh [part...]    parts: clones fml jars mc (default: all of them)
#
# Runs itself through the flake's dev shell (java, git, jq, unzip, mc-src), so it needs Nix.
# Already present parts are kept. A clone at another commit is reported and left alone.
# SRC_DIR overrides the output dir (default: src/). FULL_HISTORY=1 clones whole branches
# instead of just the pinned commit.
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if ! command -v mc-src >/dev/null; then
  command -v nix >/dev/null || { echo "setup-src: needs Nix, or run it inside 'nix develop'" >&2; exit 1; }
  exec nix develop "$root" -c "$root/scripts/setup-src.sh" "$@"
fi

SRC=${SRC_DIR:-$root/src}
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/versionunbound"

# <dir under src/>  <origin>  <branch>  <commit>
CLONES='
  mod_Src/main/fabric-api                https://github.com/FabricMC/fabric-api.git                           26.2                     6a8ed6bc5a15a01cd6979998fc950f32a67010dc
  mod_Src/main/NeoForge-1.21.1           https://github.com/neoforged/NeoForge.git                            1.21.1                   624c929f49920c250166e2a8df7af87f16d220ef
  mod_Src/main/NeoForge-26.2             https://github.com/neoforged/NeoForge.git                            26.2.x                   4e2117162e07bdf22de7e705a26fa0a0ae382350
  mod_Src/main/SinytraConnector          https://github.com/Sinytra/Connector.git                             26.2.x                   65b9f15af09500efc570aade38f12c56b09ec6b6
  mod_Src/test/Chunky                    https://github.com/pop4959/Chunky.git                                master                   666e8aca2dd4e008f9db8c1e0dc7e4bc12741567
  mod_Src/test/cobblemon                 https://gitlab.com/cable-mc/cobblemon.git                            main                     f4140a097d9426753a5537c7d3263dd1e8e98434
  mod_Src/test/Create                    https://github.com/Creators-of-Create/Create.git                     mc1.21.1/dev             4148fd8d5b05c85d88947edc00fb495a5074f57d
  mod_Src/test/Create-Aeronautics        https://github.com/Creators-of-Aeronautics/Simulated-Project.git     main                     3705df2062bebba42c3ab3d6b3dd09faa1512850
  mod_Src/test/Create-PowerGrid          https://github.com/patryk3211/PowerGrid.git                          architectury-1.20.1/dev  06ca6ac02f81e06f6d9672a4e8ef70048f319cba
  mod_Src/test/DynamicTrees              https://github.com/DynamicTreesTeam/DynamicTrees.git                 release/1.21.1           8bf66d83ac3a707fe482782fe21d2cb732527c66
  mod_Src/test/embeddium                 https://github.com/FiniteReality/embeddium.git                       21.4/neoforge            aa6657df4eaea8bdfa6243233c893207f5b7f8b4
  mod_Src/test/FarmersDelight            https://github.com/vectorwing/FarmersDelight.git                     1.21                     dd8b9b0bfec916a2da88a6d64e3a37f4a02c3259
  mod_Src/test/FirstPersonModel          https://github.com/tr7zw/FirstPersonModel.git                        main                     6b5859f90cc21805c3bbb03eb1f777925d9b3ddc
  mod_Src/test/ImmersivePortalsModForNeo https://github.com/iPortalTeam/ImmersivePortalsModForNeo.git         1.21                     aede93a4865fe4aab5dd2781fb38ab3e5cecd63b
  mod_Src/test/Iris                      https://github.com/IrisShaders/Iris.git                              26.1                     bff1e69cb6c5519d8745784aa9c8b92984de67e7
  mod_Src/test/KaleidoscopeCookery       https://github.com/KaleidoscopeMods/KaleidoscopeCookery.git          main                     2f4e386ce23f49a385ddf003c67fc6415c55417a
  mod_Src/test/Quark                     https://github.com/VazkiiMods/Quark.git                              master                   a28cbe66ba9488454d4650975cb1ee500b86b461
  mod_Src/test/sable                     https://github.com/ryanhcode/sable.git                               main                     227077f7f528c9beab1336a9fad2e27b754e4e70
  mod_Src/test/Toms-Storage              https://github.com/tom5454/Toms-Storage.git                          master                   27e352b95def40c14ced630a0ef20449e07809f5
'

# FML sources, unpacked (Layer A's FML port follows them).
FML_DIR=mod_Src/main/FML-11.0.16
FML_URL=https://maven.neoforged.net/releases/net/neoforged/fancymodloader/loader/11.0.16/loader-11.0.16-sources.jar
FML_SHA256=9a0abaf4886ae9a7899e8d1abd27211ea42e557fd7e8032bf6d152e11a79b0f3

# The 1.21.1 NeoForge test jars (HANDOFF.md §7), from Modrinth.
# <file under src/>  <sha512>  <url>
JARS='
  mod_Jars_1-21-1_Neoforge/Chunky/Chunky-NeoForge-1.4.23.jar 2db769dd723f243a21e1881e7c9f825e9c193da6f2bed454b70cb6fa9e51c57f63fdcf017c0657bbd26f7bba30815413e27c74d3c7be0783390a96ee9baa4bf7 https://cdn.modrinth.com/data/fALzjamp/versions/LuFhm4eU/Chunky-NeoForge-1.4.23.jar
  mod_Jars_1-21-1_Neoforge/cobblemon/Cobblemon-neoforge-1.8.4+1.21.1.jar 060579fb8ac67ddd131c9cc9076ef512cda9e66e7a584c7c82326be9ef0cbb69ce6c99ff772aa78f990dc1b771bac02f3bbba68a9c7ff68c557009c64f733d04 https://cdn.modrinth.com/data/MdwFAVRL/versions/xsVHrAsY/Cobblemon-neoforge-1.8.4%2B1.21.1.jar
  mod_Jars_1-21-1_Neoforge/cobblemon/kotlinforforge-5.12.0-all.jar b8c3942f4d33179edf3f102f3d870b99dd436f8b8236dbbd31aa51b888162c692cfd88927295f24dc8b4375232f4c6c17360c5d6c4823f93cbcd7cf4bdc8bd14 https://cdn.modrinth.com/data/ordsPcFz/versions/uhJhCT7X/kotlinforforge-5.12.0-all.jar
  mod_Jars_1-21-1_Neoforge/Create-Aeronautics/create-1.21.1-6.0.10.jar 11cc8fc049d2f67f6548c7abfada6b82a3adb5c7ca410a742de04bbca76e03862c518721b88d806f6e6d768a4d68531fdb903a85859b25d1484d550cc7bafd4b https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar
  mod_Jars_1-21-1_Neoforge/Create-Aeronautics/create-aeronautics-bundled-1.21.1-1.3.2.jar dc7e8e1148fa442243889ba29412207137e1bb86206e6c92bc0a589f1374a466e93de22bdbf14f753984d86c29308fbbdc0f14a4b9618b14c0ac1666a36b46f5 https://cdn.modrinth.com/data/oWaK0Q19/versions/44pLdPGg/create-aeronautics-bundled-1.21.1-1.3.2.jar
  mod_Jars_1-21-1_Neoforge/Create-Aeronautics/sable-neoforge-1.21.1-2.0.6.jar 76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8 https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar
  mod_Jars_1-21-1_Neoforge/Create/create-1.21.1-6.0.10.jar 11cc8fc049d2f67f6548c7abfada6b82a3adb5c7ca410a742de04bbca76e03862c518721b88d806f6e6d768a4d68531fdb903a85859b25d1484d550cc7bafd4b https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar
  mod_Jars_1-21-1_Neoforge/Create-PowerGrid/architectury-13.0.11-neoforge.jar d9f7c3bb8162577dfb461ffdf04bd6a3563c7586934a0e2a744c14421beffb8286f0d88d4c758317003f20f99fe8072a39b9d675af061e036970d36db36027f0 https://cdn.modrinth.com/data/lhGA9TYQ/versions/1IiqEQGl/architectury-13.0.11-neoforge.jar
  mod_Jars_1-21-1_Neoforge/Create-PowerGrid/create-1.21.1-6.0.10.jar 11cc8fc049d2f67f6548c7abfada6b82a3adb5c7ca410a742de04bbca76e03862c518721b88d806f6e6d768a4d68531fdb903a85859b25d1484d550cc7bafd4b https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar
  mod_Jars_1-21-1_Neoforge/Create-PowerGrid/powergrid-mc1.21.1-0.6.2.jar 60307e23a27526d22bf753d387edef5a2f2a063a04a47b69e98b7f71bbad32fdf17e05f9838008e47a5999a60e5e77a91f0695215d0ab57694f0034fde9873ca https://cdn.modrinth.com/data/eWiBLJ9R/versions/jCkPHjmP/powergrid-mc1.21.1-0.6.2.jar
  mod_Jars_1-21-1_Neoforge/DynamicTrees/dynamictrees-neoforge-1.21.1-1.7.2.jar 1faa5f292078fbae45fd8ee9873d22e13522a883f6650e71d1f4755ccfe5c363f606e0d4dc422384553483043883002136921ce5efb544c70d412affb2da076f https://cdn.modrinth.com/data/vdjF5PL5/versions/G4uJ0UA0/dynamictrees-neoforge-1.21.1-1.7.2.jar
  mod_Jars_1-21-1_Neoforge/embeddium/embeddium-1.0.15+mc1.21.1.jar d073cf52dcf2aeec3ac9f746d4571c7b4b1ad746fa11d4bcc5a21a264ae3119ac6d341d08cc9a70042cf0ddffc3b3ef3329ed30217035d4551c9d6788b6dc1e6 https://cdn.modrinth.com/data/sk9rgfiA/versions/J7b96IEd/embeddium-1.0.15%2Bmc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/FarmersDelight/FarmersDelight-1.21.1-1.3.4.jar 1f6f8796469f747cff36f477b4f589a3d69613882399f9ee7f86db36c7bca9aa4828731f43db15dc6c7f5a4c1bbe3d4092a232e8cef010b09cec0581848fda90 https://cdn.modrinth.com/data/R2OftAxM/versions/XTVZDOol/FarmersDelight-1.21.1-1.3.4.jar
  mod_Jars_1-21-1_Neoforge/FirstPersonModel/firstperson-neoforge-2.7.3-mc1.21.1.jar 979a3c50048042e1bcb623d89c04c7fe7048e173d7f77305edc7adc5b5580040439a7c5e001a55eab2d7cc6e2e0d13e75d8be165da13dea61ee126a794101763 https://cdn.modrinth.com/data/H5XMjpHi/versions/eaj8cYpD/firstperson-neoforge-2.7.3-mc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/FirstPersonModel/notenoughanimations-neoforge-1.12.6-mc1.21.1.jar 2b392f770cac669abcc796e836fc86579e29c1c70174f773fadbb747d8a49763fefd938835aeef8681a384aee9a7b6a8fa4684aaa1961159ccddea680e936acd https://cdn.modrinth.com/data/MPCX6s5C/versions/VCuMsK45/notenoughanimations-neoforge-1.12.6-mc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/ImmersivePortalsModForNeo/cloth-config-15.0.140-neoforge.jar aaf9b010955b8cd294e5a92f069985b18729fd5e2cf22d351f1dff9680f15488688803ec41e77e941cbde130ceb535014ca4c868047d80ab69c2d508e216654d https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar
  mod_Jars_1-21-1_Neoforge/ImmersivePortalsModForNeo/immersive_portals-6.0.7-all.jar c4b5ce9f5b3f9030cf836adef613e073036b04bbe77f07978872165c142ec88fb8d726ba6e90afd8907b8f5e868b00e0a0efa7613f32c402fcb93ed082a0f828 https://cdn.modrinth.com/data/zf4Szzx2/versions/HbBv6YVB/immersive_portals-6.0.7-all.jar
  mod_Jars_1-21-1_Neoforge/Iris/iris-neoforge-1.8.14-beta.1+mc1.21.1.jar 33fddb430e16222902eac75b8b3d57c381b0f3cc729f3b56c4628df12ca108f23a0b11e2feb167923d6d14d1fc7e8ee1a977ec421b19d1951bdca74c4092b9c5 https://cdn.modrinth.com/data/YL57xq9U/versions/KduFYu4t/iris-neoforge-1.8.14-beta.1%2Bmc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/Iris/sodium-neoforge-0.8.13+mc1.21.1.jar 4f537696af95411e9daf15795fb5fcbc49913d488a96fa912fa8f1d7db408710995e8a1461190f3cd0f8217bf9e35e9539176592428ed587ca2d7aa84450a390 https://cdn.modrinth.com/data/AANobbMI/versions/uMOpc5uV/sodium-neoforge-0.8.13%2Bmc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/KaleidoscopeCookery/kaleidoscopecookery-1.6.0-neoforge+mc1.21.1.jar b562f73395f70d39fb26d89ca62a5b5cebe7d66b11e8930217f0daf03fdcea617629728f55e4c8ced690406204c44462a8a568e5c15f05f5940274904ce0b77f https://cdn.modrinth.com/data/v17FatAc/versions/g8w0b2ly/kaleidoscopecookery-1.6.0-neoforge%2Bmc1.21.1.jar
  mod_Jars_1-21-1_Neoforge/Quark/Quark-4.1-488.jar 06f68ae18faa06497bf2495ef8c34b6f34cccd304939736e009aec024879b2998234bc4fedd7abd9125112e7c0afeb5fd7ddbcc54b6034bd4c95b479ed8880f4 https://cdn.modrinth.com/data/qnQsVE2z/versions/pMjjsBGF/Quark-4.1-488.jar
  mod_Jars_1-21-1_Neoforge/Quark/Zeta-1.1-40.jar a81dcf44f2bfd0fc1a52f00d9ca47a84ad2baf067adedb35eec859f6b9cec7b61ca432e549a3342b10902f84e76d70224cd54400285c8b13a0926835a62c667c https://cdn.modrinth.com/data/MVARlG2f/versions/9GjNW2Gf/Zeta-1.1-40.jar
  mod_Jars_1-21-1_Neoforge/sable/sable-neoforge-1.21.1-2.0.6.jar 76d293ce751a01cb9c095a3659b0a41e03e005480bc9d76dfdc73ec7c2eabf7923740c7b077921d42616673c496cd5143569f2085ddcebe174fd91d81e2a04b8 https://cdn.modrinth.com/data/T9PomCSv/versions/fg9dTRz9/sable-neoforge-1.21.1-2.0.6.jar
  mod_Jars_1-21-1_Neoforge/SinytraConnector/connector-2.0.0-beta.17+1.21.1-full.jar cb92b662047208792e61191d372d1e255766200406c35769007101f8c578c1018938610c3b90352afd93ec42d7963acf3607e35c50e669df50ac85d7c5529560 https://cdn.modrinth.com/data/u58R1TMW/versions/IITF0PRC/connector-2.0.0-beta.17%2B1.21.1-full.jar
  mod_Jars_1-21-1_Neoforge/SinytraConnector/forgified-fabric-api-0.116.15+2.3.6+1.21.1.jar 470ae4e8569484e6942a2f7e09a37ae58a58e0ad44f49f0d483db88024db19d41bfac3c68221c38623829871ade9d89bc5aa31cf0e682e798b2f9029b808011d https://cdn.modrinth.com/data/Aqlf1Shp/versions/C4eskNuR/forgified-fabric-api-0.116.15%2B2.3.6%2B1.21.1.jar
  mod_Jars_1-21-1_Neoforge/Toms-Storage/toms_storage-1.21-2.4.2.jar 53db61b4486616b3a78dfff83b58f9424dd9bf525ea3db4e444ff0403fb70aa9ab7fc097dff2f49665e480bce936b32be76b3051a5aff217898b2b94d846d30b https://cdn.modrinth.com/data/XZNI4Cpy/versions/WQAMoIMC/toms_storage-1.21-2.4.2.jar
'

MC_VERSIONS='26.2 1.21.1'

log() { echo "setup-src: $*" >&2; }

# fetch <url> <dest> <sha256|sha512> <hash> -- download unless a valid copy is already there.
fetch() {
  local url=$1 dest=$2 algo=$3 hash=$4
  if [[ -f "$dest" ]] && echo "$hash  $dest" | "${algo}sum" -c --status; then
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  if [[ -f "$dest.part-00" ]]; then
    cat "$dest".part-[0-9][0-9] >"$dest.part"
  else
    curl -fsSL --retry 3 -o "$dest.part" "$url"
  fi
  if ! echo "$hash  $dest.part" | "${algo}sum" -c --status; then
    rm -f "$dest.part"
    log "$algo mismatch for $url"
    exit 1
  fi
  mv "$dest.part" "$dest"
}

clones() {
  local dir url branch commit dest have refspec
  while read -r dir url branch commit; do
    [[ -n "$dir" ]] || continue
    dest=$SRC/$dir
    if [[ -e "$dest/.git" ]]; then
      have=$(git -C "$dest" rev-parse HEAD)
      [[ "$have" == "$commit" ]] || log "$dir: at $have, not the pinned $commit; left alone"
      continue
    fi
    log "$dir: cloning $branch at ${commit:0:9}"
    rm -rf "$dest.part"
    git init -q "$dest.part"
    git -C "$dest.part" remote add origin "$url"
    if [[ "${FULL_HISTORY:-}" == 1 ]]; then
      refspec=("+refs/heads/$branch:refs/remotes/origin/$branch")
    else
      refspec=(--depth 1 "$commit")
    fi
    if ! git -C "$dest.part" fetch -q origin "${refspec[@]}"; then
      log "$dir: couldn't fetch from $url; skipped"
      rm -rf "$dest.part"
      failed+=("$dir")
      continue
    fi
    git -C "$dest.part" checkout -q -B "$branch" "$commit"
    git -C "$dest.part" config "branch.$branch.remote" origin
    git -C "$dest.part" config "branch.$branch.merge" "refs/heads/$branch"
    mv "$dest.part" "$dest"
  done <<<"$CLONES"
}

fml() {
  local dest=$SRC/$FML_DIR jar=$CACHE/loader-11.0.16-sources.jar
  if [[ -d "$dest" ]]; then return 0; fi
  log "$FML_DIR: unpacking FML sources"
  fetch "$FML_URL" "$jar" sha256 "$FML_SHA256"
  rm -rf "$dest.part"
  mkdir -p "$dest.part"
  unzip -q "$jar" -d "$dest.part"
  mv "$dest.part" "$dest"
}

jars() {
  local file sha512 url
  log "test jars: downloading what's missing"
  while read -r file sha512 url; do
    [[ -n "$file" ]] || continue
    fetch "$url" "$SRC/$file" sha512 "$sha512"
  done <<<"$JARS"
}

mc() {
  local ver
  for ver in $MC_VERSIONS; do
    if [[ -d "$SRC/mc-$ver/java" ]]; then continue; fi
    MC_SRC_DIR=$SRC mc-src "$ver"
  done
}

failed=()
parts=("$@")
[[ ${#parts[@]} -gt 0 ]] || parts=(clones fml jars mc)
for part in "${parts[@]}"; do
  case "$part" in
    clones | fml | jars | mc) "$part" ;;
    *)
      log "unknown part '$part' (parts: clones fml jars mc)"
      exit 1
      ;;
  esac
done
if [[ ${#failed[@]} -gt 0 ]]; then
  log "done -> $SRC, except: ${failed[*]}"
  exit 1
fi
log "done -> $SRC"
