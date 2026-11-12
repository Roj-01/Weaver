package net.weaver.opsproof;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.weaver.api.WeaverApi;
import net.weaver.api.registry.DataPackSnapshot;
import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.DataRegistryManager;
import net.weaver.api.registry.TagKey;
import net.weaver.api.registry.TagView;
import net.weaver.core.ClassSpace;
import net.weaver.core.ModLoader;
import net.weaver.core.modops.ModDiagnostic;
import net.weaver.core.modops.ModList;
import net.weaver.core.modops.ModListEntry;
import net.weaver.core.modops.ModPolicies;
import net.weaver.core.modops.ModPolicyException;
import net.weaver.core.modops.ModStatus;
import net.weaver.core.modops.update.InstalledMod;
import net.weaver.core.modops.update.ModUpdate;
import net.weaver.core.modops.update.UpdateCheck;
import net.weaver.core.modops.update.UpdateCheckRequest;
import net.weaver.core.modops.update.UpdateCheckResult;
import net.weaver.core.modops.update.UpdateChecks;
import net.weaver.core.modops.update.UpdateStatus;
import net.weaver.core.registry.DataPackRepository;
import net.weaver.core.version.CompatibilityMatrix;
import net.weaver.core.version.VersionChecks;
import net.weaver.core.watch.ModChangeReport;
import net.weaver.core.watch.ModWatchService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * Standalone acceptance proof for the Weaver 2.2 Platform Ops workstream.
 *
 * <p>The proof builds synthetic mods in a temporary directory, exercises mod
 * discovery and its class-space policies, verifies the mod-list CLI text and
 * JSON contracts, checks the API/game version matrix, and verifies the update
 * check holder contract. It never launches the game and never needs Gradle
 * once the agent artifacts exist.
 *
 * <p>Every check prints {@code OPS PROOF CHECK ok=true <name>}. The process
 * ends with {@code OPS PROOF RESULT ok=true checks=<n>}. A failure prints
 * {@code OPS PROOF CHECK ok=false <name> reason=...}, then
 * {@code OPS PROOF RESULT ok=false ...}, and exits with code 1.
 */
public final class OpsProof {

    private static final List<String> JSON_MOD_KEYS = List.of(
            "id", "version", "side", "dependencies", "mixins",
            "accessTransformers", "loadOrder", "status");

    private final List<String> passed = new ArrayList<>();

    private OpsProof() {
    }

