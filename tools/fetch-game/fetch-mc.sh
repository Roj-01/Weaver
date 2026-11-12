#!/usr/bin/env bash
# =====================================================================
#  fetch-mc.sh — تنزيل ملفات Minecraft الرسمية (من piston-meta/piston-data)
#  وتجهيز مجلد إقلاع كامل: version.json + client.jar + libraries/ +
#  natives/ + assets/ + classpath.txt + meta.txt
#
#  المتطلبات: bash, curl, unzip, sha1sum, xargs, python (أو python3) لتحليل JSON
#
#  أمثلة:
#    bash fetch-mc.sh                                  # أحدث إصدار إطلاق (يتحقق أنه 26.x)
#    bash fetch-mc.sh --version 26.3 --jobs 24
#    bash fetch-mc.sh --dry-run                        # خطة فقط بلا تنزيل
#    bash fetch-mc.sh --resume --only assets           # إكمال الأصول المتوقفة
#    bash fetch-mc.sh --verify                         # تحقق شامل من المخرجات
# =====================================================================
set -uo pipefail

# ---------- أدوات مساعدة عامة ----------
die()  { printf 'FATAL: %s\n' "$*" >&2; exit 1; }
warn() { printf 'WARN: %s\n' "$*" >&2; }
log()  { printf '%s\n' "$*"; }

native() { # تحويل مسار إلى نمط ويندوز D:/... (مقبول من curl/unzip/java/sha1sum)
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi
}
human() { # بايتات → صيغة مقروءة
  awk -v b="$1" 'BEGIN{s="B K M G T"; n=split(s,a," "); i=1; while(b>=1024 && i<n){b/=1024;i++} printf "%.1f%s", b, a[i]}'
}
sha1_of() { sha1sum "$1" 2>/dev/null | cut -d' ' -f1; }
fsize()   { stat -c%s "$1" 2>/dev/null || echo -1; }
# pyq: تشغيل python مع تنقية CR (stdout على ويندوز يخرج \r\n)
pyq() { "$PY" "$@" | tr -d '\r'; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_OUT="$(native "$SCRIPT_DIR/../../work/game-26.3")"
DEFAULT_WORK="$(native "$SCRIPT_DIR/tmp")"

# ---------- المعطيات الافتراضية ----------
VERSION=""                       # فارغ = أحدث إصدار إطلاق
OUT="$DEFAULT_OUT"
WORK="$DEFAULT_WORK"
JOBS=16
RESUME=0
REFRESH_META=0
STRICT_RULES=0
DRY_RUN=0
DO_VERIFY=0
CHECK=0
WRITE_OUT=1
STEPS="meta,logging,client,libs,natives,assets"
REQUIRE_PREFIX="26."
BASE_MANIFEST="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
RESOURCES_BASE="https://resources.download.minecraft.net"
PROGRESS=1

while [ $# -gt 0 ]; do
  case "$1" in
    --version)     VERSION="${2:?}"; shift 2 ;;
    --out)         OUT="$2"; shift 2 ;;
    --work-dir)    WORK="$2"; shift 2 ;;
    --jobs)        JOBS="$2"; shift 2 ;;
    --only)        STEPS="$2"; shift 2 ;;
    --resume)      RESUME=1; shift ;;
    --refresh-meta) REFRESH_META=1; shift ;;
    --strict-rules) STRICT_RULES=1; shift ;;
    --allow-any-version) REQUIRE_PREFIX=""; shift ;;
    --dry-run)     DRY_RUN=1; WRITE_OUT=0; shift ;;
    --verify)      DO_VERIFY=1; shift ;;
    --check)       CHECK=1; DO_VERIFY=1; WRITE_OUT=0; shift ;;
    --no-progress) PROGRESS=0; shift ;;
    --help|-h)     sed -n '2,14p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "خيار غير معروف: $1  (جرّب --help)" ;;
  esac
done

OUT="$(native "$OUT")"
WORK="$(native "$WORK")"
MANIFEST_CACHE="$WORK/version_manifest_v2.json"
VJSON_CACHE=""                   # يُحدَّد بعد اختيار الإصدار
USE_CACHE=0
step_enabled() { case ",$STEPS," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }

# ---------- فحص المتطلبات ----------
for t in curl unzip sha1sum xargs awk; do
  command -v "$t" >/dev/null 2>&1 || die "أداة مفقودة: $t"
done
PY=""
for c in python python3 py; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -c 'import json,sys' >/dev/null 2>&1; then PY="$c"; break; fi
done
[ -n "$PY" ] || die "لا يوجد مترجم python يعمل (مطلوب لتحليل JSON)"

mkdir -p "$WORK" "$OUT" || die "تعذّر إنشاء المجلدات"

