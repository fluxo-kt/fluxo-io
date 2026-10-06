// Version alignment for every published fluxo-io artifact. The adapters call the core's internal
// API, so a core and an adapter from different releases can fail at run time. Each module also
// depends on this platform, so Gradle aligns them on its own; Maven users import it as a BOM.
plugins {
    `java-platform`
    alias(libs.plugins.vanniktech.mvn.publish)
}

dependencies {
    constraints {
        api(projects.fluxoIoRad)
        api(projects.fluxoIoRadOkio)
        api(projects.fluxoIoRadKotlinxIo)
    }
}

mavenPublishing {
    pom {
        description.set("fluxo-io BOM: keeps fluxo-io-rad and its adapters on one version.")
    }
}
