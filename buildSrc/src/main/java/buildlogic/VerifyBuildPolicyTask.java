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
            if (!containsLiveLiteral(text, required.value())) {
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
            String path = toPolicyPath(file, rootDir);
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 0; i < lines.size(); i++) {
                String rawLine = lines.get(i);
                String line = rawLine.trim();
                if (line.startsWith("uses:") || line.startsWith("- uses:")) {
                    checkActionRef(file, rootDir, i + 1, rawLine, line, failures);
                    if (isSetupGradleRef(extractUsesRef(line))) {
                        checkSetupGradleWrapperValidation(file, rootDir, i, lines, failures);
                    }
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
                if (path.equals(".github/workflows/build.yml") && line.startsWith("paths-ignore:")) {
                    addPolicyFailure(
                            failures,
                            file,
                            rootDir,
                            i + 1,
                            "Build runs verifyBuildPolicy and must not ignore policy-scanned paths."
                    );
                }
                if (path.equals(".github/workflows/build.yml") && line.startsWith("branches-ignore:")) {
                    addPolicyFailure(
                            failures,
                            file,
                            rootDir,
                            i + 1,
                            "Build runs default-branch PR verification and must not ignore PR base branches."
                    );
                }
            }
            String fileText = Files.readString(file.toPath());
            checkCentralPortalWorkflow(path, fileText, failures);
            workflowText.append(fileText).append('\n');
        }

        String text = workflowText.toString();
        if (!containsLiveLiteral(text, SETUP_GRADLE_PINNED_REF)) {
            failures.add(".github/workflows:1: setup-gradle must use the pinned v6.1.0 commit SHA.");
        }
        if (!containsLiveGradleCommand(text, "publishToMavenCentral")) {
            failures.add(".github/workflows:1: Central Portal publish workflows must call publishToMavenCentral.");
        }
    }

    private static void checkCentralPortalWorkflow(
            String path,
            String text,
            List<String> failures
    ) {
        if ((path.equals(".github/workflows/build.yml") || path.equals(".github/workflows/release.yml"))
                && !containsLiveGradleCommand(text, "publishToMavenCentral")) {
            failures.add(path + ":1: Central Portal publish workflow must call publishToMavenCentral.");
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
        String usesRef = extractUsesRef(line);
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

    private static String extractUsesRef(String line) {
        return line.substring(line.indexOf("uses:") + "uses:".length()).trim().split(" ")[0];
    }

    private static boolean isSetupGradleRef(String usesRef) {
        return usesRef.startsWith("gradle/actions/setup-gradle@");
    }

    private static void checkSetupGradleWrapperValidation(
            File file,
            File rootDir,
            int usesLineIndex,
            List<String> lines,
            List<String> failures
    ) {
        int usesIndent = leadingSpaces(lines.get(usesLineIndex));
        boolean inWith = false;
        int withIndent = -1;
        for (int i = usesLineIndex + 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty() || isCommentLine(trimmed)) {
                continue;
            }
            int indent = leadingSpaces(rawLine);
            if (indent < usesIndent && trimmed.startsWith("- ")) {
                break;
            }
            if (!inWith && indent == usesIndent && trimmed.equals("with:")) {
                inWith = true;
                withIndent = indent;
                continue;
            }
            if (inWith && indent <= withIndent) {
                inWith = false;
            }
            if (inWith && trimmed.equals("validate-wrappers: true")) {
                return;
            }
        }
        addPolicyFailure(
                failures,
                file,
                rootDir,
                usesLineIndex + 1,
                "Each setup-gradle step must enable validate-wrappers: true."
        );
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

    private static boolean containsLiveLiteral(String text, String literal) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (isCommentLine(trimmed)) {
                continue;
            }
            if (line.contains(literal)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLiveGradleCommand(String text, String taskName) {
        String command = "./gradlew " + taskName;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (isCommentLine(trimmed)) {
                continue;
            }
            if (line.contains(command)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCommentLine(String trimmedLine) {
        return trimmedLine.startsWith("#")
                || trimmedLine.startsWith("//")
                || trimmedLine.startsWith("*")
                || trimmedLine.startsWith("<!--");
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
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
            new ForbiddenLiteral("tsApiChecks = " + "false", "TypeScript API checks are required."),
            new ForbiddenLiteral("ubuntu" + "-latest", "Runner images must be pinned to explicit OS versions."),
            new ForbiddenLiteral("macos" + "-latest", "Runner images must be pinned to explicit OS versions."),
            new ForbiddenLiteral("windows" + "-latest", "Runner images must be pinned to explicit OS versions.")
    );

    private static final List<RequiredLiteral> REQUIRED_LITERALS = List.of(
            new RequiredLiteral("gradle.properties", "kotlin.jvm.target.validation.mode=error", "JVM target drift must fail the build."),
            new RequiredLiteral("gradle/libs.versions.toml", "javaLangTarget = \"17\"", "Published bytecode target is Java 17."),
            new RequiredLiteral("gradle/libs.versions.toml", "androidMinSdk = \"21\"", "Android minSdk must stay on the modern floor."),
            new RequiredLiteral("build.gradle.kts", "useDokka = true", "Dokka publication jars are required."),
            new RequiredLiteral("fluxo-io-rad/build.gradle.kts", "tsApiChecks = true", "TypeScript API checks are required.")
    );
}
