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
package com.bendb.thrifty.compiler

import com.bendb.thrifty.kgen.KotlinCodeGenerator
import com.bendb.thrifty.schema.FieldNamingPolicy
import com.bendb.thrifty.schema.LoadFailedException
import com.bendb.thrifty.schema.Loader
import com.bendb.thrifty.schema.Schema
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * A program that compiles Thrift IDL files into Java source code for use with thrifty-runtime.
 *
 * ```
 * java -jar thrifty-compiler.jar --out=/path/to/output
 * [--path=dir/for/search/path]
 * [--list-type=java.util.ArrayList]
 * [--set-type=java.util.HashSet]
 * [--map-type=java.util.HashMap]
 * [--kt-file-per-type]
 * [--kt-jvm-static]
 * [--kt-big-enums]
 * [--parcelable]
 * [--omit-service-clients]
 * [--omit-file-comments]
 * file1.thrift
 * file2.thrift
 * ...
 * ```
 *
 * `--out` is required, and specifies the directory to which generated Java sources will be written.
 *
 * `--path` can be given multiple times. Each directory so specified will be placed on the search
 * path. When resolving `include` statements during thrift compilation, these directories will be
 * searched for included files.
 *
 * `--list-type` is optional. When provided, the compiler will use the given class name when
 * instantiating list-typed values. Defaults to [ArrayList].
 *
 * `--set-type` is optional. When provided, the compiler will use the given class name when
 * instantiating set-typed values. Defaults to [java.util.HashSet].
 *
 * `--map-type` is optional. When provided, the compiler will use the given class name when
 * instantiating map-typed values. Defaults to [java.util.HashMap]. Android users will likely wish
 * to substitute `android.support.v4.util.ArrayMap`.
 *
 * `--kt-file-per-type` is optional. When specified, one Kotlin file will be generated for each
 * top-level generated Thrift type. When absent (the default), all generated types in a single
 * package will go in one file named `ThriftTypes.kt`. Implies `--lang=kotlin`.
 *
 * `--kt-jvm-static` is optional. When specified, certain companion-object functions will be
 * annotated with [JvmStatic]. This option is for those who want easier Java interop, and results in
 * slightly larger code. Implies `--lang=kotlin`.
 *
 * `--kt-big-enums` is optional. When specified, generated enums will use a different
 * representation. Rather than each enum member containing its value, a single large function
 * mapping enums to values will be generated. This works around some JVM class-size limitations in
 * some extreme cases, such as an enum with thousands of members. This should be avoided unless you
 * know you need it. Implies `--lang=kotlin`.
 *
 * `--parcelable` is optional. When provided, generated types will contain a `Parcelable`
 * implementation. Kotlin types will use the `@Parcelize` extension.
 *
 * `--omit-service-clients` is optional. When specified, no service clients are generated.
 *
 * `--omit-file-comments` is optional. When specified, no file-header comment is generated. The
 * default behavior is to prefix generated files with a comment indicating that they are generated
 * by Thrifty, and should probably not be modified by hand.
 *
 * If no .thrift files are given, then all .thrift files located on the search path will be
 * implicitly included; otherwise only the given files (and those included by them) will be
 * compiled.
 */
class ThriftyCompiler {

