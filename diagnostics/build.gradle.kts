plugins {
    application
}

application {
    mainClass.set("diag.Main")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "diag.Main")
    }
}
