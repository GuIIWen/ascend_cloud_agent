package com.agent.service.testcase;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
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
    private static final Map<String, String> JUNIT5_ANNOTATION_IMPORTS = Map.of(
            "Test", "org.junit.jupiter.api.Test",
            "BeforeAll", "org.junit.jupiter.api.BeforeAll",
            "BeforeEach", "org.junit.jupiter.api.BeforeEach",
            "AfterAll", "org.junit.jupiter.api.AfterAll",
            "AfterEach", "org.junit.jupiter.api.AfterEach",
            "ParameterizedTest", "org.junit.jupiter.params.ParameterizedTest",
            "RepeatedTest", "org.junit.jupiter.api.RepeatedTest",
            "TestFactory", "org.junit.jupiter.api.TestFactory",
            "TestTemplate", "org.junit.jupiter.api.TestTemplate");
    private static final List<String> JUNIT5_TEST_METHOD_ANNOTATIONS = List.of(
            "Test",
            "ParameterizedTest",
            "RepeatedTest",
            "TestFactory",
            "TestTemplate");

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\bTODO\\b");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "(?i)(auth_token_placeholder|project_id_placeholder|access_key_placeholder|secret_key_placeholder|placeholder|\\?{3,})");
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
        CompilationUnit compilationUnit = requireSuccessfulParse(parseResult, "Generated testcase code is not valid Java syntax");

        ClassOrInterfaceDeclaration testClass = requireSinglePublicClass(compilationUnit);
        ensureJUnit5TestClass(compilationUnit, testClass);

        boolean helperRequired = rewriteDirectConfigLookups(testClass);
        helperRequired = flattenRedundantRequiredConfigFallbacks(testClass) || helperRequired;
        FieldRewriteResult fieldRewriteResult = rewriteRequiredConfigFields(testClass);
        helperRequired = fieldRewriteResult.helperRequired() || helperRequired;
        if (!fieldRewriteResult.beforeAllBindings().isEmpty()) {
            ensureBeforeAllImport(compilationUnit);
            ensureBeforeAllConfigSetup(testClass, fieldRewriteResult.beforeAllBindings());
        }
        helperRequired = hasRequiredConfigMethod(testClass) || helperRequired;
        if (helperRequired) {
            ensureAssumptionsImport(compilationUnit);
            ensureRequiredConfigMethod(testClass);
        }
        ensureNoDirectConfigLookup(testClass);
        ensureNoRequiredConfigFieldInitializer(testClass);

        String normalized = compilationUnit.toString();
        if (TODO_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains TODO placeholder");
        }
        if (PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("Generated testcase code contains unresolved placeholder");
        }

        ParseResult<CompilationUnit> reparsed = javaParser.parse(normalized);
        requireSuccessfulParse(reparsed, "Generated testcase code failed validation after normalization");
        return normalized.trim();
    }

    private CompilationUnit requireSuccessfulParse(ParseResult<CompilationUnit> parseResult, String errorMessage) {
        if (parseResult == null || !parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }
        return parseResult.getResult()
                .orElseThrow(() -> new IllegalStateException(errorMessage));
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
        boolean hasTestMethod = testClass.getMethods().stream()
                .anyMatch(method -> hasJUnit5TestAnnotation(compilationUnit, method));
        if (!hasTestMethod) {
            throw new IllegalStateException("Generated testcase code must be a JUnit5 test class");
        }
        ensureJUnit5AnnotationImports(compilationUnit, testClass);
    }

    private boolean hasJUnit5TestAnnotation(CompilationUnit compilationUnit, MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(annotation -> isJUnit5AnnotationReference(
                        compilationUnit,
                        annotation.getNameAsString(),
                        JUNIT5_TEST_METHOD_ANNOTATIONS));
    }

    private FieldRewriteResult rewriteRequiredConfigFields(ClassOrInterfaceDeclaration testClass) {
        boolean helperRequired = false;
        Map<String, ConfigBinding> boundFields = new LinkedHashMap<>();
        Map<String, ConfigBinding> beforeAllBindings = new LinkedHashMap<>();
        for (FieldDeclaration fieldDeclaration : testClass.getFields()) {
            for (VariableDeclarator variable : fieldDeclaration.getVariables()) {
                Optional<Expression> initializer = variable.getInitializer();
                ConfigBinding binding = resolveFieldBinding(variable);
                if (binding == null) {
                    if (initializer.isPresent() && containsRequiredConfigCall(initializer.get())) {
                        throw new IllegalStateException(
                                "Generated testcase code must not call requiredConfig during field initialization for "
                                        + variable.getNameAsString());
                    }
                    continue;
                }
                validateBoundVariableType(variable);
                boundFields.put(variable.getNameAsString(), binding);
                if (initializer.isPresent()) {
                    ensureNoFabricatedResourceLiteral(variable.getNameAsString(), initializer.get());
                    normalizeFieldDeclarationForBeforeAll(fieldDeclaration);
                    variable.removeInitializer();
                    beforeAllBindings.put(variable.getNameAsString(), binding);
                    helperRequired = true;
                }
            }
        }

        Map<String, Boolean> assignedViaRequiredConfig = new LinkedHashMap<>();
        for (String variableName : boundFields.keySet()) {
            assignedViaRequiredConfig.put(variableName, false);
        }
        for (AssignExpr assignExpr : testClass.findAll(AssignExpr.class)) {
            String variableName = extractAssignedVariableName(assignExpr);
            ConfigBinding binding = boundFields.get(variableName);
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
            if (isFieldVariable(variable)) {
                ConfigBinding binding = boundFields.get(variable.getNameAsString());
                if (binding == null) {
                    continue;
                }
                validateBoundVariableType(variable);
                if (variable.getInitializer().isPresent()) {
                    throw new IllegalStateException(
                            "Generated testcase code must not call requiredConfig during field initialization for "
                                    + variable.getNameAsString());
                }
                if (!Boolean.TRUE.equals(assignedViaRequiredConfig.get(variable.getNameAsString()))) {
                    beforeAllBindings.putIfAbsent(variable.getNameAsString(), binding);
                    helperRequired = true;
                }
                continue;
            }

            ConfigBinding binding = resolveLocalBinding(variable);
            if (binding == null) {
                continue;
            }
            validateBoundVariableType(variable);
            Optional<Expression> initializer = variable.getInitializer();
            if (initializer.isPresent()) {
                ensureNoFabricatedResourceLiteral(variable.getNameAsString(), initializer.get());
                if (!isRequiredConfigCall(initializer.get(), binding)) {
                    variable.setInitializer(requiredConfigCall(binding.envKey(), binding.propertyKey()));
                }
                helperRequired = true;
                continue;
            }
            throw new IllegalStateException(
                    "Generated testcase code must initialize " + variable.getNameAsString() + " via requiredConfig");
        }
        return new FieldRewriteResult(helperRequired, beforeAllBindings);
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
        for (MethodCallExpr methodCallExpr : new ArrayList<>(testClass.findAll(MethodCallExpr.class))) {
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

    private boolean flattenRedundantRequiredConfigFallbacks(ClassOrInterfaceDeclaration testClass) {
        boolean rewritten = false;
        for (MethodCallExpr methodCallExpr : new ArrayList<>(testClass.findAll(MethodCallExpr.class))) {
            if (isInsideRequiredConfigMethod(methodCallExpr)) {
                continue;
            }
            ConfigBinding binding = resolveRedundantRequiredConfigFallbackBinding(methodCallExpr, true);
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
        expression = unwrapExpression(expression);
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

    private ConfigBinding resolveFieldBinding(VariableDeclarator variable) {
        ConfigBinding bindingByName = resolveBinding(variable.getNameAsString());
        ConfigBinding bindingByInitializer = variable.getInitializer()
                .map(this::resolveConfigExpressionBinding)
                .orElse(null);
        if (bindingByName != null && bindingByInitializer != null && !bindingByName.equals(bindingByInitializer)) {
            throw new IllegalStateException(
                    "Generated testcase code contains conflicting config binding for field " + variable.getNameAsString());
        }
        return bindingByInitializer != null ? bindingByInitializer : bindingByName;
    }

    private ConfigBinding resolveLocalBinding(VariableDeclarator variable) {
        ConfigBinding bindingByName = resolveBinding(variable.getNameAsString());
        ConfigBinding bindingByInitializer = variable.getInitializer()
                .map(this::resolveConfigExpressionBinding)
                .orElse(null);
        if (bindingByName != null && bindingByInitializer != null && !bindingByName.equals(bindingByInitializer)) {
            throw new IllegalStateException(
                    "Generated testcase code contains conflicting config binding for variable " + variable.getNameAsString());
        }
        return bindingByInitializer != null ? bindingByInitializer : bindingByName;
    }

    private ConfigBinding resolveConfigExpressionBinding(Expression expression) {
        expression = unwrapExpression(expression);
        ConfigBinding directBinding = resolveBinding(expression);
        if (directBinding != null) {
            return directBinding;
        }
        ConfigBinding requiredConfigBinding = resolveRequiredConfigBinding(expression);
        if (requiredConfigBinding != null) {
            return requiredConfigBinding;
        }
        return resolveRedundantRequiredConfigFallbackBinding(expression, false);
    }

    private ConfigBinding resolveRequiredConfigBinding(Expression expression) {
        expression = unwrapExpression(expression);
        if (!(expression instanceof MethodCallExpr methodCallExpr)) {
            return null;
        }
        if (!"requiredConfig".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 2) {
            return null;
        }
        if (!methodCallExpr.getArgument(0).isStringLiteralExpr() || !methodCallExpr.getArgument(1).isStringLiteralExpr()) {
            return null;
        }
        String envKey = methodCallExpr.getArgument(0).asStringLiteralExpr().asString();
        String propertyKey = methodCallExpr.getArgument(1).asStringLiteralExpr().asString();
        ConfigBinding bindingByEnv = resolveBindingByEnvKey(envKey);
        ConfigBinding bindingByProperty = resolveBindingByPropertyKey(propertyKey);
        if (bindingByEnv == null || !bindingByEnv.equals(bindingByProperty)) {
            return null;
        }
        return bindingByEnv;
    }

    private ConfigBinding resolveRedundantRequiredConfigFallbackBinding(Expression expression, boolean failOnMismatch) {
        expression = unwrapExpression(expression);
        if (!(expression instanceof MethodCallExpr methodCallExpr)) {
            return null;
        }
        if (!"orElse".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 1) {
            return null;
        }
        if (methodCallExpr.getScope().isEmpty()) {
            return null;
        }
        Expression scope = unwrapExpression(methodCallExpr.getScope().orElseThrow());
        if (!(scope instanceof MethodCallExpr optionalOfNullable)) {
            return null;
        }
        if (!"ofNullable".equals(optionalOfNullable.getNameAsString())
                || optionalOfNullable.getArguments().size() != 1
                || optionalOfNullable.getScope().isEmpty()
                || !"Optional".equals(optionalOfNullable.getScope().orElseThrow().toString())) {
            return null;
        }
        ConfigBinding primaryBinding = resolveConfigOperandBinding(optionalOfNullable.getArgument(0));
        ConfigBinding fallbackBinding = resolveConfigOperandBinding(methodCallExpr.getArgument(0));
        if (primaryBinding == null && fallbackBinding == null) {
            return null;
        }
        if (primaryBinding == null || fallbackBinding == null || !primaryBinding.equals(fallbackBinding)) {
            if (failOnMismatch) {
                throw new IllegalStateException(
                        "Generated testcase code contains unsupported Optional fallback around requiredConfig");
            }
            return null;
        }
        return primaryBinding;
    }

    private ConfigBinding resolveConfigOperandBinding(Expression expression) {
        ConfigBinding binding = resolveRequiredConfigBinding(expression);
        if (binding != null) {
            return binding;
        }
        return resolveBinding(expression);
    }

    private boolean isRequiredConfigCall(Expression expr, ConfigBinding binding) {
        expr = unwrapExpression(expr);
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

    private boolean isFieldVariable(VariableDeclarator variable) {
        return variable.findAncestor(FieldDeclaration.class).isPresent();
    }

    private void validateBoundVariableType(VariableDeclarator variable) {
        if (!"String".equals(variable.getType().asString())) {
            throw new IllegalStateException(
                    "Generated testcase code must declare " + variable.getNameAsString() + " as String");
        }
    }

    private void ensureNoFabricatedResourceLiteral(String variableName, Expression initializer) {
        initializer = unwrapExpression(initializer);
        if (initializer instanceof StringLiteralExpr stringLiteralExpr
                && looksLikeFabricatedResourceLiteral(stringLiteralExpr.asString())) {
            throw new IllegalStateException(
                    "Generated testcase code contains hard-coded resource literal for " + variableName);
        }
    }

    private void normalizeFieldDeclarationForBeforeAll(FieldDeclaration fieldDeclaration) {
        fieldDeclaration.removeModifier(Modifier.Keyword.FINAL);
        if (!fieldDeclaration.isStatic()) {
            fieldDeclaration.addModifier(Modifier.Keyword.STATIC);
        }
    }

    private boolean containsRequiredConfigCall(Expression expression) {
        expression = unwrapExpression(expression);
        if (resolveRequiredConfigBinding(expression) != null) {
            return true;
        }
        return expression.findAll(MethodCallExpr.class).stream()
                .anyMatch(methodCallExpr -> "requiredConfig".equals(methodCallExpr.getNameAsString()));
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

    private void ensureJUnit5AnnotationImports(
            CompilationUnit compilationUnit,
            ClassOrInterfaceDeclaration testClass) {
        for (MethodDeclaration method : testClass.getMethods()) {
            method.getAnnotations().forEach(annotation -> ensureJUnit5AnnotationImport(
                    compilationUnit,
                    annotation.getNameAsString()));
        }
    }

    private void ensureJUnit5AnnotationImport(CompilationUnit compilationUnit, String annotationName) {
        String simpleName = extractSimpleAnnotationName(annotationName);
        String importName = JUNIT5_ANNOTATION_IMPORTS.get(simpleName);
        if (importName == null || annotationName.contains(".") || hasImport(compilationUnit, importName)) {
            return;
        }
        if (hasConflictingImport(compilationUnit, simpleName)) {
            return;
        }
        compilationUnit.addImport(importName);
    }

    private void ensureBeforeAllImport(CompilationUnit compilationUnit) {
        boolean alreadyImported = compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch("org.junit.jupiter.api.BeforeAll"::equals);
        if (!alreadyImported) {
            compilationUnit.addImport("org.junit.jupiter.api.BeforeAll");
        }
    }

    private void ensureBeforeAllConfigSetup(
            ClassOrInterfaceDeclaration testClass,
            Map<String, ConfigBinding> beforeAllBindings) {
        MethodDeclaration method = testClass.getMethods().stream()
                .filter(this::hasBeforeAllAnnotation)
                .findFirst()
                .orElseGet(() -> {
                    MethodDeclaration created =
                            testClass.addMethod("loadRuntimeConfig", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
                    created.setType("void");
                    created.addAnnotation("BeforeAll");
                    return created;
                });
        if (!hasBeforeAllAnnotation(method)) {
            method.addAnnotation("BeforeAll");
        }
        if (!method.isStatic()) {
            method.addModifier(Modifier.Keyword.STATIC);
        }
        method.setType("void");
        var body = method.getBody().orElseGet(method::createBody);
        Map<String, Boolean> configuredInMethod = new LinkedHashMap<>();
        for (String variableName : beforeAllBindings.keySet()) {
            configuredInMethod.put(variableName, false);
        }
        for (AssignExpr assignExpr : method.findAll(AssignExpr.class)) {
            String variableName = extractAssignedVariableName(assignExpr);
            ConfigBinding binding = beforeAllBindings.get(variableName);
            if (binding == null) {
                continue;
            }
            if (!isRequiredConfigCall(assignExpr.getValue(), binding)) {
                assignExpr.setValue(requiredConfigCall(binding.envKey(), binding.propertyKey()));
            }
            configuredInMethod.put(variableName, true);
        }
        for (Map.Entry<String, ConfigBinding> entry : beforeAllBindings.entrySet()) {
            if (Boolean.TRUE.equals(configuredInMethod.get(entry.getKey()))) {
                continue;
            }
            body.addStatement(entry.getKey()
                    + " = requiredConfig(\""
                    + entry.getValue().envKey()
                    + "\", \""
                    + entry.getValue().propertyKey()
                    + "\");");
        }
    }

    private boolean hasBeforeAllAnnotation(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(annotation -> isJUnit5AnnotationReference(
                        method.findCompilationUnit().orElse(null),
                        annotation.getNameAsString(),
                        List.of("BeforeAll")));
    }

    private void ensureRequiredConfigMethod(ClassOrInterfaceDeclaration testClass) {
        MethodDeclaration method = testClass.getMethodsByName("requiredConfig").stream()
                .filter(candidate -> candidate.getParameters().size() == 2)
                .findFirst()
                .orElseGet(() -> {
                    MethodDeclaration created =
                            testClass.addMethod("requiredConfig", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
                    created.setType("String");
                    created.addParameter("String", "envKey");
                    created.addParameter("String", "propertyKey");
                    return created;
                });
        method.getParameter(0).setName("envKey");
        method.getParameter(1).setName("propertyKey");

        method.createBody()
                .addStatement("String value = System.getenv(envKey);")
                .addStatement("if (value == null || value.isBlank()) { value = System.getProperty(propertyKey); }")
                .addStatement("if (value != null) { value = value.replace(\"\\r\", \"\").replace(\"\\n\", \"\").trim(); }")
                .addStatement("Assumptions.assumeTrue(value != null && !value.isBlank(), "
                        + "\"Missing required config: env \" + envKey + \" or -D\" + propertyKey);")
                .addStatement("return value;");
    }

    private boolean hasRequiredConfigMethod(ClassOrInterfaceDeclaration testClass) {
        return testClass.getMethodsByName("requiredConfig").stream()
                .anyMatch(method -> method.getParameters().size() == 2);
    }

    private void ensureNoRequiredConfigFieldInitializer(ClassOrInterfaceDeclaration testClass) {
        for (FieldDeclaration fieldDeclaration : testClass.getFields()) {
            for (VariableDeclarator variable : fieldDeclaration.getVariables()) {
                if (variable.getInitializer().isPresent()
                        && containsRequiredConfigCall(variable.getInitializer().orElseThrow())) {
                    throw new IllegalStateException(
                            "Generated testcase code must not call requiredConfig during field initialization for "
                                    + variable.getNameAsString());
                }
            }
        }
    }

    private Expression unwrapExpression(Expression expression) {
        Expression current = expression;
        while (current != null && current.isEnclosedExpr()) {
            current = current.asEnclosedExpr().getInner();
        }
        return current;
    }

    private boolean isJUnit5AnnotationReference(
            CompilationUnit compilationUnit,
            String annotationName,
            List<String> expectedSimpleNames) {
        String simpleName = extractSimpleAnnotationName(annotationName);
        if (!expectedSimpleNames.contains(simpleName)) {
            return false;
        }
        if (annotationName.startsWith("org.junit.jupiter.")) {
            return true;
        }
        if (compilationUnit == null) {
            return !annotationName.contains(".");
        }
        String expectedImport = JUNIT5_ANNOTATION_IMPORTS.get(simpleName);
        if (expectedImport != null && hasImport(compilationUnit, expectedImport)) {
            return true;
        }
        if (hasConflictingImport(compilationUnit, simpleName)) {
            return false;
        }
        return !annotationName.contains(".");
    }

    private boolean hasImport(CompilationUnit compilationUnit, String importName) {
        return compilationUnit != null && compilationUnit.getImports().stream()
                .map(ImportDeclaration::getNameAsString)
                .anyMatch(importName::equals);
    }

    private boolean hasConflictingImport(CompilationUnit compilationUnit, String simpleName) {
        return compilationUnit != null && compilationUnit.getImports().stream()
                .filter(importDeclaration -> !importDeclaration.isAsterisk())
                .map(ImportDeclaration::getNameAsString)
                .anyMatch(importName -> importName.endsWith("." + simpleName)
                        && !importName.equals(JUNIT5_ANNOTATION_IMPORTS.get(simpleName)));
    }

    private String extractSimpleAnnotationName(String annotationName) {
        int separator = annotationName.lastIndexOf('.');
        return separator >= 0 ? annotationName.substring(separator + 1) : annotationName;
    }

    private record FieldRewriteResult(boolean helperRequired, Map<String, ConfigBinding> beforeAllBindings) {
    }

    private record ConfigBinding(String envKey, String propertyKey) {
    }
}
