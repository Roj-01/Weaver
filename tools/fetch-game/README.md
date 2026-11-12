# fetch-game

Downloads Minecraft 26.3 into `work/game-26.3` and extracts the real server
jar into `work/server-26.3`.

```bash
bash tools/fetch-game/fetch-game.sh
powershell -NoProfile -ExecutionPolicy Bypass -File tools/fetch-game/fetch-server.ps1
```

The client fetch uses `fetch-mc.sh` (client jar, libraries, natives, assets,
`classpath.txt`). The server fetch downloads `server.jar`, then runs the
bundler once so it extracts `versions/26.3/server-26.3.jar` and `libraries/`.

Override the output directories with `--out` or `WEAVER_GAME_DIR` /
`WEAVER_SERVER_DIR`.
