/*
 * Thrifty
 *
 * Copyright (c) Benjamin Bader
 * Copyright (c) Microsoft Corporation
 *
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS CODE IS PROVIDED ON AN  *AS IS* BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING
 * WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE,
 * FITNESS FOR A PARTICULAR PURPOSE, MERCHANTABLITY OR NON-INFRINGEMENT.
 *
 * See the Apache Version 2.0 License for specific language governing permissions and limitations under the License.
 */
package com.bendb.thrifty.gradle;

import com.bendb.thrifty.compiler.TypeProcessorService;
import com.bendb.thrifty.compiler.spi.TypeProcessor;
import com.bendb.thrifty.kgen.KotlinCodeGenerator;
import com.bendb.thrifty.schema.ErrorReporter;
import com.bendb.thrifty.schema.FieldNamingPolicy;
import com.bendb.thrifty.schema.LoadFailedException;
import com.bendb.thrifty.schema.Loader;
import com.bendb.thrifty.schema.Schema;
import com.squareup.kotlinpoet.FileSpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.gradle.api.GradleException;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link WorkAction} that actually generates the Thrifty sources.
 *
 * <p>We're doing this via the Worker API to ensure that Gradle's hard-coded Kotlin version doesn't cause us grief.
 * Thrifty is entirely written in Kotlin, and there's no guarantee that we'll be using a version compatible with
 * whatever Gradle happens to have bundled. According to some of their engineers, this (with classpath-level isolation)
 * is the only safe way to use Kotlin in the context of a Gradle plugin.
 */
public abstract class GenerateThriftSourcesWorkAction implements WorkAction<GenerateThriftSourcesWorkParams> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateThriftSourcesWorkAction.class);

    @Override
    public void execute() {
        try {
            actuallyExecute();
        } catch (IOException e) {
            throw new GradleException("Thrift generation failed", e);
        }
    }

    private void actuallyExecute() throws IOException {
        Schema schema;
        try {
            Loader loader = new Loader();
            for (File file : getParameters().getIncludePath().get()) {
                loader.addIncludePath(file.toPath());
            }

            for (File file : getParameters().getSource()) {
                loader.addThriftFile(file.toPath());
            }

            schema = loader.load();
        } catch (LoadFailedException e) {
            reportThriftException(e);
            throw new GradleException("Thrift compilation failed", e);
        }

        try {
            deleteRecursively(getParameters().getOutputDirectory().get().getAsFile());
        } catch (IOException e) {
            LOGGER.warn("Error clearing stale output", e);
        }

        SerializableThriftOptions opts = getParameters().getThriftOptions().get();
        generateKotlinThrifts(schema, opts);
    }

    private void reportThriftException(LoadFailedException e) {
        for (ErrorReporter.Report report : e.getErrorReporter().getReports()) {
            String template = "{}: {}";
            switch (report.getLevel()) {
                case WARNING:
                    LOGGER.warn(template, report.getLocation(), report.getMessage());
                    break;
                case ERROR:
                    LOGGER.error(template, report.getLocation(), report.getMessage());
                    break;
                default:
                    throw new IllegalStateException("Unexpected report level: " + report.getLevel());
            }
        }

        ShowStacktrace sst = getParameters().getShowStacktrace().getOrElse(ShowStacktrace.INTERNAL_EXCEPTIONS);
        switch (sst) {
            case ALWAYS:
            case ALWAYS_FULL:
                LOGGER.error("Thrift compilation failed", e);
                break;
        }
    }

    private void deleteRecursively(File file) throws IOException {
        Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes basicFileAttributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void generateKotlinThrifts(Schema schema, SerializableThriftOptions opts) throws IOException {
        KotlinCodeGenerator gen = new KotlinCodeGenerator(policyFromNameStyle(opts.getNameStyle()))
                .emitJvmName()
                .filePerType()
                .failOnUnknownEnumValues(!opts.isAllowUnknownEnumValues());

        if (opts.isParcelable()) {
            gen.parcelize();
        }

        if (!opts.isGenerateServiceClients()) {
            gen.omitServiceClients();
        }

        if (opts.isGenerateServer()) {
            gen.generateServer();
        }

        if (opts.getListType() != null) {
            gen.listClassName(opts.getListType());
        }

        if (opts.getSetType() != null) {
            gen.setClassName(opts.getSetType());
        }

        if (opts.getMapType() != null) {
            gen.mapClassName(opts.getMapType());
        }

        TypeProcessorService typeProcessorService = TypeProcessorService.getInstance();
        TypeProcessor kotlinProcessor = typeProcessorService.getProcessor();
        if (kotlinProcessor != null) {
            gen.setProcessor(kotlinProcessor);
        }

        for (FileSpec fs : gen.generate(schema)) {
            fs.writeTo(getParameters().getOutputDirectory().getAsFile().get());
        }
    }

    private static FieldNamingPolicy policyFromNameStyle(FieldNameStyle style) {
        switch (style) {
            case DEFAULT:
                return FieldNamingPolicy.Companion.getDEFAULT();
            case JAVA:
                return FieldNamingPolicy.Companion.getJAVA();
            case PASCAL:
                return FieldNamingPolicy.Companion.getPASCAL();
        }
        throw new AssertionError("unpossible");
    }
}