# =====================================================================
#  1) meta — المانيفست ← أحدث إصدار إطلاق ← version.json
# =====================================================================
fetch_meta() {
  local manifest="$MANIFEST_CACHE" fresh=0
  if [ "$REFRESH_META" = 1 ] || { [ "$RESUME" = 0 ] && [ ! -s "$manifest" ]; }; then
    log "→ جلب المانيفست: $BASE_MANIFEST"
    if curl -sS -f -L --connect-timeout 20 --retry 3 --retry-delay 2 \
         -o "$manifest.part" "$BASE_MANIFEST"; then
      mv -f "$manifest.part" "$manifest"; fresh=1
    else
      rm -f "$manifest.part"
      [ -s "$manifest" ] && { warn "فشل الجلب — أُستخدمت نسخة الكاش للمانيفست"; USE_CACHE=1; } || die "فشل جلب المانيفست ولا يوجد كاش"
    fi
  fi
  [ -s "$manifest" ] || die "ملف المانيفست غير موجود: $manifest"

  # اختيار الإصدار + رابط version.json
  local pick
  pick=$(pyq - "$manifest" "$VERSION" "$REQUIRE_PREFIX" <<'PY'
import json, sys
man, want, prefix = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(open(man, encoding='utf-8'))
vid = want or d['latest']['release']
if not want and prefix and not vid.startswith(prefix):
    sys.stderr.write("الإصدار الأحدث للإطلاق %s لا يبدأ بـ %s\n" % (vid, prefix)); sys.exit(3)
ent = next((v for v in d['versions'] if v['id'] == vid), None)
if ent is None:
    sys.stderr.write("الإصدار غير موجود في المانيفست: %s\n" % vid); sys.exit(4)
print("\t".join([ent['id'], ent['type'], ent['url'], ent['sha1'], d['latest']['release'], d['latest']['snapshot']]))
PY
  ) || die "فشل اختيار الإصدار"
  IFS=$'\t' read -r VID VTYPE VURL VSHA LATEST_REL LATEST_SNAP <<< "$pick"
  log "→ الإصدار المختار: $VID ($VTYPE) | أحدث إطلاق=$LATEST_REL أحدث سنابشوت=$LATEST_SNAP"
  case "$VID" in
    $REQUIRE_PREFIX*) : ;;
    *) [ -n "$REQUIRE_PREFIX" ] && warn "تنبيه: الإصدار $VID لا يبدأ بـ $REQUIRE_PREFIX" ;;
  esac

  VJSON_CACHE="$WORK/$VID.json"
  if [ ! -s "$VJSON_CACHE" ] || [ "$REFRESH_META" = 1 ]; then
    log "→ تنزيل version.json: $VURL"
    curl -sS -f -L --connect-timeout 20 --retry 3 --retry-delay 2 -o "$VJSON_CACHE.part" "$VURL" \
      || die "فشل تنزيل version.json"
    mv -f "$VJSON_CACHE.part" "$VJSON_CACHE"
  fi
  local got; got="$(sha1_of "$VJSON_CACHE")"
  [ "$got" = "$VSHA" ] || warn "sha1 الخاص بـ version.json غير مطابق (متوقع $VSHA، وجد $got)"

  if [ "$WRITE_OUT" = 1 ]; then cp -f "$VJSON_CACHE" "$OUT/version.json" || die "تعذّر نسخ version.json إلى $OUT"; fi
  export VID VTYPE VJSON_CACHE MANIFEST_CACHE
}

# =====================================================================
#  بناء خطة المكتبات/النيتيفز/الclasspath (python لتحليل JSON وتقييم rules)
# =====================================================================
build_plan() {
  log "→ تقييم rules وبناء خطة المكتبات..."
  OS_NAME="windows" ARCH="x86_64"
  case "$(uname -s)" in
    Linux*)  OS_NAME="linux" ;;
    Darwin*) OS_NAME="osx" ;;
    MINGW*|MSYS*|CYGWIN*) OS_NAME="windows" ;;
  esac
  local jarch; jarch="$(java -XshowSettings:properties -version 2>&1 | awk -F'= *' '/os.arch/{print $2; exit}')"
  case "$jarch" in
    aarch64|arm64) ARCH="arm64" ;;
    x86|i386|i486|i686) ARCH="x86" ;;
    *) ARCH="x86_64" ;;
  esac
  export OS_NAME ARCH
  "$PY" - "$VJSON_CACHE" "$OUT" "$WORK" "$OS_NAME" "$ARCH" "$STRICT_RULES" <<'PY' || die "فشل بناء الخطة"
import json, os, sys

