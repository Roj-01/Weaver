# Weaver fix notes — 2026-11-05

هذا الملف مخصص لتسجيل أي حل مؤقت/workaround، والقرارات، والمشاكل الأخرى المكتشفة أثناء إصلاح المشاكل الثلاث. لا أستخدم أي workaround غير مذكور هنا.

## Problem 1 — Transformer install order

- **الحل الدائم:** تركيب كل الـclass-file transformers قبل أي تحميل لكلاس لعبة ناتج عن الـAgent نفسه، ثم تفعيل `NetworkBridgeInstall.activate` و`CapabilitySyncInstall.install` بعدها.
- **حارس ترتيب:** `BootPhaseOrder` يمنع تفعيل مكوّنات اللعبة الاختيارية قبل اكتمال تركيب الـtransformers.
- **دليل الترتيب:** `ClassLoadAudit` يسجّل كلاسات `net.minecraft.*` المحمّلة عند مرحلتين: قبل اكتمال تركيب الـtransformers، وقبل `AGENT READY`.
- **ليس workaround:** إعادة ترتيب دائمة + حارس + تدقيق.
- **ملاحظة:** `SHARED` mode pre-seeds `api-game` classes inside `ClassSpace.preseedApiGame`; لم أتحقق من أنه لا يحمّل كلاسات لعبة قبل الـtransformers. هذه مخاطرة مسجلة، وليست ضمن برهان SYSTEM.

## Problem 2 — Silent lifecycle injection failure

- **الافتراضي المقترح والمنفذ:** `fatal`.
- **السبب:** إذا لم يُحقن `BuiltInRegistries.bootStrap/freeze` أو `Minecraft.<init>` أو `MinecraftServer.runServer`، فإن المودات قد لا تُحمّل أو لا تعمل مراحل الحياة، والفشل الصامت يجعل السبب غامضاً. الإيقاف الواضح أفضل من تشغيل لعبة بلا محمّل فعّال.
- **الخيار التشخيصي:** `-Dweaver.lifecycle.injection=warn` يسجّل الخطأ بدون إيقاف. أي قيمة أخرى أو غياب الخاصية = `fatal`.
- **الرسالة المطلوبة:** `[weaver] FATAL LIFECYCLE INJECTION MISSING method=...`
- **التنفيذ:** تسجيل الخطأ عند التحويل، ثم `LifecycleInjection.enforce()` عند أول tick للعميل/السيرفر. لا يوجد رمي داخل `ClassFileTransformer` لأنه قد يُبتلع من الـJVM؛ الإنفاذ عند أول tick يضمن fail-fast فعلياً.
- **workarounds:** لا يوجد.

## Verification — Problem 1 (actual)

- `BootPhaseOrderTest` + `NetworkBridgeInstallTest`: PASS.
- Full `:core:test`: PASS.
- Proof server: `[weaver] CLASSLOAD AUDIT phase=before-all-transformers gameClasses=0`; after activation: 10 network classes listed; `GAME HOOK SUMMARY applied=49 skipped=10 missing=0 invalid=0 required_misses=none`.
- Client proof: same audit result (`before-all-transformers gameClasses=0`); `GAME HOOK SUMMARY applied=49 skipped=10 ...`; `CLIENT PROOF RESULT ok=true side=client`.

## Verification — Problem 2 (actual)

- `LifecycleInjectionTest`: 4/4 PASS (default fatal, warn path, and all three transformers).
- Full server proof after the change: no `FATAL LIFECYCLE INJECTION MISSING`, `LIFECYCLE COMPLETE expected-client=false expected-server=true`, hook summary unchanged.
- Client proof after the change: no `FATAL LIFECYCLE INJECTION MISSING`, `LIFECYCLE COMPLETE expected-client=true expected-server=true`, hook summary unchanged.
- `LifecycleInjection.enforce()` runs on first client/server tick; it does not throw inside a `ClassFileTransformer`.
- No temporary workaround used.

## Problem 3 — Dev tools and proofs

- **launcher debug:** أُضيف `-Debug` و`-DebugPort` (1..65535) و`-DebugSuspend` إلى `tools/launcher/launch.ps1` عبر JDWP.
- **التحقق من launcher:** `-DryRun` أنتج `LAUNCH DEBUG side=client port=5006 suspend=y` و`-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006`، وبقي التشغيل الافتراضي بلا JDWP. لم أفتح جلسة debugger فعلية.
- **source-patch client:** فشل سابقاً في الوصول إلى الماركر. بعد `build.ps1` كامل (`sourcepatcher` + `source-patch-proof`) نجح من المحاولة الأولى:
  `SOURCE PATCH PROOF ok=true side=client value=source:minecraft:stonex1`.
  ثم `run-all.ps1` أنتج `SOURCE PATCH PROOF RUN ok=true server=ok client=ok`.
  **السبب الجذري للفشل السابق غير مثبت**؛ الأرجح أن مخرجات `out/` كانت قديمة/غير مكتملة. لم أعدّل أي كود إصلاحًا لهذه المشكلة.