    /**
     * Runs the proof and terminates with a non-zero exit code on failure.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        new OpsProof().run();
    }

    private void run() {
        Path work;
        try {
            work = Files.createTempDirectory("weaver-ops-proof-");
        } catch (IOException failure) {
            System.out.println("OPS PROOF CHECK ok=false setup reason=" + describe(failure));
            System.out.println("OPS PROOF RESULT ok=false checks=0 reason=" + describe(failure));
            System.exit(1);
            return;
        }

        try {
            Fixtures fixtures = createFixtures(work.resolve("mods"));
            check("classspace-mode", OpsProof::checkClassSpaceMode);
            check("mods-warn-report", () -> checkWarnReport(fixtures.all()));
            check("mods-fatal-policy", () -> checkFatalPolicy(fixtures.duplicateOnly()));
            check("agent-fatal-policy", () -> checkAgentFatal(fixtures.duplicateOnly(), work));
            check("agent-mod-list", () -> checkAgentModList(fixtures.all(), work));
            check("cli-text", () -> checkCliText(fixtures.all(), work));
            check("cli-json", () -> checkCliJson(fixtures.all(), work));
            check("cli-fatal", () -> checkCliFatal(fixtures.duplicateOnly(), work));
            check("version-matrix", OpsProof::checkVersionMatrix);
            check("update-contract", OpsProof::checkUpdateContract);
            check("mod-watch", () -> checkModWatch(work));
            check("data-pack-precedence", () -> checkDataPackPrecedence(work));
            check("data-registry-reload", () -> checkDataRegistryReload());
            cleanup(work);
            System.out.println("OPS PROOF RESULT ok=true checks=" + passed.size());
        } catch (Throwable failure) {
            cleanup(work);
            System.out.println("OPS PROOF RESULT ok=false checks=" + passed.size()
                    + " reason=" + describe(failure));
            if (!(failure instanceof ProofFailure)) {
                failure.printStackTrace(System.out);
            }
            System.exit(1);
        }
    }

    private void check(String name, CheckedRunnable body) {
        try {
            body.run();
        } catch (Throwable failure) {
            System.out.println("OPS PROOF CHECK ok=false " + name + " reason=" + describe(failure));
            throw new ProofFailure(name, failure);
        }
        passed.add(name);
        System.out.println("OPS PROOF CHECK ok=true " + name);
    }

    private static void checkClassSpaceMode() {
        requireEquals(ClassSpace.Mode.SHARED, ClassSpace.selectMode(true, false),
                "selectMode(requested=true, gameVisible=false)");
        requireEquals(ClassSpace.Mode.SHARED, ClassSpace.selectMode(true, true),
                "selectMode(requested=true, gameVisible=true)");
        requireEquals(ClassSpace.Mode.SYSTEM, ClassSpace.selectMode(false, true),
                "selectMode(requested=false, gameVisible=true)");
        requireEquals(ClassSpace.Mode.SYSTEM, ClassSpace.selectMode(false, false),
                "selectMode(requested=false, gameVisible=false)");
        requireEquals(List.of(ClassSpace.Mode.SYSTEM, ClassSpace.Mode.SHARED),
                Arrays.asList(ClassSpace.Mode.values()), "ClassSpace.Mode values");
        for (boolean gameVisible : new boolean[]{true, false}) {
            require(ClassSpace.selectMode(false, gameVisible) != ClassSpace.Mode.SHARED,
                    "shared mode selected implicitly with gameVisible=" + gameVisible);
        }
    }

    private static void checkWarnReport(Path mods) throws Exception {
        try (ModLoader loader = ModLoader.discover(mods, "client", ignored -> {
        }, ModPolicies.warn())) {
            ModList report = loader.report();
            requireEquals("client", report.side(), "report side");
            requireEquals(9, report.entries().size(), "entry count");
            requireEquals(7, report.accepted().size(), "accepted count");
            requireEquals(2, report.rejected().size(), "rejected count");

            Map<String, ModListEntry> byId = new LinkedHashMap<>();
            for (ModListEntry entry : report.entries()) {
                byId.put(entry.id(), entry);
            }
            requireEquals(9, byId.size(), "distinct ids");

            requireEquals(ModStatus.LOADED, byId.get("base").status(), "base status");
            requireEquals(ModStatus.LOADED, byId.get("dependent").status(), "dependent status");
            requireEquals(ModStatus.LOADED, byId.get("cycle-a").status(), "cycle-a status");
            requireEquals(ModStatus.LOADED, byId.get("cycle-b").status(), "cycle-b status");
            requireEquals(ModStatus.LOADED, byId.get("dup-a").status(), "dup-a status");
            requireEquals(ModStatus.LOADED, byId.get("dup-b").status(), "dup-b status");
            requireEquals(ModStatus.LOADED, byId.get("shadow").status(), "shadow status");
            requireEquals(ModStatus.REJECTED_DEPENDENCY, byId.get("missing-dep").status(),
                    "missing-dep status");
            requireEquals(ModStatus.REJECTED_VERSION, byId.get("version-mismatch").status(),
                    "version-mismatch status");

            requireEquals(-1, byId.get("missing-dep").loadOrder(), "missing-dep load order");
            requireEquals(-1, byId.get("version-mismatch").loadOrder(),
                    "version-mismatch load order");
            require(byId.get("missing-dep").statusDetail().contains("dependency=absent"),
                    "missing-dep detail: " + byId.get("missing-dep").statusDetail());
            require(byId.get("version-mismatch").statusDetail().contains("actual=1.0.0"),
                    "version-mismatch detail: " + byId.get("version-mismatch").statusDetail());

            List<String> loadOrder = report.loadOrdered().stream()
                    .map(ModListEntry::id)
                    .toList();
            requireEquals(List.of("base", "dup-a", "dup-b", "shadow", "dependent",
                    "cycle-a", "cycle-b"), loadOrder, "load order");
            require(byId.get("dependent").loadOrder() > byId.get("base").loadOrder(),
                    "dependent must load after base");

            requireEquals(5, report.diagnostics().size(), "diagnostic count");
            ModDiagnostic missing = onlyDiagnostic(report, ModDiagnostic.Type.MISSING_DEPENDENCY);
            requireEquals(ModDiagnostic.Severity.WARN, missing.severity(), "missing severity");
            requireEquals("missing-dep", missing.subject(), "missing subject");
            require(missing.message().contains("dependency=absent"),
                    "missing message: " + missing.message());

            ModDiagnostic mismatch = onlyDiagnostic(report, ModDiagnostic.Type.VERSION_MISMATCH);
            requireEquals(ModDiagnostic.Severity.WARN, mismatch.severity(), "mismatch severity");
            requireEquals("version-mismatch", mismatch.subject(), "mismatch subject");
            require(mismatch.message().contains("required=>=2.0.0"),
                    "mismatch message: " + mismatch.message());
            require(mismatch.message().contains("actual=1.0.0"),
                    "mismatch message: " + mismatch.message());

            ModDiagnostic cycle = onlyDiagnostic(report, ModDiagnostic.Type.CYCLE);
            requireEquals(ModDiagnostic.Severity.WARN, cycle.severity(), "cycle severity");
            requireEquals("cycle-a,cycle-b", cycle.subject(), "cycle subject");
            require(cycle.message().contains("cycle ids="), "cycle message: " + cycle.message());

            ModDiagnostic duplicate = onlyDiagnostic(report,
                    ModDiagnostic.Type.DUPLICATE_CLASS);
            requireEquals(ModDiagnostic.Severity.WARN, duplicate.severity(),
                    "duplicate severity");
            requireEquals("com/example/shared/Shared.class", duplicate.subject(),
                    "duplicate subject");
            require(duplicate.message().contains("mods=dup-a,dup-b"),
                    "duplicate message: " + duplicate.message());

            ModDiagnostic shadow = onlyDiagnostic(report, ModDiagnostic.Type.API_SHADOW);
            requireEquals(ModDiagnostic.Severity.WARN, shadow.severity(), "shadow severity");
            requireEquals("net/weaver/api/Shadow.class", shadow.subject(), "shadow subject");
            requireEquals("mod=shadow", shadow.message(), "shadow message");

            requireEquals("mods total=9 loaded=7 rejected=2 diagnostics=5 cycles=1",
                    report.summaryLine(), "summary line");
        }
    }

    private static void checkFatalPolicy(Path mods) throws Exception {
        try (ModLoader loader = ModLoader.discover(mods, "client", ignored -> {
        }, ModPolicies.fatal())) {
            throw new AssertionError("expected ModPolicyException from fatal duplicate class policy");
        } catch (ModPolicyException failure) {
            requireEquals(1, failure.diagnostics().size(), "fatal diagnostic count");
            ModDiagnostic diagnostic = failure.diagnostics().get(0);
            requireEquals(ModDiagnostic.Type.DUPLICATE_CLASS, diagnostic.type(), "fatal type");
            requireEquals(ModDiagnostic.Severity.FATAL, diagnostic.severity(), "fatal severity");
            requireEquals("com/example/shared/Shared.class", diagnostic.subject(),
                    "fatal subject");
        }
    }

    private static void checkAgentFatal(Path mods, Path work) throws Exception {
        String agent = System.getProperty("weaver.ops.agentJar", "").trim();
        require(!agent.isBlank() && Files.isRegularFile(Path.of(agent)),
                "agent jar not configured; set -Dweaver.ops.agentJar=<path>");
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Dweaver.policy.duplicateClasses=fatal");
        command.add("-javaagent:" + agent + "=side=client;modsDir=" + mods);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("net.weaver.core.modops.ModListCommand");
        command.add("--mods");
        command.add(mods.toString());
        command.add("--side");
        command.add("client");
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exit = process.waitFor();
        require(exit != 0, "agent fatal policy should abort the JVM, exit=" + exit);
        require(output.contains("MOD POLICY FATAL"),
                "missing MOD POLICY FATAL in agent output: " + output);
        require(!output.contains("AGENT READY"),
                "agent booted despite fatal policy: " + output);
    }

    private static void checkAgentModList(Path mods, Path work) throws Exception {
        String agent = System.getProperty("weaver.ops.agentJar", "").trim();
        require(!agent.isBlank() && Files.isRegularFile(Path.of(agent)),
                "agent jar not configured; set -Dweaver.ops.agentJar=<path>");
        Path report = work.resolve("agent-mod-list.json");
        Files.deleteIfExists(report);
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Dweaver.policy=warn");
        command.add("-javaagent:" + agent + "=side=client;modsDir=" + mods
                + ";modListFile=" + report + ";modListFormat=json");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("net.weaver.core.modops.ModListCommand");
        command.add("--help");
        Process process = new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int exit = process.waitFor();
        require(Files.isRegularFile(report),
                "agent mod list not written, exit=" + exit + " output=" + output);
        String json = Files.readString(report, StandardCharsets.UTF_8);
        require(json.contains("\"schemaVersion\": 1"),
                "agent report missing schemaVersion: " + json);
        require(json.contains("\"mods\":"), "agent report missing mods: " + json);
        require(json.contains("\"status\": \"LOADED\""),
                "agent report missing LOADED status: " + json);
        require(json.contains("\"diagnostics\":"),
                "agent report missing diagnostics: " + json);
        require(output.contains("MOD LIST path="),
                "missing MOD LIST log in agent output: " + output);
    }

    private static void checkCliText(Path mods, Path work) throws Exception {
        CommandResult result = runModList(work, "--mods", mods.toString(), "--side", "client");
        requireEquals(0, result.exit(), "cli text exit");
        require(result.stdout().contains("weaver mod list schema=1"),
                "missing text header: " + result.stdout());
        require(result.stdout().contains("mod id=base "), "missing base line");
        require(result.stdout().contains("status=LOADED"), "missing LOADED status");
        require(result.stdout().contains("mod id=missing-dep "), "missing rejected mod line");
        require(result.stdout().contains("status=REJECTED_DEPENDENCY"),
                "missing rejected status");
        require(result.stdout().contains("diagnostic severity=WARN type=CYCLE"),
                "missing cycle diagnostic");
        require(result.stdout().contains(
                        "summary mods total=9 loaded=7 rejected=2 diagnostics=5 cycles=1"),
                "missing summary line");
        require(result.stderr().contains("[weaver] MOD READ"),
                "loader log missing from stderr");
    }

    private static void checkCliJson(Path mods, Path work) throws Exception {
        CommandResult result = runModList(work, "--mods", mods.toString(),
                "--side", "client", "--json");
        requireEquals(0, result.exit(), "cli json exit");
        JsonObject root = JsonParser.parseString(result.stdout()).getAsJsonObject();
        require(root.has("schemaVersion"), "missing schemaVersion");
        requireEquals(ModList.SCHEMA_VERSION, root.get("schemaVersion").getAsInt(),
                "schemaVersion");
        require(root.has("loader") && root.get("loader").isJsonObject(), "missing loader");
        JsonObject loader = root.getAsJsonObject("loader");
        require(loader.has("name"), "missing loader.name");
        require(loader.has("version"), "missing loader.version");
        requireEquals("weaver", loader.get("name").getAsString(), "loader name");
        requireEquals("client", root.get("side").getAsString(), "json side");
        require(root.has("summary") && root.get("summary").isJsonObject(), "missing summary");
        JsonObject summary = root.getAsJsonObject("summary");
        requireEquals(9, summary.get("total").getAsInt(), "summary total");
        requireEquals(7, summary.get("loaded").getAsInt(), "summary loaded");
        requireEquals(2, summary.get("rejected").getAsInt(), "summary rejected");
        requireEquals(5, summary.get("diagnostics").getAsInt(), "summary diagnostics");
        requireEquals(1, summary.get("cycles").getAsInt(), "summary cycles");

        require(root.has("mods") && root.get("mods").isJsonArray(), "missing mods");
        JsonArray modsArray = root.getAsJsonArray("mods");
        requireEquals(9, modsArray.size(), "json mod count");
        for (JsonElement element : modsArray) {
            require(element.isJsonObject(), "mod entry is not an object");
            JsonObject mod = element.getAsJsonObject();
            for (String key : JSON_MOD_KEYS) {
                require(mod.has(key), "mod missing key " + key + " in " + mod);
            }
            require(mod.get("dependencies").isJsonObject(), "dependencies not an object");
            require(mod.get("mixins").isJsonArray(), "mixins not an array");
            require(mod.get("accessTransformers").isJsonArray(),
                    "accessTransformers not an array");
            require(mod.get("loadOrder").isJsonPrimitive(), "loadOrder not a primitive");
            require(mod.get("status").isJsonPrimitive(), "status not a primitive");
        }

        JsonObject base = modById(modsArray, "base");
        requireEquals(0, base.get("loadOrder").getAsInt(), "base json loadOrder");
        requireEquals("LOADED", base.get("status").getAsString(), "base json status");
        JsonObject dependent = modById(modsArray, "dependent");
        requireEquals(">=1.0.0",
                dependent.getAsJsonObject("dependencies").get("base").getAsString(),
                "dependent json dependency range");

        require(root.has("diagnostics") && root.get("diagnostics").isJsonArray(),
                "missing diagnostics");
        JsonArray diagnostics = root.getAsJsonArray("diagnostics");
        requireEquals(5, diagnostics.size(), "json diagnostic count");
        for (JsonElement element : diagnostics) {
            JsonObject diagnostic = element.getAsJsonObject();
            for (String key : List.of("severity", "type", "subject", "message")) {
                require(diagnostic.has(key), "diagnostic missing key " + key);
            }
        }
    }

    private static void checkCliFatal(Path duplicateOnly, Path work) throws Exception {
        CommandResult result = runModList(work, "--mods", duplicateOnly.toString(),
                "--side", "client", "--policy", "fatal", "--json");
        requireEquals(1, result.exit(), "cli fatal exit");
        require(result.stdout().isBlank(), "fatal stdout should be blank: " + result.stdout());
        require(result.stderr().contains("MOD CLASS CONFLICT"), "missing conflict log");
        require(result.stderr().contains("action=fatal"), "missing fatal action log");
        require(result.stderr().contains("MOD POLICY FAILED"), "missing policy failure log");
    }

    private static void checkVersionMatrix() {
        requireEquals("2.1.0", WeaverApi.VERSION, "WeaverApi.VERSION");
        requireEquals("2.2", WeaverApi.FEATURE_LEVEL, "WeaverApi.FEATURE_LEVEL");
        requireEquals("26.3", WeaverApi.GAME_VERSION, "WeaverApi.GAME_VERSION");
        requireEquals("26.3", WeaverApi.HOOK_TARGET, "WeaverApi.HOOK_TARGET");
        require(WeaverApi.isCompatibleApi("2.1.0"), "isCompatibleApi(2.1.0)");
        require(WeaverApi.isCompatibleGame("26.3"), "isCompatibleGame(26.3)");
        VersionChecks.Result result = VersionChecks.check(null, "26.3");
        require(result.compatible(), "VersionChecks.check compatible: " + result.message());
        requireEquals(CompatibilityMatrix.Level.SUPPORTED, result.apiLevel(),
                "VersionChecks apiLevel");
        requireEquals(CompatibilityMatrix.Level.SUPPORTED, result.gameLevel(),
                "VersionChecks gameLevel");
        requireEquals(CompatibilityMatrix.Level.SUPPORTED,
                CompatibilityMatrix.currentLevel(), "CompatibilityMatrix.currentLevel");
    }

    private static void checkUpdateContract() {
        UpdateCheckRequest request = new UpdateCheckRequest(WeaverApi.VERSION,
                WeaverApi.GAME_VERSION, "client", List.of(new InstalledMod("base", "1.0.0")));
        UpdateChecks.reset();
        try {
            requireEquals(UpdateStatus.NOT_CONFIGURED, UpdateChecks.check(request).status(),
                    "default update status");

            AtomicReference<UpdateCheckRequest> seen = new AtomicReference<>();
            UpdateCheck fake = received -> {
                seen.set(received);
                return new UpdateCheckResult(UpdateStatus.UPDATE_AVAILABLE,
                        List.of(new ModUpdate("base", "1.0.0", "1.1.0",
                                "https://example.invalid/base-1.1.0.jar", "fake notes")),
                        "fake check");
            };
            UpdateChecks.install(fake);
            UpdateCheckResult result = UpdateChecks.check(request);
            requireEquals(UpdateStatus.UPDATE_AVAILABLE, result.status(), "installed status");
            requireEquals(1, result.updates().size(), "update count");
            requireEquals("fake check", result.message(), "update message");
            ModUpdate update = result.updates().get(0);
            requireEquals("base", update.id(), "update id");
            requireEquals("1.0.0", update.installedVersion(), "update installed version");
            requireEquals("1.1.0", update.availableVersion(), "update available version");
            require(seen.get() == request, "installed check did not receive the request");
        } finally {
            UpdateChecks.reset();
        }
        requireEquals(UpdateStatus.NOT_CONFIGURED, UpdateChecks.check(request).status(),
                "status after reset");
    }

    private static void checkModWatch(Path work) throws Exception
    {
        Path mods = Files.createDirectories(work.resolve("watch-mods"));
        List<String> logs = new ArrayList<>();
        try (ModWatchService watcher = ModWatchService.manual(logs::add))
        {
            require(watcher.start(mods, logs::add), "watch start");
            Files.writeString(mods.resolve("notes.txt"), "ignored");

            Path jar = mods.resolve("proof.jar");
            Files.writeString(jar, "one");
            List<ModChangeReport> added = watcher.scanOnce();

            requireEquals(1, added.size(), "watch added count");
            requireEquals(ModChangeReport.Kind.ADDED, added.get(0).kind(),
                    "watch added kind");
            require(watcher.scanOnce().isEmpty(), "watch scan must be idempotent");

            Files.writeString(jar, "changed-value");
            List<ModChangeReport> modified = watcher.scanOnce();

            requireEquals(1, modified.size(), "watch modified count");
            requireEquals(ModChangeReport.Kind.MODIFIED, modified.get(0).kind(),
                    "watch modified kind");

            Files.delete(jar);
            List<ModChangeReport> removed = watcher.scanOnce();

            requireEquals(1, removed.size(), "watch removed count");
            requireEquals(ModChangeReport.Kind.REMOVED, removed.get(0).kind(),
                    "watch removed kind");

            watcher.close();
            watcher.close();
            require(watcher.scanOnce().isEmpty(), "closed watcher must not scan");
        }
        require(logs.stream().anyMatch(line -> line.contains("MODS CHANGED")
                        && line.contains("action=restart-required")),
                "missing restart-required log: " + logs);
    }

    private static void checkDataPackPrecedence(Path work) throws Exception
    {
        DataRegistryManager.reset();
        try
        {
            Path low = Files.createDirectories(work.resolve("packs/low"));
            Path high = Files.createDirectories(work.resolve("packs/high"));
            writePackMetadata(low, 15);
            writePackMetadata(high, 20);
            writePackEntry(low, "opsproof", "example", "gems", "ruby",
                    "{\"color\":\"blue\"}");
            writePackEntry(low, "opsproof", "example", "gems", "sapphire",
                    "{\"color\":\"blue\"}");
            writePackEntry(high, "opsproof", "example", "gems", "ruby",
                    "{\"color\":\"red\"}");
            writePackTag(low, "opsproof", "example", "gems", "shiny",
                    "{\"values\":[\"example:ruby\"]}");
            writePackTag(high, "opsproof", "example", "gems", "shiny",
                    "{\"values\":[\"example:sapphire\"],\"replace\":true}");

            DataPackRepository.Report report = DataPackRepository.load(List.of(low, high));

            requireEquals(2, report.snapshots().size(), "pack count");
            require(report.problems().isEmpty(), "pack problems: " + report.problems());
            requireEquals(0, report.snapshots().get(0).priority(), "low priority");
            requireEquals(1, report.snapshots().get(1).priority(), "high priority");
            requireEquals(15, report.packs().get(0).packFormat(), "low format");
            requireEquals(20, report.packs().get(1).packFormat(), "high format");

            DataRegistry<String> registry = DataRegistryManager.create("example:gems",
                    String.class, (entryId, data) -> String.valueOf(data.get("color")));
            DataRegistryManager.loadAll(report.snapshots());

            requireEquals("red", registry.get("example:ruby"), "pack precedence");
            TagView<String> view = registry.tag(
                    TagKey.of(registry.registryKey(), "opsproof:shiny"));
            requireEquals(1, view.size(), "replacing tag size");
            require(view.contains("blue"), "replacing tag lost the high value");
            require(!view.contains("red"), "replacing tag kept the low value");
        }
        finally
        {
            DataRegistryManager.reset();
        }
    }

    private static void checkDataRegistryReload()
    {
        DataRegistryManager.reset();
        try
        {
            DataRegistry<String> frozen = DataRegistryManager.create("opsproof:frozen",
                    String.class, (entryId, data) -> String.valueOf(data.get("color")));
            DataRegistry<String> open = DataRegistryManager.create("opsproof:open",
                    String.class, (entryId, data) -> String.valueOf(data.get("color")));
            frozen.freeze();

            Map<String, Object> red = new LinkedHashMap<>();
            red.put("color", "red");
            Map<String, Object> blue = new LinkedHashMap<>();
            blue.put("color", "blue");
            DataPackSnapshot snapshot = DataPackSnapshot.builder("reload", 10)
                    .entry("opsproof:frozen", "ruby", red)
                    .entry("opsproof:open", "sapphire", blue)
                    .build();

            DataRegistryManager.ReloadReport report =
                    DataRegistryManager.reloadAll(List.of(snapshot));

            requireEquals(1, report.frozenCount(), "frozen registry count");
            require(report.frozenIds().contains(frozen.id()), "frozen registry id");
            require(report.openIds().contains(open.id()), "open registry id");
            require(!report.isComplete(), "reload must report refusal");
            require(report.problems().stream().anyMatch(problem ->
                            problem.contains("frozen") && problem.contains(frozen.id())),
                    "missing frozen problem: " + report.problems());
            requireEquals(0, frozen.size(), "frozen registry was mutated");
            requireEquals(1, open.size(), "open registry size");
            requireEquals("blue", open.get("opsproof:sapphire"), "open registry value");
        }
        finally
        {
            DataRegistryManager.reset();
        }
    }

    private static void writePackMetadata(Path root, int format) throws IOException
    {
        Files.writeString(root.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + format
                        + ",\"description\":\"ops proof pack\"}}",
                StandardCharsets.UTF_8);
    }

    private static void writePackEntry(Path root, String packNamespace,
                                       String registryNamespace, String registryPath,
                                       String entryPath, String json) throws IOException
    {
        writePackFile(root, "data/" + packNamespace + "/weaver/registry/"
                + registryNamespace + "/" + registryPath + "/" + entryPath + ".json", json);
    }

    private static void writePackTag(Path root, String packNamespace,
                                     String registryNamespace, String registryPath,
                                     String tagPath, String json) throws IOException
    {
        writePackFile(root, "data/" + packNamespace + "/weaver/tags/"
                + registryNamespace + "/" + registryPath + "/" + tagPath + ".json", json);
    }

    private static void writePackFile(Path root, String relative, String json)
            throws IOException
    {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static Fixtures createFixtures(Path modsRoot) throws IOException {
        Path all = Files.createDirectories(modsRoot.resolve("all"));
        Path duplicateOnly = Files.createDirectories(modsRoot.resolve("duplicate-only"));

        writeMod(all, "a-base.jar", "base", "1.0.0", Map.of(),
                List.of("com/example/base/Base.class"));
        writeMod(all, "b-dependent.jar", "dependent", "1.0.0",
                Map.of("base", ">=1.0.0"), List.of("com/example/dependent/Dependent.class"));
        writeMod(all, "c-missing.jar", "missing-dep", "1.0.0",
                Map.of("absent", "*"), List.of("com/example/missing/Missing.class"));
        writeMod(all, "d-mismatch.jar", "version-mismatch", "1.0.0",
                Map.of("base", ">=2.0.0"), List.of("com/example/mismatch/Mismatch.class"));
        writeMod(all, "e-cycle-a.jar", "cycle-a", "1.0.0",
                Map.of("cycle-b", "*"), List.of("com/example/cyclea/CycleA.class"));
        writeMod(all, "f-cycle-b.jar", "cycle-b", "1.0.0",
                Map.of("cycle-a", "*"), List.of("com/example/cycleb/CycleB.class"));
        writeMod(all, "g-dup-a.jar", "dup-a", "1.0.0", Map.of(),
                List.of("com/example/shared/Shared.class"));
        writeMod(all, "h-dup-b.jar", "dup-b", "1.0.0", Map.of(),
                List.of("com/example/shared/Shared.class"));
        writeMod(all, "i-shadow.jar", "shadow", "1.0.0", Map.of(),
                List.of("net/weaver/api/Shadow.class"));

        writeMod(duplicateOnly, "dup-a.jar", "dup-a", "1.0.0", Map.of(),
                List.of("com/example/shared/Shared.class"));
        writeMod(duplicateOnly, "dup-b.jar", "dup-b", "1.0.0", Map.of(),
                List.of("com/example/shared/Shared.class"));

        return new Fixtures(all, duplicateOnly);
    }

    private static void writeMod(Path dir, String fileName, String id, String version,
                                 Map<String, String> dependencies, List<String> classes)
            throws IOException {
        JsonObject descriptor = new JsonObject();
        descriptor.addProperty("schemaVersion", 2);
        descriptor.addProperty("id", id);
        descriptor.addProperty("version", version);
        descriptor.addProperty("name", id);
        descriptor.addProperty("mainClass",
                "com.example." + id.replace('-', '_') + ".Main");
        descriptor.addProperty("side", "both");
        descriptor.addProperty("minecraft", "26.3");
        JsonObject dependencyObject = new JsonObject();
        for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
            dependencyObject.addProperty(dependency.getKey(), dependency.getValue());
        }
        descriptor.add("dependencies", dependencyObject);

        try (OutputStream stream = Files.newOutputStream(dir.resolve(fileName));
             JarOutputStream jar = new JarOutputStream(stream)) {
            jar.putNextEntry(new JarEntry("weaver.mod.json"));
            jar.write(descriptor.toString().getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            for (String entry : classes) {
                jar.putNextEntry(new JarEntry(entry));
                jar.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
                jar.closeEntry();
            }
        }
    }

    private static CommandResult runModList(Path workingDirectory, String... args)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("net.weaver.core.modops.ModListCommand");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(false)
                .start();
        byte[] stdout = process.getInputStream().readAllBytes();
        byte[] stderr = process.getErrorStream().readAllBytes();
        int exit = process.waitFor();
        return new CommandResult(exit, new String(stdout, StandardCharsets.UTF_8),
                new String(stderr, StandardCharsets.UTF_8));
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        return Path.of(System.getProperty("java.home"), "bin",
                windows ? "java.exe" : "java").toString();
    }

    private static JsonObject modById(JsonArray mods, String id) {
        for (JsonElement element : mods) {
            JsonObject mod = element.getAsJsonObject();
            if (id.equals(mod.get("id").getAsString())) {
                return mod;
            }
        }
        throw new AssertionError("missing mod id=" + id + " in " + mods);
    }

    private static ModDiagnostic onlyDiagnostic(ModList report, ModDiagnostic.Type type) {
        List<ModDiagnostic> matches = report.diagnostics().stream()
                .filter(diagnostic -> diagnostic.type() == type)
                .toList();
        requireEquals(1, matches.size(), "diagnostic count for " + type);
        return matches.get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void requireEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return failure.getClass().getSimpleName() + ": "
                + message.replace('\r', ' ').replace('\n', ' ');
    }

    private static void cleanup(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            System.out.println("OPS PROOF CLEANUP ok=false path=" + root
                    + " reason=" + describe(failure));
        }
    }

    private record Fixtures(Path all, Path duplicateOnly) {
    }

    private record CommandResult(int exit, String stdout, String stderr) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {

        void run() throws Exception;
    }

    private static final class ProofFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private ProofFailure(String check, Throwable cause) {
            super("check failed: " + check + " reason=" + describe(cause), cause);
        }
    }
}
