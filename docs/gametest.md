# Game tests for mods

Mods write in-game tests and run them headless: no client, no window, and a
JUnit-style report CI can show. Weaver uses vanilla's game test framework
(`net.minecraft.gametest`); it adds registration from code and a way to start
the game test server with the Weaver agent and your mods.

## Writing tests

```java
@Override
public void onInitialize(ModContext context) {
    GameTests.register(Identifier.fromNamespaceAndPath("examplemod", "ruby_block_places"), helper -> {
        helper.setBlock(0, 0, 0, RUBY_BLOCK);
        helper.assertBlockPresent(RUBY_BLOCK, 0, 0, 0);
        helper.succeed();
    });

    GameTests.test(Identifier.fromNamespaceAndPath("examplemod", "machine_fills_up"))
            .structure(Identifier.fromNamespaceAndPath("examplemod", "machine_room"))
            .maxTicks(200)
            .register(helper -> helper.succeedWhen(() -> helper.assertTrue(isFull(helper), "machine full")));
}
```

`GameTests` is in `api-game` (`net.weaver.api.game.gametest`). Register
in `onInitialize`: the test function goes into vanilla's test function
registry, which freezes after mod initialization, and a call after that
throws. The test body gets vanilla's `GameTestHelper`: positions are relative
to the test structure, and a test ends with `succeed()`, `succeedWhen(...)`,
or a failure (`fail(...)`, a failed assertion, or the time limit).

Weaver writes the matching `test_instance` file into a generated data pack
(`weaver/gametests`), so tests need no data files. Options on `test(id)`:

| Option | Default | Meaning |
|---|---|---|
| `structure` | `minecraft:empty` (one block) | The structure placed for the test, from `data/<ns>/structure/<path>.nbt`. |
| `maxTicks` | 100 | Ticks before the test fails by timeout. |
| `setupTicks` | 0 | Ticks between placing the structure and starting the test. |
| `required` | `true` | Whether a failure fails the run. |
| `environment` | `minecraft:default` | The test environment (game rules, weather, time). |

Mods can still ship their own `test_instance` files; vanilla runs those too.

## Running tests

With the Gradle plugin:

```bash
gradlew weaverGameTest
```

```bash
gradlew weaverGameTest -Pweaver.gameTests=examplemod:*
```

The task stages the mod like `runServer`, starts vanilla's game test server
(`net.minecraft.gametest.Main`) on the dedicated-server classpath with the
agent, and runs every test (or the selection). The test world goes in
`build/weaver/gametest/universe` (emptied first), the report in
`build/weaver/gametest/report.xml`, and the log in
`build/weaver/logs/gametest.log`. The server exits with the number of failed
required tests, so any required failure fails the build. Set the selection in
the build script with `weaver { gameTests.set("examplemod:*") }`. Gradle must
run on JDK 26, like the other Weaver tasks.

Without Gradle, `examples/gametest/run.ps1` does the same for the mods in
`examples/gametest/out/mods`. It prints a `GAMETEST CASE` line per test and
`GAMETEST RESULT ok=... exit=... tests=... failed=...`.

Any server with the mods can also run the tests by hand with vanilla's
`/test` command.

## The report

The report is vanilla's JUnit-style XML: one `testcase` per test, named by
test id. A failed required test has a `failure` element. A failed optional
test is reported as `skipped`, with the failure message, and does not fail
the run.

## Example and gate step

`examples/gametest` is a small mod with four tests:

- `place_block` places and checks a block;
- `chest_item_handler` uses Weaver's item lookup on a chest;
- `waits_ticks` succeeds after ten ticks;
- `expected_failure` is optional and fails on purpose.

Gate step `H-gametest` builds it, runs it, and requires those results plus
exit code 0. It then runs it again with `WEAVER_GAMETEST_FORCE_FAILURE=1`,
which adds a failing required test, and requires the run to fail.

## Limitations

- Tests run on the dedicated-server side only; there are no client tests.
- No Weaver-side batching, retries, or per-test environments beyond what
  vanilla's framework and test instance files offer.
- The CI workflow runs the gate only where the game files are present
  (`tools/fetch-game`).