vjson, out, work, osname, arch, strict = sys.argv[1], sys.argv[2].rstrip('/'), sys.argv[3].rstrip('/'), sys.argv[4], sys.argv[5], sys.argv[6] == '1'
vj = json.load(open(vjson, encoding='utf-8'))
FEATURES = {}  # كل خصائص اللانشر عندنا false (لا ديمو، لا دقة مخصصة...)

def rule_applies(rule):
    """هل ينطبق الشرط على منصتنا؟ (os.name/os.arch/features)"""
    o = rule.get('os')
    if o:
        if 'name' in o and o['name'] != osname: return False
        if 'arch' in o and o['arch'] != arch: return False
        if 'version' in o:
            import re
            osv = os.environ.get('OS_VERSION', '10.0')
            if not re.match(o['version'], osv): return False
    f = rule.get('features')
    if f:
        for k, v in f.items():
            if FEATURES.get(k, False) != v: return False
    return True

def allowed(lib):
    rules = lib.get('rules')
    if not rules: return True          # بلا rules = مسموح
    ok = False
    for r in rules:
        if rule_applies(r):
            ok = (r.get('action') == 'allow')
    return ok

def platform_classifiers():
    """أصناف النيتيفز المقبولة للمنصة الحالية (نمط الميتاداتا الجديد: الاسم يحمل :natives-*)"""
    if osname == 'windows':  return ['natives-windows'] if arch in ('x86_64','x86') else ['natives-windows-arm64']
    if osname == 'linux':    return ['natives-linux']   if arch in ('x86_64','x86') else ['natives-linux-arm64']
    if osname == 'osx':      return ['natives-macos']   if arch in ('x86_64','x86') else ['natives-macos-arm64']
    return []

PLATFORM_CLS = platform_classifiers()
files, natives, cp, skipped = {}, [], [], []
for lib in vj['libraries']:
    if not allowed(lib):
        skipped.append(lib['name']); continue
    parts = lib['name'].split(':')
    cls = parts[3] if len(parts) > 3 else None
    is_nat = bool(cls) and cls.startswith('natives-')
    if is_nat and not strict and cls not in PLATFORM_CLS:
        skipped.append(lib['name'] + ' (معمارية أخرى)'); continue
    d = lib.get('downloads') or {}
    art = d.get('artifact')
    if art:
        dest = "%s/libraries/%s" % (out, art['path'])
        files[dest] = (art['sha1'], art['size'], art['url'])
        cp.append(dest)
        if is_nat or 'extract' in lib:
            natives.append(dest)
    # صيغة قديمة (classifiers + natives) — دعم احتياطي
    nat = lib.get('natives')
    if nat and not art:
        key = nat.get(osname)
        if key:
            key = key.replace('${arch}', '64' if arch in ('x86_64','x86') else 'arm64')
            c = (d.get('classifiers') or {}).get(key)
            if c:
                dest = "%s/libraries/%s" % (out, c['path'])
                files[dest] = (c['sha1'], c['size'], c['url'])
                natives.append(dest)

cp = [out + '/client.jar'] + cp
with open(work + '/plan-libs.tsv', 'w', encoding='utf-8', newline='\n') as f:
    for dest, (sha, size, url) in files.items():
        f.write("%s\t%s\t%s\t%s\n" % (sha, size, url, dest))
with open(work + '/natives-jars.txt', 'w', encoding='utf-8', newline='\n') as f:
    for p in natives: f.write(p + "\n")
with open(work + '/classpath.txt', 'w', encoding='utf-8', newline='\n') as f:
    f.write(';'.join(cp) + "\n")
info = {
    'version': vj['id'], 'type': vj.get('type'), 'releaseTime': vj.get('releaseTime'),
    'mainClass': vj['mainClass'],
    'javaVersion': (vj.get('javaVersion') or {}).get('component'),
    'javaMajorVersion': (vj.get('javaVersion') or {}).get('majorVersion'),
    'minimumLauncherVersion': vj.get('minimumLauncherVersion'),
    'assets': vj.get('assets'),
    'assetIndex': vj['assetIndex']['id'], 'assetIndexSha1': vj['assetIndex']['sha1'],
    'assetIndexSize': vj['assetIndex']['size'], 'assetIndexTotalSize': vj['assetIndex'].get('totalSize'),
    'assetIndexUrl': vj['assetIndex']['url'],
    'clientSha1': vj['downloads']['client']['sha1'], 'clientSize': vj['downloads']['client']['size'],
    'clientUrl': vj['downloads']['client']['url'],
    'loggingFile': ((vj.get('logging') or {}).get('client') or {}).get('file', {}).get('url'),
    'loggingFileSha1': ((vj.get('logging') or {}).get('client') or {}).get('file', {}).get('sha1'),
    'loggingFileName': ((vj.get('logging') or {}).get('client') or {}).get('file', {}).get('id'),
    'librariesFiles': len(files), 'librariesBytes': sum(v[1] for v in files.values()),
    'nativesJars': len(natives), 'classpathEntries': len(cp),
    'classpathChars': len(';'.join(cp)), 'skippedLibraries': len(skipped),
    'os': osname + '-' + arch, 'strictRules': strict,
}
json.dump(info, open(work + '/info.json', 'w', encoding='utf-8'), indent=1)
print("   وحدات مسموحة: %d ملف (%.1f MB) | حُذفت بـ rules: %d | نيتيفز: %d | classpath: %d مدخل (%d حرف)" % (
    len(files), info['librariesBytes']/1048576.0, len(skipped), len(natives), len(cp), info['classpathChars']))
