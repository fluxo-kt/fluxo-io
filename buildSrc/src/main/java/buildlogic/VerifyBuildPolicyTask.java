package buildlogic;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class VerifyBuildPolicyTask extends DefaultTask {
    private final ConfigurableFileCollection policyFiles =
            getProject().getObjects().fileCollection();
    private final Property<String> rootDirectory =
            getProject().getObjects().property(String.class);

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public final ConfigurableFileCollection getPolicyFiles() {
        return policyFiles;
    }

    @Input
    public final Property<String> getRootDirectory() {
        return rootDirectory;
    }

    @TaskAction
    public final void verify() throws IOException {
        File rootDir = new File(rootDirectory.get());
        List<String> failures = new ArrayList<>();
        List<File> files = new ArrayList<>(policyFiles.getFiles());
        files.sort(Comparator.comparing(file -> toPolicyPath(file, rootDir)));

        checkForbiddenLiterals(files, rootDir, failures);
        checkRequiredLiterals(rootDir, failures);
        checkWorkflowPolicy(files, rootDir, failures);

        if (!failures.isEmpty()) {
            throw new GradleException("Build policy violations:\n" + String.join("\n", failures));
        }
    }

    private static void checkForbiddenLiterals(
            List<File> files,
            File rootDir,
            List<String> failures
    ) throws IOException {
        for (File file : files) {
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (ForbiddenLiteral literal : FORBIDDEN_LITERALS) {
                    if (line.contains(literal.value())) {
                        addPolicyFailure(
                                failures,
                                file,
                                rootDir,
                                i + 1,
                                literal.rationale() + " Forbidden literal: " + literal.value()
                        );
                    }
                }
                if (line.trim().startsWith("//") && line.contains("useDokka = true")) {
                    addPolicyFailure(
                            failures,
                            file,
                            rootDir,
                            i + 1,
                            "Dokka enablement must be live, not commented."
                    );
                }
            }
        }
    }

    private static void checkRequiredLiterals(File rootDir, List<String> failures) throws IOException {
        for (RequiredLiteral required : REQUIRED_LITERALS) {
            File file = new File(rootDir, required.relativePath());
            String text = Files.readString(file.toPath());
            if (!text.contains(required.value())) {
                failures.add(required.relativePath()
                        + ":1: "
                        + required.rationale()
                        + " Missing literal: "
                        + required.value());
            }
        }
    }

    private static void checkWorkflowPolicy(
            List<File> files,
            File rootDir,
            List<String> failures
    ) throws IOException {
        List<File> workflowFiles = new ArrayList<>();
        for (File file : files) {
            String path = toPolicyPath(file, rootDir);
            if (path.startsWith(".github/workflows/") || path.startsWith(".github/actions/")) {
                workflowFiles.add(file);
            }
        }

        StringBuilder workflowText = new StringBuilder();
        for (File file : workflowFiles) {
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                String rawLine = lines.get(i);
                String line = rawLine.trim();
                if (line.startsWith("uses:") || line.startsWith("- uses:")) {
                    checkActionRef(file, rootDir, i + 1, rawLine, line, failures);
                }
                if (line.startsWith("runs-on:")
                        && line.substring(line.indexOf("runs-on:") + "runs-on:".length()).contains("-latest")) {
                    addPolicyFailure(
                            failures,
                            file,
                            rootDir,
                            i + 1,
                            "Runner images must be pinned, not '*-latest'."
                    );
                }
            }
            workflowText.append(Files.readString(file.toPath())).append('\n');
        }

        String text = workflowText.toString();
        if (!text.contains(SETUP_GRADLE_PINNED_REF)) {
            failures.add(".github/workflows:1: setup-gradle must use the pinned v6.1.0 commit SHA.");
        }
        if (!text.contains("validate-wrappers: true")) {
            failures.add(".github/workflows:1: setup-gradle wrapper validation must be enabled.");
        }
        if (!text.contains("publishToMavenCentral")) {
            failures.add(".github/workflows:1: Central Portal publish workflows must call publishToMavenCentral.");
        }
    }

    private static void checkActionRef(
            File file,
            File rootDir,
            int lineNumber,
            String rawLine,
            String line,
            List<String> failures
    ) {
        String usesRef = line.substring(line.indexOf("uses:") + "uses:".length()).trim().split(" ")[0];
        String actionRef = "";
        int atIndex = usesRef.lastIndexOf('@');
        if (atIndex >= 0) {
            actionRef = usesRef.substring(atIndex + 1);
        }

        if (usesRef.startsWith("./")) {
            return;
        }
        if (usesRef.startsWith("docker://")) {
            if (!usesRef.contains("@sha256:")) {
                addPolicyFailure(
                        failures,
                        file,
                        rootDir,
                        lineNumber,
                        "Docker action refs must use immutable sha256 digests."
                );
            }
            return;
        }
        if (!isPolicySha(actionRef)) {
            addPolicyFailure(
                    failures,
                    file,
                    rootDir,
                    lineNumber,
                    "Remote action refs must be pinned to a 40-character commit SHA."
            );
        } else if (!rawLine.contains("# v")) {
            addPolicyFailure(
                    failures,
                    file,
                    rootDir,
                    lineNumber,
                    "Pinned action refs must keep a version comment for maintainability."
            );
        }
    }

    private static boolean isPolicySha(String value) {
        if (value.length() != 40) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static void addPolicyFailure(
            List<String> failures,
            File file,
            File rootDir,
            int lineNumber,
            String message
    ) {
        failures.add(toPolicyPath(file, rootDir) + ":" + lineNumber + ": " + message);
    }

    private static String toPolicyPath(File file, File rootDir) {
        return rootDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private record ForbiddenLiteral(String value, String rationale) {
    }

    private record RequiredLiteral(String relativePath, String value, String rationale) {
    }

    private static final String SETUP_GRADLE_PINNED_REF =
            "gradle/actions/setup-gradle@50e97c2cd7a37755bbfafc9c5b7cafaece252f6e # v6.1.0";

    private static final List<ForbiddenLiteral> FORBIDDEN_LITERALS = List.of(
            new ForbiddenLiteral("Sonatype" + "Host", "Central Portal publishing must not regress to OSSRH host APIs."),
            new ForbiddenLiteral("oss.sonatype" + ".org", "OSSRH endpoints are obsolete for this project."),
            new ForbiddenLiteral("s01.oss.sonatype" + ".org", "S01 endpoints are obsolete for this project."),
            new ForbiddenLiteral("sonatype" + "Host =", "Vanniktech Central Portal publishing must not use legacy host configuration."),
            new ForbiddenLiteral("kotlin-compiler-" + "embeddable", "Do not reintroduce the compiler classpath pin."),
            new ForbiddenLiteral("jitpack" + ".io", "Do not add JitPack to dependency resolution."),
            new ForbiddenLiteral("kotlin.native.binary." + "memoryModel=experimental", "The old Native memory model flag is dead."),
            new ForbiddenLiteral("kotlin.mpp.stability." + "nowarn=true", "Do not suppress KMP stability warnings."),
            new ForbiddenLiteral("kotlin.compiler.suppressExperimentalIC" + "OptimizationsWarning=true", "Do not suppress compiler optimization warnings."),
            new ForbiddenLiteral("kotlin.mpp.import.enableKgp" + "DependencyResolution=true", "Do not restore obsolete KGP dependency resolution flags."),
            new ForbiddenLiteral("kotlin.mpp.androidSourceSet" + "LayoutVersion=2", "Do not restore the old Android source-set layout flag."),
            new ForbiddenLiteral("org.gradle.configure" + "ondemand=true", "Configuration-on-demand is not compatible with this build policy."),
            new ForbiddenLiteral("tsApiChecks = " + "false", "TypeScript API checks are required.")
    );

    private static final List<RequiredLiteral> REQUIRED_LITERALS = List.of(
            new RequiredLiteral("gradle.properties", "kotlin.jvm.target.validation.mode=error", "JVM target drift must fail the build."),
            new RequiredLiteral("gradle/libs.versions.toml", "javaLangTarget = \"17\"", "Published bytecode target is Java 17."),
            new RequiredLiteral("gradle/libs.versions.toml", "androidMinSdk = \"21\"", "Android minSdk must stay on the modern floor."),
            new RequiredLiteral("build.gradle.kts", "useDokka = true", "Dokka publication jars are required."),
            new RequiredLiteral("fluxo-io-rad/build.gradle.kts", "tsApiChecks = true", "TypeScript API checks are required.")
    );
}
