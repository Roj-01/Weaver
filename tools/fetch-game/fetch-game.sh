#!/usr/bin/env bash
# =============================================================================
#  scripts/fetch-game.sh — تنزيل ملفات Minecraft 26.3 والتحقق منها
#
#  غلاف حول spike/s1-artifacts/fetch-mc.sh:
#      bash scripts/fetch-game.sh                 # نزّل + تحقق
#      bash scripts/fetch-game.sh --check         # تحقق محلي فقط (بلا شبكة)
#      bash scripts/fetch-game.sh --version 26.3 --out work/game-26.3 --jobs 16
#      bash scripts/fetch-game.sh --only meta,client,libs   # لعبة بلا أصول/نيتيفز (مثل CI compat)
#
#  الضمانات:
#    • fetch-mc.sh يتحقق من sha1 لكل أصل مقابل version.json (--verify).
#    • هذا الغلاف يكتب work/game-26.3/sha256sums.txt لـversion.json/client.jar/
#      classpath.txt/meta.txt، ويتحقق منه في --check (كشف الانزياح).
#    • لا تُكتب ملفات اللعبة في git: work/ مُستثنى.
#
#  المتطلبات: bash, curl, unzip, sha1sum, xargs, python3 (fetch-mc.sh).
#  خروج: 0 نجاح/تحقق سليم · 1 فشل تنزيل/تحقق · 2 خطأ استخدام.
# =============================================================================
set -uo pipefail

# fetch-mc.sh يستعمل Python لتحليل JSON ويطبع تفاصيل عربية؛ على Windows console
# الافتراضي cp1252 فيسقط بـUnicodeEncodeError. نفرض UTF-8 على العملية وذرّيتها.
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

HERE="$(cd -- "$(dirname -- "$0")" && pwd)"
ROOT="$(cd -- "$HERE/../.." && pwd)"
FETCH="$HERE/fetch-mc.sh"
VERSION="26.3"
OUT="$ROOT/work/game-$VERSION"
JOBS="${WEAVER_FETCH_JOBS:-16}"
ONLY=""
CHECK_ONLY=0

while [ $# -gt 0 ]; do
  case "$1" in
    --check) CHECK_ONLY=1 ;;
    --version) [ $# -ge 2 ] || { echo "fetch-game.sh: --version needs a value" >&2; exit 2; }; VERSION="$2"; OUT="$ROOT/work/game-$VERSION"; shift ;;
    --out) [ $# -ge 2 ] || { echo "fetch-game.sh: --out needs a path" >&2; exit 2; }; OUT="$2"; shift ;;
    --jobs) [ $# -ge 2 ] || { echo "fetch-game.sh: --jobs needs a number" >&2; exit 2; }; JOBS="$2"; shift ;;
    --only) [ $# -ge 2 ] || { echo "fetch-game.sh: --only needs a step list" >&2; exit 2; }; ONLY="$2"; shift ;;
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
    *) echo "fetch-game.sh: unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

ONLY_ARGS=()
[ -n "$ONLY" ] && ONLY_ARGS=(--only "$ONLY")

[ -f "$FETCH" ] || { echo "fetch-game.sh: missing $FETCH" >&2; exit 2; }

native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
OUT_NATIVE="$(native "$OUT")"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d' ' -f1
  else return 1; fi
}

verify_sha256_manifest() {
  local manifest="$OUT/sha256sums.txt" rc=0 checked=0
  if [ ! -f "$manifest" ]; then
    echo "GAME SHA256 manifest=absent path=$manifest (fetch-mc.sh sha1 verification is authoritative)"
    return 0
  fi
  if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
    echo "GAME VERIFY ok=false reason=no-sha256-tool"
    return 1
  fi
  while read -r want rel; do
    [ -n "${want:-}" ] && [ -n "${rel:-}" ] || continue
    if [ ! -f "$OUT/$rel" ]; then
      echo "GAME VERIFY ok=false reason=missing file=$rel"; rc=1; continue
    fi
    local got; got="$(sha256_of "$OUT/$rel")"
    if [ "$got" != "$want" ]; then
      echo "GAME VERIFY ok=false reason=sha256-mismatch file=$rel want=$want got=$got"; rc=1
    else
      checked=$((checked + 1))
    fi
  done < "$manifest"
  if [ "$rc" -eq 0 ]; then
    echo "GAME VERIFY ok=true files=$checked manifest=$OUT_NATIVE/sha256sums.txt"
  fi
  return "$rc"
}

if [ "$CHECK_ONLY" = 1 ]; then
  echo "GAME CHECK version=$VERSION out=$OUT_NATIVE (local only)"
  bash "$FETCH" --check --version "$VERSION" --out "$OUT_NATIVE" ${ONLY_ARGS[@]+"${ONLY_ARGS[@]}"} || exit 1
  verify_sha256_manifest || exit 1
  exit 0
fi

mkdir -p "$OUT"
echo "GAME FETCH start version=$VERSION out=$OUT_NATIVE jobs=$JOBS"
if ! bash "$FETCH" --version "$VERSION" --out "$OUT_NATIVE" --jobs "$JOBS" --verify ${ONLY_ARGS[@]+"${ONLY_ARGS[@]}"}; then
  echo "GAME FETCH ok=false version=$VERSION out=$OUT_NATIVE reason=fetch-or-sha1-verify-failed"
  exit 1
fi

MANIFEST="$OUT/sha256sums.txt"
: > "$MANIFEST"
for rel in version.json client.jar classpath.txt meta.txt; do
  if [ -f "$OUT/$rel" ]; then
    printf '%s  %s\n' "$(sha256_of "$OUT/$rel")" "$rel" >> "$MANIFEST"
  fi
done
echo "GAME SHA256 wrote=$OUT_NATIVE/sha256sums.txt lines=$(wc -l < "$MANIFEST" | tr -d '[:space:]')"
verify_sha256_manifest || exit 1
echo "GAME FETCH ok=true version=$VERSION out=$OUT_NATIVE"