for s in skipped[:6]: print("     - مُستبعد:", s)
PY
  if [ "$WRITE_OUT" = 1 ]; then
    cp -f "$WORK/classpath.txt" "$OUT/classpath.txt" || die "تعذّر كتابة classpath.txt"
    write_meta
  else
    log "   (وضع --check: لا كتابة في مجلد المخرجات)"
  fi
}

# ---------- meta.txt ----------
write_meta() {
  "$PY" - "$VJSON_CACHE" "$WORK/info.json" "$OUT/meta.txt" "$OUT" <<'PY' || die "فشل كتابة meta.txt"
import json, sys, datetime
vj = json.load(open(sys.argv[1], encoding='utf-8'))
info = json.load(open(sys.argv[2], encoding='utf-8'))
outp = sys.argv[4]
lines = [
 "# Weaver — بيانات تجهيز ملفات اللعبة (مولَّد آلياً بواسطة fetch-mc.sh)",
 "version=%s" % vj['id'],
 "type=%s" % (vj.get('type') or ''),
 "releaseTime=%s" % (vj.get('releaseTime') or ''),
 "mainClass=%s" % vj['mainClass'],
 "javaVersion=%s" % info['javaVersion'],
 "javaMajorVersion=%s" % info['javaMajorVersion'],
 "minimumLauncherVersion=%s" % info.get('minimumLauncherVersion'),
 "assetIndex=%s" % vj['assetIndex']['id'],
 "assetIndexSha1=%s" % vj['assetIndex']['sha1'],
 "assetIndexTotalSize=%s" % vj['assetIndex'].get('totalSize'),
 "assetsVersionField=%s" % vj.get('assets'),
 "clientSha1=%s" % vj['downloads']['client']['sha1'],
 "clientSize=%s" % vj['downloads']['client']['size'],
 "librariesFiles=%s" % info['librariesFiles'],
 "librariesBytes=%s" % info['librariesBytes'],
 "librariesSkippedByRules=%s" % info['skippedLibraries'],
 "nativesJars=%s" % info['nativesJars'],
 "classpathEntries=%s" % info['classpathEntries'],
 "classpathChars=%s" % info['classpathChars'],
 "platform=%s" % info['os'],
 "strictRules=%s" % (1 if info['strictRules'] else 0),
 "clientJar=%s/client.jar" % outp,
 "librariesDir=%s/libraries" % outp,
 "nativesDir=%s/natives" % outp,
 "assetsDir=%s/assets" % outp,
 "classpathFile=%s/classpath.txt" % outp,
 "versionJson=%s/version.json" % outp,
 "loggingConfig=logging/%s" % (info.get('loggingFileName') or ''),
 "generated=%s" % datetime.datetime.now().strftime('%Y-%m-%dT%H:%M:%S'),
]
open(sys.argv[3], 'w', encoding='utf-8', newline='\n').write("\n".join(lines) + "\n")
PY
}

