package org.example;

import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Extractor {
    private final File inputDir;
    private final File outputDir;

    private final String inputPackageName;
    private final String outputPackageName;

    public Extractor(File inputDir, File outputDir, String inputPackageName, String outputPackageName) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.inputPackageName = inputPackageName;
        this.outputPackageName = outputPackageName;
    }

    public void exec() throws IOException, ClassNotFoundException {
        final boolean ignored = outputDir.mkdirs();

        final URLClassLoader urlClassLoader = URLClassLoader.newInstance(new URL[]{inputDir.toURI().toURL()});

        final File inputDirWithPackage = inputDir.toPath().resolve(inputPackageName.replaceAll(Pattern.quote("."), "/")).toFile();

        for (final File file : Objects.requireNonNull(inputDirWithPackage.listFiles())) {
            final String clazzName = file.getName().split("[.]")[0];

            final Class<?> clazz = urlClassLoader.loadClass(inputPackageName + "." + clazzName);

            if (clazz.getEnclosingClass() != null) {
                continue;
            }

            final Field[] fields = clazz.getDeclaredFields();

            final List<FieldSpec> fieldSpecs = Arrays.stream(fields)
                    .filter(field -> !field.getName().startsWith("_"))
                    .map(field -> FieldSpec.builder(resolveType(TypeName.get(field.getGenericType())), field.getName(), Modifier.PRIVATE)
                            .build()
                    ).collect(Collectors.toList());

            final List<MethodSpec> getterMethods = fieldSpecs.stream()
                    .map(fieldSpec -> MethodSpec.methodBuilder("get" + capitalize(fieldSpec.name))
                            .addModifiers(Modifier.PUBLIC)
                            .returns(resolveType(fieldSpec.type))
                            .addStatement("return " + fieldSpec.name)
                            .build())
                    .collect(Collectors.toList());

            final List<MethodSpec> setterMethods = fieldSpecs.stream()
                    .map(fieldSpec -> MethodSpec.methodBuilder("set" + capitalize(fieldSpec.name))
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(ParameterSpec.builder(fieldSpec.type, "value").build())
                            .addStatement("this.$N = value", fieldSpec.name)
                            .build())
                    .collect(Collectors.toList());

            final List<TypeSpec> nestedClasses = Arrays.stream(clazz.getDeclaredClasses())
                    .map(this::makeClazz)
                    .collect(Collectors.toList());

            final TypeSpec newClazzSpec = TypeSpec.classBuilder(clazz.getSimpleName())
                    .addModifiers(Modifier.PUBLIC)
                    .addFields(fieldSpecs)
                    .addMethods(getterMethods)
                    .addMethods(setterMethods)
                    .addTypes(nestedClasses)
                    .build();

            JavaFile javaFile = JavaFile.builder(outputPackageName, newClazzSpec).build();
            javaFile.writeTo(outputDir);
        }
    }

    private TypeSpec makeClazz(Class<?> clazz) {
        if (clazz.isEnum()) {
            final TypeName resolvedClazzName = resolveType(ClassName.get(clazz));
            TypeSpec.Builder enumSpec = TypeSpec.enumBuilder(clazz.getSimpleName())
                    .addModifiers(Modifier.PUBLIC);

            var constants = clazz.getEnumConstants();

            for (int i = 0; i < constants.length; i++) {
                var constant = constants[i];
                enumSpec.addEnumConstant(constant.toString(), TypeSpec.anonymousClassBuilder(Integer.toString(i)).build());
            }

            enumSpec.addField(TypeName.LONG, "id", Modifier.PRIVATE, Modifier.FINAL);
            enumSpec.addMethod(MethodSpec.methodBuilder("id")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.LONG)
                    .addStatement("return id")
                    .build());

            enumSpec.addMethod(MethodSpec.constructorBuilder()
                    .addParameter(TypeName.LONG, "id")
                    .addStatement("this.id = id")
                    .build());

            enumSpec.addField(FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(Map.class), ClassName.get(Long.class), resolvedClazzName), "byId")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(CodeBlock.builder()
                            .addStatement("new $T<>()", HashMap.class)
                            .build())
                    .build());

            enumSpec.addStaticBlock(CodeBlock.builder()
                    .beginControlFlow("for ($T e : $T.values())", resolvedClazzName, resolvedClazzName)
                    .addStatement("byId.put(e.id(), e)")
                    .endControlFlow()
                    .build());

            enumSpec.addMethod(MethodSpec.methodBuilder("byId")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(resolvedClazzName)
                    .addParameter(TypeName.LONG, "id")
                    .addStatement("return byId.get(id)")
                    .build());

            return enumSpec.build();
        }

        final Field[] fields = clazz.getDeclaredFields();

        final List<FieldSpec> fieldSpecs = Arrays.stream(fields)
                .filter(field -> !field.getName().startsWith("_"))
                .map(field -> FieldSpec.builder(resolveType(TypeName.get(field.getGenericType())), field.getName(), Modifier.PRIVATE)
                        .build()
                ).collect(Collectors.toList());

        final List<MethodSpec> getterMethods = fieldSpecs.stream()
                .map(fieldSpec -> MethodSpec.methodBuilder("get" + capitalize(fieldSpec.name))
                        .addModifiers(Modifier.PUBLIC)
                        .returns(resolveType(resolveType(fieldSpec.type)))
                        .addStatement("return " + fieldSpec.name)
                        .build())
                .collect(Collectors.toList());

        final List<MethodSpec> setterMethods = fieldSpecs.stream()
                .map(fieldSpec -> MethodSpec.methodBuilder("set" + capitalize(fieldSpec.name))
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(resolveType(fieldSpec.type), "value").build())
                        .addStatement("this.$N = value", fieldSpec.name)
                        .build())
                .collect(Collectors.toList());

        final List<TypeSpec> nestedClasses = Arrays.stream(clazz.getDeclaredClasses())
                .map(this::makeClazz)
                .collect(Collectors.toList());

        return TypeSpec.classBuilder(clazz.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addFields(fieldSpecs)
                .addMethods(getterMethods)
                .addMethods(setterMethods)
                .addTypes(nestedClasses)
                .build();
    }

    public static String capitalize(String str)
    {
        if (str == null || str.length() <= 1) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private TypeName resolveType(TypeName value) {
        if (value instanceof ParameterizedTypeName) {
            final ClassName rawType = ((ParameterizedTypeName) value).rawType;
            final List<TypeName> typeArgs = ((ParameterizedTypeName) value).typeArguments.stream().map(this::resolveType).collect(Collectors.toList());
            value = ParameterizedTypeName.get(rawType, typeArgs.toArray(TypeName[]::new));
        } else if (value instanceof ClassName) {
            if (((ClassName) value).packageName().equals(inputPackageName)) {
                if (((ClassName) value).enclosingClassName() != null) {
                    value = ClassName.get(outputPackageName, ((ClassName) value).enclosingClassName().simpleName(), ((ClassName) value).simpleName());
                } else {
                    value = ClassName.get(outputPackageName, ((ClassName) value).simpleName());
                }
            }
        }
        return value;
    }
}
