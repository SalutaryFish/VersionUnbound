# mc-src: download and decompile vanilla Minecraft into ./src/mc-<version>.
# Wrapped by flake.nix, which sets VINEFLOWER_JAR / ART_JAR and puts the tools on PATH.
#
# Output (override the parent dir with MC_SRC_DIR):
#   src/mc-<ver>/java/              decompiled client jar (client + common code), Mojang names
#   src/mc-<ver>/resources/         non-class files from the jar (assets/, data/, ...)
#   src/mc-<ver>/asset-index.json   Mojang asset index (sounds, lang, ...)
#
# 26.x jars ship unobfuscated and are decompiled as-is. Older versions (e.g. 1.21.1)
# are remapped with Mojang's official client mappings first, then get parameter names
# and javadocs from the latest Parchment release, and type-derived names for locals.
#
# Downloads are cached in ${XDG_CACHE_HOME:-~/.cache}/mc-src so the project tree
# (which the flake copies into the nix store) stays small.

MANIFEST_URL="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
PARCHMENT_MAVEN="https://maven.parchmentmc.org/org/parchmentmc/data"

usage() {
  cat <<EOF
Usage: mc-src <version>...      e.g. mc-src 26.2 1.21.1

Output dir: \$MC_SRC_DIR (default: src/ next to flake.nix)
Cache dir:  \$XDG_CACHE_HOME/mc-src
EOF
}

if [[ -z "${MC_SRC_DIR:-}" ]]; then
  root=$PWD
  while [[ "$root" != / && ! -f "$root/flake.nix" ]]; do root=$(dirname "$root"); done
  [[ "$root" == / ]] && root=$PWD
  MC_SRC_DIR="$root/src"
fi
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/mc-src"

log() { echo "mc-src: $*" >&2; }

# fetch <url> <dest> [sha1] -- download unless a valid copy is already cached.
fetch() {
  local url=$1 dest=$2 sha1=${3:-}
  if [[ -f "$dest" ]]; then
    if [[ -z "$sha1" ]] || echo "$sha1  $dest" | sha1sum -c --status; then
      return 0
    fi
  fi
  mkdir -p "$(dirname "$dest")"
  curl -fsSL --retry 3 -o "$dest.part" "$url"
  if [[ -n "$sha1" ]] && ! echo "$sha1  $dest.part" | sha1sum -c --status; then
    rm -f "$dest.part"
    log "sha1 mismatch for $url"
    exit 1
  fi
  mv "$dest.part" "$dest"
}

version_json() {
  local ver=$1 dest="$CACHE/versions/$1.json"
  if [[ ! -f "$dest" ]]; then
    mkdir -p "$CACHE"
    curl -fsSL --retry 3 -o "$CACHE/version_manifest_v2.json.part" "$MANIFEST_URL"
    mv "$CACHE/version_manifest_v2.json.part" "$CACHE/version_manifest_v2.json"
    local entry
    entry=$(jq -c --arg v "$ver" '.versions[] | select(.id == $v)' "$CACHE/version_manifest_v2.json")
    if [[ -z "$entry" ]]; then
      log "unknown version '$ver'"
      exit 1
    fi
    fetch "$(jq -r .url <<<"$entry")" "$dest" "$(jq -r .sha1 <<<"$entry")"
  fi
  echo "$dest"
}

# parchment_zip <ver> -- download the latest Parchment release for <ver>; fails if there is none.
parchment_zip() {
  local ver=$1 base="$PARCHMENT_MAVEN/parchment-$1" release
  release=$(curl -fsSL --retry 3 "$base/maven-metadata.xml" 2>/dev/null |
    sed -n 's:.*<release>\(.*\)</release>.*:\1:p') || return 1
  [[ -n "$release" ]] || return 1
  local dest="$CACHE/parchment/parchment-$ver-$release.zip"
  fetch "$base/$release/parchment-$ver-$release.zip" "$dest"
  echo "$dest"
}