# =====================================================================
#  محرّك التنزيل الموازي (curl + xargs + تحقق SHA1 + استكمال + إعادة محاولة)
# =====================================================================
write_worker() {
  cat > "$WORK/worker.sh" <<'WEOF'
#!/usr/bin/env bash
# وسيط تنزيل واحد: سطر واحد بصيغة  sha1<TAB>size<TAB>url<TAB>dest
line="$1"
IFS=$'\t' read -r sha size url dest <<< "$line"
if [ -z "${dest:-}" ] || [ -z "${url:-}" ]; then
  printf 'BADLINE\t%s\n' "$line" >&2; exit 2
fi
mkdir -p "$(dirname "$dest")" || { printf 'FAIL(mkdir)\t%s\n' "$dest"; echo FAIL > "$STATUS_DIR/$sha.stat"; exit 1; }

# مسار سريع: الملف موجود وسليم
if [ -f "$dest" ] && [ "$(stat -c%s "$dest")" = "$size" ] && [ "$(sha1sum "$dest" | cut -d' ' -f1)" = "$sha" ]; then
  echo SKIP > "$STATUS_DIR/$sha.stat"; exit 0
fi

part="$dest.part"
for attempt in 1 2 3; do
  if [ -f "$part" ]; then
    psz="$(stat -c%s "$part" 2>/dev/null || echo 0)"
    if [ "$psz" -ge "$size" ]; then rm -f "$part"; fi   # ملف مكتمل الحجم لكن تالف → نبدأ من الصفر
  fi
  if [ -f "$part" ]; then
    curl -sS -f -L --connect-timeout 20 --retry 2 --retry-delay 2 --retry-connrefused \
         -C - -o "$part" "$url" 2>>"$STATUS_DIR/$sha.err"
  else
    curl -sS -f -L --connect-timeout 20 --retry 2 --retry-delay 2 --retry-connrefused \
         -o "$part" "$url" 2>>"$STATUS_DIR/$sha.err"
  fi
  if [ -f "$part" ] && [ "$(stat -c%s "$part")" = "$size" ] && [ "$(sha1sum "$part" | cut -d' ' -f1)" = "$sha" ]; then
    mv -f "$part" "$dest" && { echo OK > "$STATUS_DIR/$sha.stat"; exit 0; }
  fi
  rm -f "$part"
done
echo FAIL > "$STATUS_DIR/$sha.stat"
printf 'FAIL\t%s\t%s\n' "$url" "$dest"
exit 1
WEOF
}

