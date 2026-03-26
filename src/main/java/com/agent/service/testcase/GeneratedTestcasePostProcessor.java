package com.agent.service.testcase;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 对LLM生成的测试用例做最小可交付收口。
 */
class GeneratedTestcasePostProcessor {

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\bTODO\\b");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(auth_token_placeholder|project_id_placeholder|access_key_placeholder|secret_key_placeholder|placeholder)");
    private static final Pattern FABRICATED_RESOURCE_PATTERN = Pattern.compile(
            "(?i)(lite-\\d+|server-\\d+|instance-\\d+|disk-\\d+|volume-\\d+|demo[-_].+|test[-_].+|system)");
    private static final Map<String, ConfigBinding> CONFIG_BINDINGS = Map.of(
            "AUTHTOKEN", new ConfigBinding("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token"),
            "ACCESSTOKEN", new ConfigBinding("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token"),
            "TOKEN", new ConfigBinding("HUAWEICLOUD_AUTH_TOKEN", "hwcloud.auth.token"),
            "PROJECTID", new ConfigBinding("HUAWEICLOUD_PROJECT_ID", "hwcloud.project.id"),
            "BASEURL", new ConfigBinding("HUAWEICLOUD_BASE_URL", "hwcloud.base.url"),
            "DEVSERVERID", new ConfigBinding("HUAWEICLOUD_DEV_SERVER_ID", "hwcloud.dev-server.id"),
            "SERVERID", new ConfigBinding("HUAWEICLOUD_SERVER_ID", "hwcloud.server.id"),
            "INSTANCEID", new ConfigBinding("HUAWEICLOUD_INSTANCE_ID", "hwcloud.instance.id"),
            "VOLUMEID", new ConfigBinding("HUAWEICLOUD_VOLUME_ID", "hwcloud.volume.id"),
            "DISKID", new ConfigBinding("HUAWEICLOUD_DISK_ID", "hwcloud.disk.id"));
    private static final List<ConfigBinding> DISTINCT_CONFIG_BINDINGS = CONFIG_BINDINGS.values().stream()
            .distinct()
            .toList();

    private final JavaParser javaParser = new JavaParser();

    String process(String javaCode) {
        if (javaCode == null || javaCode.trim().isEmpty()) {
            throw new IllegalStateException("Generated testcase code must not be blank");
        }

        ParseResult<CompilationUnit> parseResult = javaParser.parse(javaCode);
        CompilationUnit compilationUnit = parseResult.getResult()
                .orElseThrow(() -> new IllegalStateException("Generated testcase code is not valid Java syntax"));

        ClassOrInterfaceDeclaration testClass = requireSinglePublicClass(compilationUnit);
        ensureJUnit5TestClass(compilationUnit, testClass);

        boolean helperRequired = rewriteRequiredConfigFields(testClass);
        helperRequired = rewriteDirectConfigLookups(testClass) || helperRequired;
        if (helperRequired) {
            ensureAssumptionsImport(compilationUnit);
            ensureRequiredConfigMethod(testClass);
        }
        ensureNoDirectConfigLookup(testClass);

        String normalized = compilationUnit.toString();
        if (TODO_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains TODO placeholder");
        }
        if (PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains unresolved placeholder");
        }

        ParseResult<CompilationUnit> reparsed = javaParser.parse(normalized);
        if (reparsed.getResult().isEmpty()) {
            throw new IllegalStateException("Generated testcase code failed validation after normalization");
        }
        return normalized.trim();
    }

    private ClassOrInterfaceDeclaration requireSinglePublicClass(CompilationUnit compilationUnit) {
        NodeList<TypeDeclaration<?>> types = compilationUnit.getTypes();
        if (types.size() != 1 || !(types.get(0) instanceof ClassOrInterfaceDeclaration testClass)) {
            throw new IllegalStateException("Generated testcase code must contain exactly one top-level class");
        }
        if (testClass.isInterface() || !testClass.isPublic()) {
            throw new IllegalStateException("Generated testcase code must declare one public class");
        }
        return testClass;
    }

    private void ensureJUnit5TestClass(CompilationUnit compilationUnit, ClassOrInterfaceDeclaration testClass) {
        boolean importedJUnit5 = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch(name -> name.startsWith("org.junit.jupiter"));
        boolean hasTestMethod = testClass.getMethods().stream().anyMatch(this::hasJUnit5TestAnnotation);
        if (!importedJUnit5 || !hasTestMethod) {
            throw new IllegalStateException("Generated testcase code must be a JUnit5 test class");
        }
    }

    private boolean hasJUnit5TestAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .anyMatch(name -> List.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory", "TestTemplate")
                        .contains(name));
    }

    private boolean rewriteRequiredConfigFields(ClassOrInterfaceDeclaration testClass) {
        boolean helperRequired = false;
        Map<String, ConfigBinding> boundVariables = new LinkedHashMap<>();
        for (VariableDeclarator variable : testClass.findAll(VariableDeclarator.class)) {
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isPresent()) {
                ConfigBinding bindingFromLookup = resolveBinding(initializer.get());
                if (bindingFromLookup != null) {
                    variable.setInitializer(requiredConfigCall(bindingFromLookup.envKey(), bindingFromLookup.propertyKey()));
                    helperRequired = true;
                    continue;
                }
            }
            ConfigBinding binding = resolveBinding(variable.getNameAsString());
            if (binding == null) {
                continue;
            }
            if (!"String".equals(variable.getType().asString())) {
                throw new IllegalStateException(
                        "Generated testcase code must declare " + variable.getNameAsString() + " as String");
            }
            boundVariables.put(variable.getNameAsString(), binding);

            if (initializer.isPresent() && !isRequiredConfigCall(initializer.get(), binding)) {
                if (initializer.get() instanceof StringLiteralExpr stringLiteralExpr
                        && looksLikeFabricatedResourceLiteral(stringLiteralExpr.asString())) {
                    throw new IllegalStateException(
                            "Generated testcase code contains hard-coded resource literal for " + variable.getNameAsString());
                }
                variable.setInitializer(requiredConfigCall(binding.envKey(), binding.propertyKey()));
                helperRequired = true;
            }
        }

        Map<String, Boolean> assignedViaRequiredConfig = new LinkedHashMap<>();
        for (String variableName : boundVariables.keySet()) {
            assignedViaRequiredConfig.put(variableName, false);
        }
        for (AssignExpr assignExpr : testClass.findAll(AssignExpr.class)) {
            String variableName = extractAssignedVariableName(assignExpr);
            ConfigBinding binding = resolveBinding(variableName);
            if (binding == null) {
                continue;
            }
            if (!isRequiredConfigCall(assignExpr.getValue(), binding)) {
                assignExpr.setValue(requiredConfigCall(binding.envKey(), binding.propertyKey()));
            }
            assignedViaRequiredConfig.put(variableName, true);
            helperRequired = true;
        }

        for (VariableDeclarator variable : testClass.findAll(VariableDeclarator.class)) {
            ConfigBinding binding = resolveBinding(variable.getNameAsString());
            if (binding == null) {
                continue;
            }
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isPresent()) {
                if (isRequiredConfigCall(initializer.get(), binding)) {
                    helperRequired = true;
                    assignedViaRequiredConfig.put(variable.getNameAsString(), true);
                    continue;
                }
                if (initializer.get() instanceof StringLiteralExpr stringLiteralExpr
                        && looksLikeFabricatedResourceLiteral(stringLiteralExpr.asString())) {
                    throw new IllegalStateException(
                            "Generated testcase code contains hard-coded resource literal for " + variable.getNameAsString());
                }
                throw new IllegalStateException(
                        "Generated testcase code must load " + variable.getNameAsString() + " via requiredConfig");
            }
            if (Boolean.TRUE.equals(assignedViaRequiredConfig.get(variable.getNameAsString()))) {
                helperRequired = true;
                continue;
            }
            throw new IllegalStateException(
                    "Generated testcase code must initialize " + variable.getNameAsString() + " via requiredConfig");
        }
        return helperRequired;
    }

    private void ensureNoDirectConfigLookup(ClassOrInterfaceDeclaration testClass) {
        for (MethodCallExpr methodCallExpr : testClass.findAll(MethodCallExpr.class)) {
            if (isInsideRequiredConfigMethod(methodCallExpr)) {
                continue;
            }
            ConfigBinding binding = resolveBinding(methodCallExpr);
            if (binding != null) {
                throw new IllegalStateException(
                        "Generated testcase code must load " + binding.envKey() + " via requiredConfig");
            }
        }
    }

    private boolean rewriteDirectConfigLookups(ClassOrInterfaceDeclaration testClass) {
        boolean rewritten = false;
        for (MethodCallExpr methodCallExpr : testClass.findAll(MethodCallExpr.class)) {
            if (isInsideRequiredConfigMethod(methodCallExpr)) {
                continue;
            }
            ConfigBinding binding = resolveBinding(methodCallExpr);
            if (binding == null) {
                continue;
            }
            methodCallExpr.replace(requiredConfigCall(binding.envKey(), binding.propertyKey()));
            rewritten = true;
        }
        return rewritten;
    }

    private boolean isInsideRequiredConfigMethod(MethodCallExpr methodCallExpr) {
        return methodCallExpr.findAncestor(MethodDeclaration.class)
                .map(method -> "requiredConfig".equals(method.getNameAsString())
                        && method.getParameters().size() == 2)
                .orElse(false);
    }

    private ConfigBinding resolveBinding(String fieldName) {
        return CONFIG_BINDINGS.get(normalizeBindingKey(fieldName));
    }

    private String normalizeBindingKey(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        return fieldName.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private ConfigBinding resolveBinding(Expression expression) {
        if (!(expression instanceof MethodCallExpr methodCallExpr)) {
            return null;
        }
        if (!methodCallExpr.getScope().map(scope -> "System".equals(scope.toString())).orElse(false)
                || methodCallExpr.getArguments().size() != 1
                || !methodCallExpr.getArgument(0).isStringLiteralExpr()) {
            return null;
        }
        String argument = methodCallExpr.getArgument(0).asStringLiteralExpr().asString();
        return switch (methodCallExpr.getNameAsString()) {
            case "getenv" -> resolveBindingByEnvKey(argument);
            case "getProperty" -> resolveBindingByPropertyKey(argument);
            default -> null;
        };
    }

    private ConfigBinding resolveBindingByEnvKey(String envKey) {
        return DISTINCT_CONFIG_BINDINGS.stream()
                .filter(binding -> binding.envKey().equals(envKey))
                .findFirst()
                .orElse(null);
    }

    private ConfigBinding resolveBindingByPropertyKey(String propertyKey) {
        return DISTINCT_CONFIG_BINDINGS.stream()
                .filter(binding -> binding.propertyKey().equals(propertyKey))
                .findFirst()
                .orElse(null);
    }

    private boolean looksLikeFabricatedResourceLiteral(String value) {
        return FABRICATED_RESOURCE_PATTERN.matcher(value == null ? "" : value.trim()).find();
    }

    private MethodCallExpr requiredConfigCall(String envKey, String propertyKey) {
        return new MethodCallExpr("requiredConfig")
                .addArgument(new StringLiteralExpr(envKey))
                .addArgument(new StringLiteralExpr(propertyKey));
    }

    private boolean isRequiredConfigCall(Expression expr, ConfigBinding binding) {
        if (!(expr instanceof MethodCallExpr methodCallExpr)) {
            return false;
        }
        if (!"requiredConfig".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 2) {
            return false;
        }
        String envKey = methodCallExpr.getArgument(0).isStringLiteralExpr()
                ? methodCallExpr.getArgument(0).asStringLiteralExpr().asString()
                : "";
        String propertyKey = methodCallExpr.getArgument(1).isStringLiteralExpr()
                ? methodCallExpr.getArgument(1).asStringLiteralExpr().asString()
                : "";
        return binding.envKey().equals(envKey) && binding.propertyKey().equals(propertyKey);
    }

    private String extractAssignedVariableName(AssignExpr assignExpr) {
        if (assignExpr.getTarget() instanceof NameExpr nameExpr) {
            return nameExpr.getNameAsString();
        }
        if (assignExpr.getTarget() instanceof FieldAccessExpr fieldAccessExpr) {
            return fieldAccessExpr.getNameAsString();
        }
        return "";
    }

    private boolean looksLikePlaceholder(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("placeholder")
                || normalized.startsWith("your_")
                || normalized.startsWith("replace_with_")
                || normalized.contains("to_be_filled");
    }

    private void ensureAssumptionsImport(CompilationUnit compilationUnit) {
        boolean alreadyImported = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch("org.junit.jupiter.api.Assumptions"::equals);
        if (!alreadyImported) {
            compilationUnit.addImport("org.junit.jupiter.api.Assumptions");
        }
    }

    private void ensureRequiredConfigMethod(ClassOrInterfaceDeclaration testClass) {
        boolean exists = testClass.getMethodsByName("requiredConfig").stream()
                .anyMatch(method -> method.getParameters().size() == 2);
        if (exists) {
            return;
        }

        MethodDeclaration method = testClass.addMethod("requiredConfig", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        method.setType("String");
        method.addParameter("String", "envKey");
        method.addParameter("String", "propertyKey");
        method.createBody()
                .addStatement("String value = System.getenv(envKey);")
                .addStatement("if (value == null || value.isBlank()) { value = System.getProperty(propertyKey); }")
                .addStatement("Assumptions.assumeTrue(value != null && !value.isBlank(), "
                        + "\"Missing required config: env \" + envKey + \" or -D\" + propertyKey);")
                .addStatement("return value;");
    }

    private record ConfigBinding(String envKey, String propertyKey) {
    }
}
