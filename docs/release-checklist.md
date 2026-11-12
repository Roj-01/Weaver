# Release checklist

Release validation checklist for Weaver 2.2 on Minecraft 26.3. Run every
command from the repository root unless a step says otherwise. Record the
observed result next to the item; do not check a box from an earlier run
unless the exact marker is present in the current logs.

This page intentionally names the unfinished-work markers that the hygiene
gate searches for. `tools/verify-ci.ps1` excludes this page from that scan so
the checklist can describe the rule without failing itself.

Latest recorded gate summary on 2026-11-06, branch
`hardening/known-issues-and-infra` (run captured at
`weaver-verify-ci-20261106-173755`; 955 module tests; the client proof passed
on its documented single retry after the first launch exited without a Java
error):

```text
WEAVER VERIFY-CI ok=true steps=17 pass=17 fail=0 skip=0 shared_mode=proof-pass elapsed=207.8s
```

Earlier recorded final gate summary on 2026-11-04 (run captured at
`weaver-verify-ci-20261104-132517`):

```text
WEAVER VERIFY-CI ok=true steps=16 pass=16 fail=0 skip=0 shared_mode=proof-pass elapsed=337.3s
```

All 16 steps in the recorded run passed and no step was skipped. The current gate adds step L (patcher), for 17 steps. Step I accepted the full
shared-mode proof pass as `shared_mode=proof-pass`; that label is an
environment-dependent observation, not a support claim. Do not sign a release
that presents shared class space as supported, regardless of which step I
outcome is observed.

## Build matrix

| Item | Command | Expected | Observed 2026-11-04 |
|---|---|---|---|
| Standard with-game build | `.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL`, one `api-game-*.jar` | PASS |
| No-game build | `.\gradlew.bat clean build --no-build-cache --rerun-tasks --no-daemon -Pweaver.forceNoGameApi=true` | `BUILD SUCCESSFUL`, `[weaver] api-game SKIPPED reason=no-game-classpath`, no `api-game` jar | PASS |
| Required API failure | `.\gradlew.bat build -Pweaver.forceNoGameApi=true -Pweaver.requireGameApi=true` | Non-zero exit, no `BUILD SUCCESSFUL`, all three remediation options printed | PASS |
| Explicit skip | `.\gradlew.bat build -Pweaver.skipGameApi=true` | `BUILD SUCCESSFUL`, `[weaver] api-game SKIPPED reason=skip-game-api`, jar and manifest entry removed | PASS |
| Restore with-game artifacts | `.\gradlew.bat :core:agentJar` | `BUILD SUCCESSFUL`, one agent-lib `api-game` jar and manifest entry | PASS |

Checklist:

- [ ] Standard with-game build passes.
- [ ] No-game build passes and produces no `api-game` artifact.
- [ ] `requireGameApi` fails with all three remediation options.
- [ ] Explicit `skipGameApi` removes the jar and manifest entry.
- [ ] `:core:agentJar` restores exactly one `api-game` jar and manifest entry.
- [ ] Every build command exited with the expected status; the logs are kept.

## Unit tests

Command:

```powershell
.\gradlew.bat :api:test :core:test :api-game:test --rerun-tasks --no-daemon
```

Expected: `BUILD SUCCESSFUL`, zero failures, zero errors, zero skipped.

Observed on 2026-11-05:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `api` | 280 | 0 | 0 | 0 |
| `core` | 451 | 0 | 0 | 0 |
| `api-game` | 150 | 0 | 0 | 0 |
| **Total** | **881** | **0** | **0** | **0** |

The 2.2 baseline was 625 tests (api 259, core 328, api-game 38). The bridge, hook, client, config, command, capability, and watch work adds 21 `api` tests, 123 `core` tests, and 112 `api-game` tests, for 256 new tests.

Checklist:

- [ ] `:api:test` passes with 280 tests.
- [ ] `:core:test` passes with 451 tests.
- [ ] `:api-game:test` passes with 150 tests.
- [ ] Existing `BaseEventCompatibilityTest` and `TypedBinaryCompatibilityTest`
      still pass unchanged.