run_plan() { # $1=plan  $2=tag  → يعيد 0 إن نجحت كل الملفات
  local plan="$1" tag="$2"
  local sdir="$WORK/status-$tag"
  local total; total="$(grep -c . "$plan" 2>/dev/null || echo 0)"
  if [ "$total" = 0 ]; then log "   ($tag: لا ملفات)"; return 0; fi
  rm -rf "$sdir"; mkdir -p "$sdir"
  write_worker
  export STATUS_DIR="$sdir"
  log "→ تنزيل $tag: $total ملف (توازي $JOBS)..."
  local pider=""
  if [ "$PROGRESS" = 1 ]; then
    ( while :; do
        sleep 15
        printf '   [%s] %s/%s ...\n' "$tag" "$(ls "$sdir" 2>/dev/null | grep -c '\.stat$')" "$total" >&2
      done ) & pider=$!
  fi
  # shellcheck disable=SC2016
  xargs -d '\n' -P "$JOBS" -n 1 bash "$WORK/worker.sh" < "$plan" 2>&1 | sed 's/^/   /' | tail -n 20
  [ -n "$pider" ] && kill "$pider" 2>/dev/null
  wait "$pider" 2>/dev/null
  local ok skip fail
  ok="$(cat "$sdir"/*.stat 2>/dev/null | grep -c '^OK')"
  skip="$(cat "$sdir"/*.stat 2>/dev/null | grep -c '^SKIP')"
  fail="$(cat "$sdir"/*.stat 2>/dev/null | grep -c '^FAIL')"
  ok="${ok:-0}"; skip="${skip:-0}"; fail="${fail:-0}"
  log "   النتيجة[$tag]: جديد=$ok موجود=$skip فشل=$fail من $total"
  if [ "$fail" -gt 0 ]; then
    log "   الملفات الفاشلة:"
    grep -l '^FAIL' "$sdir"/*.stat 2>/dev/null | while IFS= read -r s; do
      local h; h="$(basename "$s" .stat)"
      printf '     %s\n' "$(grep -h "^$h" "$plan" | cut -f3,4)"
      [ -s "$sdir/$h.err" ] && tail -n 2 "$sdir/$h.err" | sed 's/^/       /'
    done
    return 1
  fi
  return 0
}

# ---------- client.jar ----------
step_client() {
  local url sha size
  read -r sha size url < <(pyq -c "
import json,sys
d=json.load(open(r'$VJSON_CACHE',encoding='utf-8'))['downloads']['client']
print(d['sha1'], d['size'], d['url'])")
  log "→ client.jar ($(human "$size"))"
  printf '%s\t%s\t%s\t%s\n' "$sha" "$size" "$url" "$OUT/client.jar" > "$WORK/plan-client.tsv"
  run_plan "$WORK/plan-client.tsv" client || return 1
  return 0
}

# ---------- log4j config ----------
step_logging() {
  local url sha size
  read -r url sha size < <(pyq -c "
import json
d=(json.load(open(r'$VJSON_CACHE',encoding='utf-8')).get('logging') or {}).get('client',{}).get('file') or {}
print(d.get('url','') or '-', d.get('sha1','') or '-', d.get('size',0))")
  [ "$url" != "-" ] || return 0
  mkdir -p "$OUT/logging"
  printf '%s\t%s\t%s\t%s\n' "$sha" "$size" "$url" "$OUT/logging/client-1.21.2.xml" > "$WORK/plan-logging.tsv"
  run_plan "$WORK/plan-logging.tsv" logging || return 1
  return 0
}

# ---------- المكتبات ----------
step_libs() {
  run_plan "$WORK/plan-libs.tsv" libs || return 1
  return 0
}

# ---------- استخراج النيتيفز ----------
# ملاحظة مهمة: في ميتاداتا 26.x صارت جرات النيتيفز تحمل المسار الداخلي الجديد
#   windows/x64/org/lwjgl/<lib>.dll   (بدل الملفات المسطّحة في النسخ القديمة)
# لذلك: (1) نستخرج كامل الشجرة كما هي داخل natives/، (2) ننسخ كل مكتبة أصلية
# إلى natives/java/ بشكل مسطّح ليعمل java.library.path=${natives_directory}/java،
# (3) ننشئ مجلدات العمل lwjgl/jna/netty كما تعلنها وسائط الـJVM في version.json.
step_natives() {
  local list="$WORK/natives-jars.txt" jar n=0 bad=0 tree=0 flat=0
  [ -s "$list" ] || { log "   (لا نيتيفز)"; return 0; }
  log "→ استخراج النيتيفز..."
  mkdir -p "$OUT/natives"
  while IFS= read -r jar; do
    [ -n "$jar" ] || continue
    [ -f "$jar" ] || { warn "نيتيفز مفقود: $jar"; bad=$((bad+1)); continue; }
    local d="$OUT/natives"
    [ "$STRICT_RULES" = 1 ] && d="$OUT/natives/$(basename "$jar" .jar)"   # كل صنف في مجلد مستقل (تفادي تعارض DLL بين المعماريات)
    mkdir -p "$d"
    # ملاحظة: خيار -x في unzip لا يطبّق الاستثناءات بشكل موثوق في MSYS،
    # لذلك نستخرج ثم نحذف META-INF (مطابق لقاعدة extract.exclude=["META-INF/"] في الميتاداتا القديمة)
    if unzip -o -q "$jar" -d "$d"; then rm -rf "$d/META-INF"; n=$((n+1)); else warn "فشل استخراج: $jar"; bad=$((bad+1)); fi
  done < "$list"

  if [ "$STRICT_RULES" != 1 ]; then
    mkdir -p "$OUT/natives/java" "$OUT/natives/lwjgl" "$OUT/natives/jna" "$OUT/natives/netty"
    local f
    while IFS= read -r f; do cp -f "$f" "$OUT/natives/java/"; done \
      < <(find "$OUT/natives" -type f \( -iname '*.dll' -o -iname '*.so' -o -iname '*.dylib' \) ! -path "$OUT/natives/java/*")
  fi
  tree="$(find "$OUT/natives" -type f ! -path "$OUT/natives/java/*" | wc -l | tr -d ' ')"
  flat="$(find "$OUT/natives/java" -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ')"
  log "   استُخرجت $n/$((n+bad)) جرة → $OUT/natives/ (شجرة: $tree ملف) | مسطّح في natives/java: $flat مكتبة"
  [ "$bad" -eq 0 ] || return 1
  return 0
}

# ---------- الأصول (assets) ----------
build_assets_plan() { # $1 = مسار ملف فهرس الأصول
  "$PY" - "$1" "$OUT" "$WORK" "$RESOURCES_BASE" <<'PY' || return 1
import json, sys
idxp, out, work, base = sys.argv[1], sys.argv[2].rstrip('/'), sys.argv[3].rstrip('/'), sys.argv[4]
d = json.load(open(idxp, encoding='utf-8'))
objs = d['objects']
n = 0; total = 0
with open(work + '/plan-assets.tsv', 'w', encoding='utf-8', newline='\n') as f:
    for name, o in objs.items():
        h = o['hash']; s = o['size']
        url = "%s/%s/%s" % (base, h[:2], h)
        dest = "%s/assets/objects/%s/%s" % (out, h[:2], h)
        f.write("%s\t%s\t%s\t%s\n" % (h, s, url, dest))
        n += 1; total += s
json.dump({'assetObjects': n, 'assetBytes': total, 'assetIndexId': d.get('id', '')},
          open(work + '/info-assets.json', 'w', encoding='utf-8'))
print("   كائنات الأصول: %d ملف، %.1f MB" % (n, total/1048576.0))
PY
}

step_assets() {
  local idx_id idx_url idx_sha idx_dest
  read -r idx_id idx_url idx_sha < <(pyq -c "
import json
d=json.load(open(r'$VJSON_CACHE',encoding='utf-8'))['assetIndex']
print(d['id'], d['url'], d['sha1'])")
  mkdir -p "$OUT/assets/indexes" "$OUT/assets/objects"
  idx_dest="$OUT/assets/indexes/$idx_id.json"
  log "→ فهرس الأصول id=$idx_id"
  if [ -f "$idx_dest" ] && [ "$(sha1_of "$idx_dest")" = "$idx_sha" ]; then
    log "   الفهرس موجود ومطابق — يُستخدم"
  elif [ "$WRITE_OUT" = 0 ]; then
    warn "الفهرس مفقود في وضع --check — تُتخطّى مرحلة الأصول"
    return 0
  else
    curl -sS -f -L --connect-timeout 20 --retry 3 --retry-delay 2 -o "$idx_dest.part" "$idx_url" || die "فشل تنزيل فهرس الأصول"
    mv -f "$idx_dest.part" "$idx_dest"
    [ "$(sha1_of "$idx_dest")" = "$idx_sha" ] || die "sha1 فهرس الأصول غير مطابق"
  fi
  build_assets_plan "$idx_dest" || die "فشل بناء خطة الأصول"
  run_plan "$WORK/plan-assets.tsv" assets || return 1
  return 0
}

# =====================================================================
#  التحقق الشامل
# =====================================================================
verify_all() {
  local rc=0
  log "== التحقق من المخرجات =="
  [ -s "$OUT/version.json" ] || { log "  ✗ version.json مفقود"; return 1; }
  [ -s "$OUT/client.jar" ]  || { log "  ✗ client.jar مفقود"; rc=1; }
  [ -s "$OUT/classpath.txt" ] || { log "  ✗ classpath.txt مفقود"; rc=1; }
  [ -s "$OUT/meta.txt" ] || { log "  ✗ meta.txt مفقود"; rc=1; }

  # client.jar مقابل sha1 في version.json
  local csha got
  csha="$(pyq -c "import json;print(json.load(open(r'$OUT/version.json',encoding='utf-8'))['downloads']['client']['sha1'])")"
  got="$(sha1_of "$OUT/client.jar")"
  if [ "$got" = "$csha" ]; then log "  ✓ client.jar sha1 مطابق"; else log "  ✗ client.jar sha1 غير مطابق ($got != $csha)"; rc=1; fi

  # أدوات meta المطلوبة
  for k in mainClass javaVersion assetIndex; do
    grep -q "^$k=" "$OUT/meta.txt" && log "  ✓ meta.txt فيه $k" || { log "  ✗ meta.txt ينقصه $k"; rc=1; }
  done

  # كل مدخل في classpath موجود فعلاً
  local missing=0 count=0 p
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    count=$((count+1))
    [ -f "$p" ] || { missing=$((missing+1)); [ "$missing" -le 5 ] && log "  ✗ مدخل مفقود في classpath: $p"; }
  done < <(tr ';' '\n' < "$OUT/classpath.txt")
  if [ "$missing" = 0 ]; then log "  ✓ classpath: كل الـ$count مدخل موجودة"; else log "  ✗ classpath: $missing من $count مدخل مفقودة"; rc=1; fi

  # النيتيفز: كل مدخل داخل جرات النيتيفز موجود في natives/ بنفس المسار الداخلي + مرآة مسطّحة
  if [ -s "$WORK/natives-jars.txt" ] && [ -d "$OUT/natives" ]; then
    local nmiss=0 njar=0 nflat=0
    while IFS= read -r p; do
      [ -n "$p" ] || continue
      [ -f "$p" ] || continue
      njar=$((njar+1))
      local tdir="$OUT/natives"
      [ "$STRICT_RULES" = 1 ] && tdir="$OUT/natives/$(basename "$p" .jar)"
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        case "$f" in META-INF/*|*/) continue ;; esac
        [ -f "$tdir/$f" ] || { nmiss=$((nmiss+1)); [ "$nmiss" -le 5 ] && log "  ✗ نيتيف ناقص: $f"; }
        case "$f" in
          *.dll|*.so|*.dylib) [ -f "$OUT/natives/java/$(basename "$f")" ] || nflat=$((nflat+1)) ;;
        esac
      done < <(unzip -Z1 "$p" 2>/dev/null)
    done < "$WORK/natives-jars.txt"
    if [ "$nmiss" = 0 ] && [ "$nflat" = 0 ]; then
      log "  ✓ natives: كل مداخل $njar جرة موجودة بنفس المسار + نسخة مسطّحة في natives/java"
    else
      log "  ✗ natives: ناقص في الشجرة=$nmiss، ناقص في المرآة المسطّحة=$nflat"; rc=1
    fi
  else
    log "  - natives: تخطّي (بلا جرات)"
  fi

  # الأصول: عدد وسلامة sha لكل الكائنات (قراءة كاملة)
  if [ -f "$WORK/plan-assets.tsv" ] && [ -d "$OUT/assets/objects" ]; then
    local a_total a_bad=0 a_missing=0 sha size url dest
    a_total="$(grep -c . "$WORK/plan-assets.tsv")"
    while IFS=$'\t' read -r sha size url dest; do
      [ -n "$dest" ] || continue
      if [ ! -f "$dest" ]; then a_missing=$((a_missing+1)); continue; fi
      [ "$(sha1_of "$dest")" = "$sha" ] || a_bad=$((a_bad+1))
    done < "$WORK/plan-assets.tsv"
    if [ "$a_missing" = 0 ] && [ "$a_bad" = 0 ]; then
      log "  ✓ assets: $a_total كائن — كل البصمات صحيحة"
    else
      log "  ✗ assets: مفقود $a_missing / تالف $a_bad من $a_total"; rc=1
    fi
  else
    log "  - assets: تخطّي (لم تُنزَّل بعد)"
  fi
  return $rc
}

