# Diagnostics

Crash-report analyzer for Weaver. It reads a Java `crash-report.txt`, extracts the
head exception and stack frames, classifies frames as mod/vanilla/loader/platform,
and ranks likely mod candidates.

## Build

```powershell
.\gradlew.bat :diagnostics:build
```

## Run

```powershell
java -jar diagnostics/build/libs/diagnostics-2.3.2.jar <crash-report.txt> [options]
```

| Option | Meaning |
|---|---|
| `--mods <file>` | Known mod ids, one per line; `#` starts a comment |
| `--format text\|json\|top1` | Output format (default `text`) |
| `--top <N>` | Number of candidates in text output (default 3) |
| `--out <file>` | Write the JSON report to a file |
| `-h`, `--help` | Show usage |

Exit codes: `0` success, `1` internal error, `2` usage error, `3` read/write error.

## Fixtures

The `fixtures/` directory contains five crash reports covering a mod event
exception, a missing Mixin target, a resource conflict, a vanilla-only crash, and
a Mixin runtime exception. Use `--format top1` to check the expected top candidate.
