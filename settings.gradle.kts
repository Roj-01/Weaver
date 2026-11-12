plugins {
    // Provisions the JDK 26 toolchain automatically when none is installed,
    // so a fresh checkout builds without a manual JDK setup.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "weaver"

include(":api")
include(":api-game")
include(":nf")
include(":core")
include(":mixin")
include(":diagnostics")
include(":sync")