# =====================================================================
#  التشغيل
# =====================================================================
log "Weaver fetch-mc | out=$OUT | work=$WORK | jobs=$JOBS | steps=$STEPS"

if [ "$CHECK" = 1 ]; then
  # وضع التحقق فقط: بلا شبكة وبلا كتابة في مجلد المخرجات
  [ -s "$OUT/version.json" ] || die "--check يحتاج $OUT/version.json"
  VID="$(pyq -c "import json;print(json.load(open(r'$OUT/version.json',encoding='utf-8'))['id'])")"
  VJSON_CACHE="$OUT/version.json"
  [ -s "$WORK/$VID.json" ] || cp -f "$OUT/version.json" "$WORK/$VID.json"
  export VID VJSON_CACHE
  build_plan
  # خطة الأصول من الفهرس المحلي (إن وُجد) للتحقق من البصمات
  local_idx="$(find "$OUT/assets/indexes" -maxdepth 1 -name '*.json' 2>/dev/null | head -n 1)"
  if [ -n "$local_idx" ]; then build_assets_plan "$local_idx" || true; fi
  verify_all
  exit $?
fi

fetch_meta
build_plan

if [ "$DRY_RUN" = 1 ]; then
  "$PY" - "$WORK/info.json" "$OUT" <<'PY'