  private val cli =
      object :
          CliktCommand(
              name = "thrifty-compiler",
          ) {
        val outputDirectory: Path by
            option("-o", "--out", help = "the output directory for generated files")
                .path(canBeFile = false, canBeDir = true)
                .required()
                .validate { Files.isDirectory(it) || !Files.exists(it) }

        val searchPath: List<Path> by
            option("-p", "--path", help = "the search path for .thrift includes")
                .path(mustExist = true, canBeDir = true, canBeFile = false)
                .multiple()

        val nameStyle: FieldNamingPolicy by
            option(
                    "--name-style",
                    help =
                        "Format style for generated names.  Default is to leave names unaltered.")
                .choice("default" to FieldNamingPolicy.DEFAULT, "java" to FieldNamingPolicy.JAVA)
                .default(FieldNamingPolicy.DEFAULT)

        val listTypeName: String? by
            option("--list-type", help = "when specified, the concrete type to use for lists")
        val setTypeName: String? by
            option("--set-type", help = "when specified, the concrete type to use for sets")
        val mapTypeName: String? by
            option("--map-type", help = "when specified, the concrete type to use for maps")

        val emitParcelable: Boolean by
            option(
                    "--parcelable",
                    help = "When set, generates Parcelable implementations for structs")
                .flag(default = false)

        val omitServiceClients: Boolean by
            option("--omit-service-clients", help = "When set, don't generate service clients")
                .flag(default = false)

        val generateServer: Boolean by
            option(
                    "--experimental-kt-generate-server",
                    help = "When set, generate kotlin server implementation (EXPERIMENTAL)")
                .flag(default = false)

        val omitFileComments: Boolean by
            option(
                    "--omit-file-comments",
                    help = "When set, don't add file comments to generated files")
                .flag(default = false)

        val kotlinEmitJvmName: Boolean by
            option("--kt-emit-jvmname", help = "When set, emit @JvmName annotations")
                .flag(default = false)

        val kotlinFilePerType: Boolean by
            option(
                    "--kt-file-per-type",
                    help = "Generate one .kt file per type; default is one per namespace.")
                .flag(default = false)

        val kotlinEmitJvmStatic: Boolean by
            option(
                    "--kt-jvm-static",
                    help =
                        "Add @JvmStatic annotations to companion-object functions.  For ease-of-use with Java code.")
                .flag("--kt-no-jvm-static", default = false)

        val kotlinBigEnums: Boolean by
            option("--kt-big-enums").flag("--kt-no-big-enums", default = false)

        val thriftFiles: List<Path> by
            argument(help = "All .thrift files to compile")
                .path(mustExist = true, canBeFile = true, canBeDir = false, mustBeReadable = true)
                .multiple()

        val failOnUnknownEnumValues by
            option(
                    "--fail-on-unknown-enum-values",
                    help =
                        "When set, unknown values found when decoding will throw an exception. Otherwise, it uses null/default values.")
                .flag("--no-fail-on-unknown-enum-values", default = true)

        override fun help(context: Context): String {
          return "Generate Kotlin code from .thrift files"
        }

        override fun run() {
          val loader = Loader()
          for (thriftFile in thriftFiles) {
            loader.addThriftFile(thriftFile)
          }

          loader.addIncludePath(Paths.get(System.getProperty("user.dir")))
          for (dir in searchPath) {
            loader.addIncludePath(dir)
          }

          val schema: Schema
          try {
            schema = loader.load()
          } catch (e: LoadFailedException) {
            if (!e.errorReporter.hasError && e.cause != null) {
              println(e.cause)
            }
            for (report in e.errorReporter.formattedReports()) {
              println(report)
            }

            Runtime.getRuntime().exit(1)
            return
          }

          generateKotlin(schema)
        }

        private fun generateKotlin(schema: Schema) {
          val gen = KotlinCodeGenerator(nameStyle)

          if (emitParcelable) {
            gen.parcelize()
          }

          if (omitServiceClients) {
            gen.omitServiceClients()
          }

          if (generateServer) {
            gen.generateServer()
          }

          if (kotlinEmitJvmName) {
            gen.emitJvmName()
          }

          if (kotlinEmitJvmStatic) {
            gen.emitJvmStatic()
          }

          if (kotlinBigEnums) {
            gen.emitBigEnums()
          }

          gen.emitFileComment(!omitFileComments)

          if (kotlinFilePerType) {
            gen.filePerType()
          } else {
            gen.filePerNamespace()
          }

          gen.failOnUnknownEnumValues(failOnUnknownEnumValues)

          listTypeName?.let { gen.listClassName(it) }
          setTypeName?.let { gen.setClassName(it) }
          mapTypeName?.let { gen.mapClassName(it) }

          val svc = TypeProcessorService.getInstance()
          svc.processor?.let { gen.processor = it }

          val specs = gen.generate(schema)

          specs.forEach { it.writeTo(outputDirectory) }
        }
      }

  fun compile(args: Array<String>) {
    try {
      cli.main(args)
    } catch (e: Exception) {
      cli.echo("Unhandled exception", err = true)
      e.printStackTrace(System.err)
      exitProcess(1)
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      ThriftyCompiler().compile(args)
    }
  }
}