decompile() {
  local ver=$1 vjson out
  vjson=$(version_json "$ver")
  out="$MC_SRC_DIR/mc-$ver"

  log "$ver: downloading client jar"
  local client="$CACHE/jars/$ver-client.jar"
  fetch "$(jq -r .downloads.client.url "$vjson")" "$client" "$(jq -r .downloads.client.sha1 "$vjson")"

  log "$ver: downloading libraries"
  local libs=() path url sha1
  while IFS=$'\t' read -r path url sha1; do
    fetch "$url" "$CACHE/libraries/$path" "$sha1"
    libs+=("$CACHE/libraries/$path")
  done < <(jq -r '.libraries[]
      | select(.name | test(":natives-") | not)
      | .downloads.artifact // empty
      | [.path, .url, .sha1] | @tsv' "$vjson")

  # Obfuscated versions ship ProGuard mappings (named -> obf); apply them reversed.
  local input=$client parchment=""
  if jq -e '.downloads.client_mappings' "$vjson" >/dev/null; then
    log "$ver: remapping to Mojang names"
    local mappings="$CACHE/mappings/$ver-client.txt"
    fetch "$(jq -r .downloads.client_mappings.url "$vjson")" "$mappings" "$(jq -r .downloads.client_mappings.sha1 "$vjson")"
    local mojmap="$CACHE/jars/$ver-client-mojmap.jar"
    if [[ ! -f "$mojmap" ]]; then
      local lib art_libs=()
      for lib in "${libs[@]}"; do art_libs+=(--lib "$lib"); done
      java -jar "$ART_JAR" \
        --input "$client" --output "$mojmap.part" \
        --names "$mappings" --reverse \
        --ann-fix --ids-fix --src-fix --record-fix --strip-sigs \
        "${art_libs[@]}" >"$CACHE/jars/$ver-remap.log"
      mv "$mojmap.part" "$mojmap"
    fi

    # Mojang's jars name every local and parameter $$N. Like Loom, apply Parchment's
    # parameter names in bytecode, then give whatever is left type-derived names
    # (blockState, itemStack, ...). Class/member names map to themselves.
    local names="$CACHE/mappings/$ver-unnamed.tiny" tag=unnamed
    if parchment=$(parchment_zip "$ver"); then
      tag=$(basename "$parchment" .zip)
      names="$CACHE/mappings/$tag.tiny"
      [[ -f "$names" ]] || unzip -p "$parchment" parchment.json | jq -r '
          "tiny\t2\t0\tofficial\tnamed",
          (.classes[] | select(any(.methods[]?; .parameters))
            | "c\t\(.name)\t\(.name)",
              (.methods[] | select(.parameters)
                | "\tm\t\(.descriptor)\t\(.name)\t\(.name)",
                  (.parameters[] | select(.name) | "\t\tp\t\(.index)\t\t\(.name)")))
        ' >"$names"
    else
      parchment=""
      log "$ver: no Parchment release found; parameters get type-derived names"
      printf 'tiny\t2\t0\tofficial\tnamed\n' >"$names"
    fi
    log "$ver: naming parameters and locals ($tag)"
    input="$CACHE/jars/$ver-client-$tag.jar"
    if [[ ! -f "$input" ]]; then
      # tiny-remapper writes a jar only when the output name ends in .jar, and a folder
      # otherwise. Older runs left such folders at $input; mv would move into them.
      rm -rf "$input" "${input%.jar}.part.jar"
      java -jar "$TINY_REMAPPER_JAR" \
        "$mojmap" "${input%.jar}.part.jar" "$names" official named "${libs[@]}" \
        --renameInvalidLocals '--invalidLvNamePattern=\$\$\d+' --inferNameFromSameLvIndex \
        --nonClassCopyMode=unchanged >"$CACHE/jars/$ver-names.log" 2>&1
      mv "${input%.jar}.part.jar" "$input"
    fi
  fi

  log "$ver: downloading asset index"
  fetch "$(jq -r .assetIndex.url "$vjson")" "$out/asset-index.json" "$(jq -r .assetIndex.sha1 "$vjson")"

  log "$ver: extracting resources"
  rm -rf "$out/resources"
  mkdir -p "$out/resources"
  unzip -q -o "$client" -x '*.class' 'META-INF/*' -d "$out/resources"

  log "$ver: decompiling with Vineflower (this takes a few minutes)"
  local lib vf_libs=()
  for lib in "${libs[@]}"; do vf_libs+=("-e=$lib"); done
  rm -rf "$out/java" "$out/java.part"
  mkdir -p "$out/java.part"
  java -Xmx6G -jar "$VINEFLOWER_JAR" \
    --folder \
    --log-level=warn \
    --indent-string="    " \
    --thread-count="$(nproc)" \
    "${vf_libs[@]}" \
    "$input" "$out/java.part"
  # Vineflower also copies non-class files; those already live in resources/.
  find "$out/java.part" -type f ! -name '*.java' -delete
  find "$out/java.part" -type d -empty -delete

  # Parameter names are already in the bytecode; JST adds Parchment's javadocs.
  if [[ -n "$parchment" ]]; then
    log "$ver: adding Parchment javadocs"
    # Libraries only: the Minecraft classes themselves come from the source being transformed.
    printf '%s\n' "${libs[@]}" >"$CACHE/jars/$ver-classpath.txt"
    rm -rf "$out/java.jst"
    if ! java -Xmx6G -jar "$JST_JAR" \
      --libraries-list="$CACHE/jars/$ver-classpath.txt" \
      --enable-parchment --parchment-mappings="$parchment" --no-parchment-parameters \
      --in-format=FOLDER --out-format=FOLDER \
      "$out/java.part" "$out/java.jst" >"$CACHE/jars/$ver-jst.log" 2>&1; then
      rm -rf "$out/java.part" "$out/java.jst"
      log "$ver: Parchment javadoc step failed, see $CACHE/jars/$ver-jst.log"
      exit 1
    fi
    rm -rf "$out/java.part"
    mv "$out/java.jst" "$out/java.part"
  fi
  mv "$out/java.part" "$out/java"

  log "$ver: done -> $out"
}

case "${1:-}" in
  "" | -h | --help | help)
    usage
    [[ $# -gt 0 ]]
    ;;
  *)
    for ver in "$@"; do decompile "$ver"; done
    ;;
esac