import json, sys
i = json.load(open(sys.argv[1], encoding='utf-8'))
print("== خطة (dry-run) ==")
print("  الإصدار: %s (%s)" % (i['version'], i['type']))
print("  mainClass: %s | javaVersion: %s (jdk %s) | assetIndex: %s" % (i['mainClass'], i['javaVersion'], i['javaMajorVersion'], i['assetIndex']))
print("  منصة التقييم: %s (strict-rules=%s)" % (i['os'], i['strictRules']))
print("  المكتبات: %d ملف، %.1f MB (مستبعد بـ rules: %d)" % (i['librariesFiles'], i['librariesBytes']/1048576.0, i['skippedLibraries']))
print("  النيتيفز: %d جرة تُستخرج إلى natives/" % i['nativesJars'])
print("  client.jar: %.1f MB | الأصول: %.1f MB" % (i['clientSize']/1048576.0, i['assetIndexTotalSize']/1048576.0))
print("  classpath: %d مدخل، %d حرف" % (i['classpathEntries'], i['classpathChars']))
print("  مجلد الإخراج: %s" % sys.argv[2])
print("  (dry-run: لم يُنزَّل شيء)")
PY
  [ "$DO_VERIFY" = 1 ] && verify_all
  exit 0
fi

FAILED=""
if step_enabled meta; then :; fi   # meta أُنجز أعلاه
if step_enabled logging; then step_logging || FAILED="$FAILED logging"; fi
if step_enabled client;  then step_client  || FAILED="$FAILED client"; fi
if step_enabled libs;    then step_libs    || FAILED="$FAILED libs"; fi
if step_enabled natives; then step_natives || FAILED="$FAILED natives"; fi
if step_enabled assets;  then step_assets  || FAILED="$FAILED assets"; fi

local_rc=0
if [ "$DO_VERIFY" = 1 ]; then verify_all || local_rc=1; fi

if [ -n "$FAILED" ]; then
  log ""
  log "انتهى مع نواقص في:$FAILED"
  log "أعد التشغيل مع --resume لإكمال الناقص (الملفات السليمة تُتخطّى)."
  exit 1
fi
log ""
log "تمّ ✅  المخرجات في: $OUT"
log "  version.json | client.jar | libraries/ | natives/ | assets/ | classpath.txt | meta.txt"
exit $local_rc