- **release-checklist.md:106:** فُصل السطر إلى صفين (binary patched / source-patched) مع تواريخ الأدلة الفعلية. لا توجد ادعاءات نجاح غير مدعومة.
- **workarounds:** لا يوجد.

## Issues discovered but NOT fixed in this scope

1. `README.md:209-211` يقول إن source patches/decompilation/recompilation "future phases" بينما `tools/sourcepatcher` موجود. تعارض داخلي في README. لم يُصلح الآن.
2. `docs/README.md:90-91` يصف بوابة 16 خطوة بينما `tools/verify-ci.ps1` الحالي 17 خطوة.
3. `docs/compatibility.md:234-236` يذكر 38-spec/"current 55-spec" بينما الكود الحالي 59 spec.
4. `docs/release-checklist.md:269` يقول "No automatic client command-tree sync; in-memory permissions; no live node removal" بينما `docs/commands.md` + `ClientCommandTreeMixin` + `JsonPermissionStore` + `CoreCommandBackend` تثبت العكس.
5. `docs/network.md:1079-1081` يقول إن bridge "inert" و`LoaderAgent` "intentionally unchanged" بينما `LoaderAgent` يستدعي `NetworkBridgeInstall.arm/activate` والـclient proof يثبت round trip.
6. `examples/source-patch-proof/README.md:27` يقول "this checkout has **not** executed the build or the game proof" بينما الخادم والعميل ثبتا الآن (`SOURCE PATCH PROOF RUN ok=true server=ok client=ok`). الوثيقة قديمة؛ لم أصلحها لأنها خارج نطاق المشاكل الثلاث.
7. `examples/toolchain-fixture/README.md:107-119` يقول إن loader لا يكتشف nested jars بينما `ModLoader.withNested` موجود؛ الـproof end-to-end عبر `LoaderAgent` غير موجود.
8. `tools/verify.ps1` يطبع `Step 9`/`Step 6` في بعض فروع الفشل بدل `Step 7`/`Step 8` (أسطر 307 و336). لم يُصلح الآن.
9. `diagnostics/README.md:16` يذكر `diagnostics-2.0.0-SNAPSHOT.jar` بينما المخرج `2.1.0-SNAPSHOT`.
10. `tools/launcher/launch.ps1` لم يكن يدعم JDWP قبل هذه المهمة؛ أُضيف الآن.
11. `GameHook SUMMARY` يعرض `applied=49 skipped=10` من 59 spec؛ 10 skipped عند snapshot أول tick (side/classload timing) وليست كلها فشلاً. لم تُغيّر السياسة.
12. `verify-ci` الكامل 17/17 لم يُشغّل على HEAD؛ الأدلة مجمّعة من تشغيلات منفصلة. لم يُصلح الآن.
13. لم أتحقق من `SHARED` mode بعد إعادة الترتيب؛ يحتاج تشغيلاً منفصلاً.
14. لم أفتح جلسة debugger فعلية بعد إضافة JDWP؛ سأتحقق من بناء الأمر فقط.

## Resolution — 2026-11-06 (branch hardening/known-issues-and-infra)

Status of the "discovered but not fixed" list above:

1. Fixed: README now describes `tools\sourcepatcher` as implemented.
2. Fixed: docs/README.md says 17-step gate.
3. Fixed: docs/compatibility.md cites the current 59-spec catalog (asserted by `HookContractTest`).
4. Fixed: release-checklist row now lists command-tree sync, JSON permissions, and live removal as implemented.
5. Fixed: docs/network.md (two places) says `LoaderAgent` arms and activates the bridge.
6. Fixed: source-patch-proof README records the verified 2026-11-05 run.
7. Fixed: nested jars are discovered by `LoaderAgent` itself; `ModLoaderNestedJarTest` proves it end to end. Docs updated.
8. Fixed: verify.ps1 step labels and capture-log names match their steps.
9. Fixed: diagnostics README uses `2.1.0-SNAPSHOT`.
10. Already done before this branch.
11. Fixed: `GAME HOOK SUMMARY` now reports not-yet-loaded specs as `pending=N` instead of `skipped`; required specs whose class is absent are MISSING, and late MISSING/INVALID results are logged and enforced in strict mode.
12. Done: full `tools\verify-ci.ps1` run on this branch (see the release checklist for the recorded summary).
13. Done: shared mode passes the gate after the transformer reordering (`shared_mode=proof-pass`).
14. Still open: no interactive debugger session has been attached through the JDWP flags.

The branch also fixes 33 defects from a read-only audit of the boot path,
event/registry API, networking, and persistence; see the branch history and
docs/neoforge-parity-plan.md for follow-up feature work. One audit item is
deliberately not changed: a client that only joins remote servers never
reaches `onLoadComplete` (documented in docs/architecture.md as an open
lifecycle decision).
