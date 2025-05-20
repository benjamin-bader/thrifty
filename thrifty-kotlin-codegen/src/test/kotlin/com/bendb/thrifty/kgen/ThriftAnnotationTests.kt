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
package com.bendb.thrifty.kgen

import com.bendb.thrifty.compiler.spi.ThriftAnnotations
import com.bendb.thrifty.compiler.spi.TypeProcessor
import com.bendb.thrifty.schema.FieldNamingPolicy
import com.bendb.thrifty.schema.Loader
import com.bendb.thrifty.schema.Schema
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.DelicateKotlinPoetApi
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.tag
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class ThriftAnnotationTests {
  @TempDir lateinit var tempDir: File

  annotation class TestAnnotation(val name: String, val value: String)

  class ThriftAnnotationKotlinProcessor : TypeProcessor {
    @OptIn(DelicateKotlinPoetApi::class)
    override fun process(type: TypeSpec): TypeSpec {
      val newEnumConstants =
          type.enumConstants.entries.map { (name, value) -> name to process(value) }
      val newPropertySpecs =
          type.propertySpecs.map { prop ->
            val annotations = prop.tag<ThriftAnnotations>()?.annotations ?: return@map prop
            val newAnnotations =
                annotations.map { (name, value) ->
                  AnnotationSpec.builder(TestAnnotation::class.java)
                      .addMember("%L = \"%L\"", name, value)
                      .build()
                }
            return@map prop.toBuilder().addAnnotations(newAnnotations).build()
          }
      val newTypeSpecs = type.typeSpecs.map { spec -> process(spec) }
      return type.toBuilder().run {
        type.tag<ThriftAnnotations>()?.annotations?.also { annotations ->
          val newAnnotations =
              annotations.map { (name, value) ->
                AnnotationSpec.builder(TestAnnotation::class.java)
                    .addMember("%L = \"%L\"", name, value)
                    .build()
              }
          addAnnotations(newAnnotations)
        }
        enumConstants.clear()
        enumConstants.putAll(newEnumConstants)
        propertySpecs.clear()
        propertySpecs.addAll(newPropertySpecs)
        typeSpecs.clear()
        typeSpecs.addAll(newTypeSpecs)
        build()
      }
    }
  }

  @Test
  fun `struct to data class should generate annotation from custom Thrift annotation`() {
    val thrift =
        """
            namespace kt com.test

            struct Test {
              1: optional string jsonName (json.name = "json_name");
              2: optional string kotlin_name (go.name = "GoName", kotlin.name = "kotlinName");
            } (kotlin.name = "KotlinTest")
        """
            .trimIndent()

    val file = generate(thrift).single()

    file.toString().also {
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(kotlin.name = "KotlinTest")
          """
              .trimIndent()
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(json.name = "json_name")
          """
              .trimIndent()
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(go.name = "GoName")
          """
              .trimIndent()
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(kotlin.name = "kotlinName")
          """
              .trimIndent()
    }
  }

  @Test
  fun `union generate sealed class should generate annotation from custom Thrift annotation`() {
    val thrift =
        """
            namespace kt com.test

            union Union {
              1: i32 Foo;
              2: i64 Bar;
              3: string Baz;
              4: i32 NotFoo (java.name = "NotFoo");
            } (kotlin.name = "UnionClass")
        """
            .trimMargin()

    val file = generate(thrift)

    file.single().toString().also {
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(kotlin.name = "UnionClass")
          """
              .trimIndent()
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(java.name = "NotFoo")
          """
              .trimIndent()
    }
  }

  @Test
  fun `enum generation should generate annotation from custom Thrift annotation`() {
    val thrift =
        """
            namespace kt com.test

            enum Foo {
              FIRST_VALUE = 0 (go.name = "FirstValue"),
              SECOND_VALUE = 1,
              THIRD_VALUE = 2
            } (kotlin.name = "FooEnum")
        """
            .trimIndent()

    val file = generate(thrift)

    file.single().toString().also {
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(go.name = "FirstValue")
          """
              .trimIndent()
      it shouldContain
          """
              @ThriftAnnotationTests.TestAnnotation(kotlin.name = "FooEnum")
          """
              .trimIndent()
    }
  }

  private fun generate(thrift: String): List<FileSpec> {

    return KotlinCodeGenerator(FieldNamingPolicy.JAVA)
        .apply { processor = ThriftAnnotationKotlinProcessor() }
        .generate(load(thrift))
  }

  private fun load(thrift: String): Schema {
    val file = File(tempDir, "test.thrift").also { it.writeText(thrift) }
    val loader = Loader().apply { addThriftFile(file.toPath()) }
    return loader.load()
  }
}