- [ ] `EventBusPerformanceBudgetTest` passes as part of `:api:test`.
- [ ] Any changed count is explained by an added or removed test class.

## Proofs and acceptance gates

Run the Arabic input mixin probe (no game launch):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\verify-arabic.ps1
```

Run the standalone proofs:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\config-proof\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\network-proof\run.ps1
```

Run the real game proofs sequentially:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\run-server.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\compat-suite\run.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File examples\client-proof\run-client.ps1
```

Expected exact markers and observed outcomes:

| Proof | Required marker | Observed |
|---|---|---|
| Config | `CONFIG PROOF RESULT ok=true checks=58` | PASS |
| Patched game (binary) | `PATCHER VERIFY ok=true`, `PATCHER CLASSLOAD`, `signatures=stripped`; `PATCHED PROOF RUN ok=true server=ok client=ok` | PASS (2026-11-05; dedicated server and client markers observed) |
| Source-patched game | `PATCHER VERIFY source=ok`; `SOURCE PATCH PROOF RUN ok=true server=ok client=ok` | PASS (2026-11-05; `run-all.ps1` first attempt, server and client markers observed) |
| Network | `NETWORK PROOF RUN ok=true server=ok client=ok mismatch=ok` | PASS |
| Main server | `PROOF RESULT ok=true side=server checks=20` | PASS |
| Compatibility suite | `COMPAT SUITE RESULT ok=true side=server checks=20` and `COMPAT SUITE PROOF ok=true` | PASS |
| Client | `CLIENT PROOF RESULT ok=true side=client` with `CLIENT PROOF CLIENT FIXTURE world=` | PASS (final run, first attempt; an earlier full run needed one retry) |
| Shared mode | `shared_mode=documented-blocker` (exact blocker plus `SHARED FACTORY PROBE ok=true`) or `shared_mode=proof-pass` (`CLASS SPACE mode=shared`, `AGENT READY ... class-space=shared`, `PROOF RESULT ok=true side=server checks=20`, `SHARED FACTORY PROBE ok=true`) | PASS: proof-pass observed; experimental, not support |

Client-proof honesty note: in the first full run (earlier 38-spec set), the
first client launch reached client setup and `GAME HOOK SUMMARY applied=30
skipped=8 missing=0 invalid=0 required_misses=none`, but it did not reach
`CLIENT PROOF RESULT
ok=true side=client`; the offline account also logged authentication 401
responses. The single automatic retry copied the fixture world, reached every
client check, and printed the marker. In a later full run, both launches
reached the same point and then the JVM crashed; Windows Application Error
1000 recorded `java.exe` exception `0xc0000005`, faulting module `jvm.dll`,
offset `0xa36da7`. A standalone client run immediately afterward passed, and
the final full run passed on the first attempt. Treat these as environment/JVM
flakiness, not proof assertion failures, and record the crash evidence when
they occur.

Shared-mode honesty note: two consecutive reconciliation full runs on
2026-11-04 (and earlier full runs that day) observed a complete shared-mode
proof pass rather than the documented blocker:

```text
[weaver] CLASS SPACE mode=shared game-entries=40 mods=1 loader=weaver-shared api-game=36
[weaver] AGENT READY side=server mods=1 configs=1 class-space=shared
[weaver] GAME HOOK SUMMARY applied=30 skipped=8 missing=0 invalid=0 required_misses=none
[weaver] MOD weaverproof PROOF RESULT ok=true side=server checks=20
SHARED FACTORY PROBE ok=true
```

`tools/verify-ci.ps1` accepts this as `shared_mode=proof-pass` and accepts the
precise documented blocker as `shared_mode=documented-blocker`; both PASS, both
are labeled experimental and environment-dependent, and neither changes the
support status. Shared class space remains unsupported as a release gate.
Do not cite either outcome as shared-mode support.

Checklist:

- [ ] Config proof prints `checks=58`.
- [ ] Patcher verification prints `PATCHER VERIFY ok=true` and the full class
      load reports `failures=0` for both patched jars.
- [ ] The patched proof prints `PATCHED PROOF RUN ok=true server=ok client=ok`.
- [ ] Network proof prints `server=ok client=ok mismatch=ok`.
- [ ] Main server proof prints `side=server checks=20`.
- [ ] Compatibility suite prints `side=server checks=20` and
      `COMPAT SUITE PROOF ok=true`.
- [ ] Client proof prints `side=client` after using the fixture world; a retry
      is recorded if the first launch fails.
- [ ] Shared-mode outcome is recorded honestly and is not counted as support.
- [ ] Dedicated-server proofs ran sequentially and left no orphan game process.

## Performance

Commands:

```powershell
.\gradlew.bat :api:benchmark
powershell -NoProfile -ExecutionPolicy Bypass -File examples\proof\bench\run.ps1
```

Expected: at least 4 `BENCH` lines from the Gradle benchmark and
`PLATFORM_BENCH BUDGET ok=true` from the platform harness.

Observed on 2026-11-04: PASS, 8 lines matching the gate's `^BENCH ` prefix, and
`PLATFORM_BENCH BUDGET ok=true`. `EventBusPerformanceBudgetTest` also passed as
part of `:api:test`.

The recorded measurement tables, methodology, and regression ceilings live in
[Dispatch model](dispatch.md#performance-model) and
[Dispatch model](dispatch.md#platform-benchmark). Do not copy the numbers into
release notes or this checklist; they drift with CPU, JIT state, and machine
load. Link to the dispatch page instead.

Checklist:

- [ ] `:api:benchmark` prints at least 4 `BENCH` lines.
- [ ] Platform benchmark prints `PLATFORM_BENCH BUDGET ok=true`.
- [ ] No budget was relaxed to make a run pass.
- [ ] Performance claims link to `docs/dispatch.md` rather than duplicating
      measured values.

## Documentation

Every documentation page linked from `docs/README.md` must exist and describe
the shipped surface:

`events.md`, `api-game.md`, `registry.md`, `hooks.md`, `capabilities.md`,
`client.md`, `commands.md`, `config.md`, `network.md`, `architecture.md`,
`datagen.md`, `migration-2.1.md`, `platform-ops.md`, `toolchain.md`,
`dispatch.md`, `compatibility.md`, `release-checklist.md`.

Every feature must have a runnable example and proof, or a documented
limitation. The feature-to-proof matrix in `examples/README.md` covers
network, config, capability, client, command, datagen, hooks, toolchain, and
cross-feature compatibility. Compatibility and release are covered by this
page and `compatibility.md`.

Checklist:

- [ ] Every `docs/README.md` entry exists.
- [ ] `docs/README.md` links `compatibility.md` and `release-checklist.md`
      instead of marking them planned. This is owned by the documentation
      workstream; record an integration request if it is still stale.
- [ ] `docs/README.md` no longer says compatibility evidence will be added when
      `compatibility.md` exists.
- [ ] Every feature has an example and proof, or a limitation that names the
      missing evidence.
- [ ] The compatibility matrix and the recorded gate outcomes match the
      current `tools/verify-ci.ps1` output.
- [ ] Stale claims in other pages are corrected or recorded as an integration
      request. In particular, `docs/platform-ops.md` currently describes
      shared mode as verified while `examples/README.md` and this checklist
      record it as an unsupported gate.

## Hygiene

Commands:

```powershell
git diff --check
git status --porcelain
```

The CI gate also scans tracked source and documentation for the unfinished-work
markers `TODO`, `FIXME`, `XXX`, and stub-style comments, excluding `spike/`,
`build/`, `.gradle/`, `logs/`, `out/`, `fixture-world/`, `work/`, and this
policy page. Hits must be reported as `file:line`.

Observed on 2026-11-04: `git diff --check` clean; the final gate reported 0 hits
in 646 scanned source/docs files. This policy page is excluded from the scan,
so it does not change the count. The count changes as documentation and source
files are added; re-run the gate after any change and record the new count from
the `K-marker-scan` log rather than reusing this number.

Checklist:

- [ ] `git diff --check` is clean.
- [ ] The marker scan reports 0 hits.
- [ ] No commit was created by the verification work.
- [ ] The existing dirty tree was preserved; verification did not run
      `git commit`, `git reset`, `git checkout`, or `git clean`.
- [ ] No orphan Java or game process remains after the proofs.
- [ ] Build outputs are ignored or disposable; no generated artifact is
      presented as a source change.

## Known limitations and blockers

Record these in release notes or accept them explicitly; do not omit them.

| Limitation | Status | Evidence |
|---|---|---|
| Shared class space is experimental and unsupported as a release gate | Environment-dependent; CI records `proof-pass` or `documented-blocker` without changing support status. Two 2026-11-04 runs observed `proof-pass` | `examples/proof/run-server-shared.ps1`, `docs/compatibility.md` |
| Minecraft 26.3 network game bridge needs `api-game` and the game classes | The bridge is installed and proven with a real client/server payload round trip; without `api-game` only the TCP transport is available | `NetworkFeature.isInstalled()`, `CLIENT PROOF NETWORK ok=true`, `docs/network.md` |
| Client proof needs a fixture world, game assets, natives, and a working graphics stack | One earlier 2026-11-04 run needed one retry; another lost both launches to a `jvm.dll` `0xc0000005` APPCRASH, then a standalone run and the final full run passed | Client proof logs, Windows Application Error 1000, `examples/README.md` |
| `api-game` is optional at build time but required by typed mods | No-game and skip builds pass; typed runtime needs the jar | Build matrix steps B and D |
| Weaver is trusted-code only | No sandbox or permission boundary between mods and the game | `docs/architecture.md`, `docs/compatibility.md` |
| Minecraft 26.3 only, no NeoForge compatibility | Game version and hook target are fixed at 26.3 | `WeaverApi`, `docs/api-game.md` |
| Dedicated-server proofs share `work/server-26.3` and fixed ports | Must run sequentially | `examples/README.md` |
| Linux proof path not exercised | Gradle build and tests are platform-neutral; the proof and CI scripts are Windows PowerShell 5.1 only | This checklist |
| Jar-in-jar is proven at the loader level, not yet in a live game boot | `ModLoader.discover` extracts and loads nested jars (`ModLoaderNestedJarTest`); no recorded in-game run of a jar-in-jar mod | `examples/toolchain-fixture/README.md` |
| Command permissions are Weaver-owned, not vanilla op levels or a permissions mod | Client command-tree sync (`ClientCommandTreeMixin`), JSON-persisted permissions (`JsonPermissionStore`), and live node removal by dispatcher rebuild (`CoreCommandBackend`) are implemented; integration with external permission systems is not | `docs/commands.md`, `examples/README.md` |
| Datagen proof does not execute recipes, trigger advancements, or render client assets | Documented datagen gap | `examples/README.md` |
| Arabic input covers `EditBox` only, not custom text widgets; search-highlight formatters and long-text RTL scrolling are approximate | Built-in client feature v1; verified by `tools/verify-arabic.ps1` and an in-game typing test | `docs/client.md` |

## Release sign-off

Do not sign while any required gate is FAIL or any integration request is
unresolved.

- [ ] Build matrix A through E passed in the current run.
- [ ] Unit tests passed with the expected counts.
- [ ] All standalone and with-game proofs passed, or the accepted limitation
      is recorded.
- [ ] Shared-mode outcome is recorded as experimental and environment-dependent
      (`proof-pass` or `documented-blocker`) and is not presented as support.
- [ ] Performance budgets passed; no threshold was weakened.
- [ ] Documentation links, feature-to-proof coverage, and stale claims are
      resolved.
- [ ] Hygiene checks passed and the dirty tree is preserved.
- [ ] `tools/verify-ci.ps1` was run end to end and the final summary is
      attached to the release record.

Sign-off:

| Role | Name | Date | Verdict |
|---|---|---|---|
| Compatibility / CI | | | |
| QA lead | | | |
| Release manager | | | |
