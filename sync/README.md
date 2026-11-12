# Sync

Manifest and mirror synchronization for Weaver. The client verifies the manifest
and SHA-256 hashes, diffs a content-addressed store, downloads missing blobs, and
places them atomically. It does not scan or sign jars.

## Build

```powershell
.\gradlew.bat :sync:build
```

## Manifest server

```powershell
java -cp sync/build/libs/sync-2.0.0-SNAPSHOT.jar weaver.sync.server.ManifestServer `
  --content <dir> --port 8787 --pack-id local-smp --pack-name "Local SMP"
```

## Client

```powershell
java -cp sync/build/libs/sync-2.0.0-SNAPSHOT.jar weaver.sync.client.SyncClient `
  --manifest-url http://127.0.0.1:8787/weaver/manifest.json `
  --target <mods-dir> --store <cache-dir> [--markers <file>]
```

## Mirrors

`weaver.mirror.server.MirrorServer` serves the same manifest with multiple blob
roots. `weaver.mirror.client.MirrorSyncClient` adds mirror failover and resumable
downloads. Both accept `--help`-style unknown-argument errors and print
`[weaver-mirror]` markers.

## Download limits

Both clients stream response bodies with hard limits, so a broken or hostile
server cannot exhaust memory or fill the disk:

- The manifest is capped at 4 MiB by default. Override it with
  `--max-manifest-bytes <n>`. A larger `Content-Length`, or a body that streams
  past the cap, fails with `body-too-large` (`SyncClient` exits 2;
  `MirrorSyncClient` logs `MANIFEST FAILED` and exits 2).
- A blob is capped at the `size` declared in the manifest. `SyncClient` treats a
  longer body as a failed URL (`DOWNLOAD url-failed ... body-too-large`) and
  rejects a declared size over 2 GiB (`ARTIFACT REJECTED ... reason=bad-size`).
  `MirrorSyncClient` stops writing at the declared size, removes the `.part`,
  logs `DOWNLOAD OVERSIZE`, and moves on to the next mirror. A blob without a
  declared size is capped at 2 GiB (`SyncClient`, in memory) or 1 GiB
  (`MirrorSyncClient`, on disk).

## Leftover `.part` files

`MirrorSyncClient` resumes a leftover `<sha>.part` with `Range: bytes=<kept>-`.
When the leftover is already at least the declared size, it cannot be resumed,
so the client checks its SHA-256 directly: on a match it is moved into the store
without any network request (`RESUME COMPLETE`), otherwise it is deleted and
downloaded again (`RESUME DISCARDED`). When a mirror answers a resume with
`416 Range Not Satisfiable`, the client truncates the `.part` and retries that
mirror once from byte 0 (`RANGE NOT SATISFIABLE`).
