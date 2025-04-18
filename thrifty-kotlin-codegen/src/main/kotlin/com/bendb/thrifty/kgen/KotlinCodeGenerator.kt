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

import com.bendb.thrifty.Adapter
import com.bendb.thrifty.Obfuscated
import com.bendb.thrifty.Redacted
import com.bendb.thrifty.Struct
import com.bendb.thrifty.TType
import com.bendb.thrifty.ThriftException
import com.bendb.thrifty.ThriftField
import com.bendb.thrifty.compiler.spi.TypeProcessor
import com.bendb.thrifty.protocol.MessageMetadata
import com.bendb.thrifty.protocol.Protocol
import com.bendb.thrifty.schema.BuiltinType
import com.bendb.thrifty.schema.Constant
import com.bendb.thrifty.schema.EnumType
import com.bendb.thrifty.schema.Field
import com.bendb.thrifty.schema.FieldNamingPolicy
import com.bendb.thrifty.schema.ListType
import com.bendb.thrifty.schema.MapType
import com.bendb.thrifty.schema.NamespaceScope
import com.bendb.thrifty.schema.Schema
import com.bendb.thrifty.schema.ServiceMethod
import com.bendb.thrifty.schema.ServiceType
import com.bendb.thrifty.schema.SetType
import com.bendb.thrifty.schema.StructType
import com.bendb.thrifty.schema.ThriftType
import com.bendb.thrifty.schema.TypedefType
import com.bendb.thrifty.schema.UserElement
import com.bendb.thrifty.schema.parser.ConstValueElement
import com.bendb.thrifty.schema.parser.DoubleValueElement
import com.bendb.thrifty.schema.parser.IdentifierValueElement
import com.bendb.thrifty.schema.parser.IntValueElement
import com.bendb.thrifty.schema.parser.ListValueElement
import com.bendb.thrifty.schema.parser.LiteralValueElement
import com.bendb.thrifty.schema.parser.MapValueElement
import com.bendb.thrifty.service.AsyncClientBase
import com.bendb.thrifty.service.MethodCall
import com.bendb.thrifty.service.TMessageType
import com.bendb.thrifty.service.server.DefaultErrorHandler
import com.bendb.thrifty.service.server.ErrorHandler
import com.bendb.thrifty.service.server.Processor
import com.bendb.thrifty.service.server.ServerCall
import com.bendb.thrifty.util.ObfuscationUtil
import com.bendb.thrifty.util.ProtocolUtil
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.LinkedHashMultimap
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.AnnotationSpec.UseSiteTarget.FILE
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.jvm.jvmField
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.tag
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import okio.ByteString

private object Tags {
  val ADAPTER = "RESERVED:ADAPTER"
  val MESSAGE = "RESERVED:message"
  val CAUSE = "RESERVED:cause"
  val FIND_BY_VALUE = "RESERVED:findByValue"
  val VALUE = "RESERVED:value"
  val SEND = "RESERVED:send"
  val RECEIVE = "RESERVED:receive"
  val RESULT = "RESERVED:resultValue"
  val FIELD = "RESERVED:fieldMeta"
  val DEFAULT = "RESERVED:default"
}

// ClassName instances for those "constant" types that we cannot use
// the Literal::class.asClassName() syntax (i.e. because they are typaliases
// to JVM types), or because they are just constant and there's no reason
// to instantiate a bunch of them.
private object ClassNames {
  val COROUTINE_DISPATCHER = ClassName("kotlinx.coroutines", "CoroutineDispatcher")
  val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")
  val EXCEPTION = ClassName("kotlin", "Exception")
  val ILLEGAL_ARGUMENT_EXCEPTION = ClassName("kotlin", "IllegalArgumentException")
  val THROWABLE = ClassName("kotlin", "Throwable")
  val THROWS = ClassName("kotlin", "Throws")
  val ARRAY_LIST = ClassName("kotlin.collections", "ArrayList")
  val LINKED_HASH_SET = ClassName("kotlin.collections", "LinkedHashSet")
  val LINKED_HASH_MAP = ClassName("kotlin.collections", "LinkedHashMap")
  val IO_EXCEPTION = ClassName("okio", "IOException")
}

/**
 * Generates Kotlin code from a [Schema].
 *
 * @param fieldNamingPolicy A user-specified naming policy for fields.
 */
class KotlinCodeGenerator(fieldNamingPolicy: FieldNamingPolicy = FieldNamingPolicy.DEFAULT) {
  enum class OutputStyle {
    FILE_PER_NAMESPACE,
    FILE_PER_TYPE
  }

  // TODO: Add a compiler flag to omit struct generation
  private var shouldImplementStruct: Boolean = true

  private var parcelize: Boolean = false
  private var omitServiceClients: Boolean = false
  private var emitJvmName: Boolean = false
  private var emitJvmStatic: Boolean = false
  private var emitBigEnums: Boolean = false
  private var emitFileComment: Boolean = true
  private var failOnUnknownEnumValues: Boolean = true
  private var generateServer: Boolean = false

  private var listClassName: ClassName? = null
  private var setClassName: ClassName? = null
  private var mapClassName: ClassName? = null

  private val nameAllocators =
      CacheBuilder.newBuilder()
          .build(
              object : CacheLoader<UserElement, NameAllocator>() {
                override fun load(key: UserElement): NameAllocator {
                  return NameAllocator().apply {
                    when (key) {
                      is StructType -> {
                        newName("ADAPTER", Tags.ADAPTER)
                        if (key.isException) {
                          newName("message", Tags.MESSAGE)
                          newName("cause", Tags.CAUSE)
                        }

                        if (key.isUnion && key.fields.any { it.defaultValue != null }) {
                          newName("DEFAULT", Tags.DEFAULT)
                        }

                        for (field in key.fields) {
                          val conformingName = fieldNamingPolicy.apply(field.name)
                          newName(conformingName, field)
                        }

                        newName("result", Tags.RESULT)
                      }

                      is EnumType -> {
                        newName("findByValue", Tags.FIND_BY_VALUE)
                        newName("value", Tags.VALUE)
                        for (member in key.members) {
                          newName(member.name, member)
                        }
                      }

                      is ServiceType -> {
                        for (method in key.methods) {
                          newName(method.name, method)
                        }
                      }

                      is ServiceMethod -> {
                        newName("send", Tags.SEND)
                        newName("receive", Tags.RECEIVE)
                        newName("resultValue", Tags.RESULT)
                        newName("fieldMeta", Tags.FIELD)

                        for (param in key.parameters) {
                          newName(param.name, param)
                        }

                        for (ex in key.exceptions) {
                          newName(ex.name, ex)
                        }
                      }

                      else -> error("Unexpected UserElement: $key")
                    }
                  }
                }
              })

  // region Configuration

  var processor: TypeProcessor = NoTypeProcessor
  var outputStyle: OutputStyle = OutputStyle.FILE_PER_NAMESPACE

  fun filePerNamespace(): KotlinCodeGenerator = apply {
    outputStyle = OutputStyle.FILE_PER_NAMESPACE
  }

  fun filePerType(): KotlinCodeGenerator = apply { outputStyle = OutputStyle.FILE_PER_TYPE }

  fun parcelize(): KotlinCodeGenerator = apply { parcelize = true }

  fun listClassName(name: String): KotlinCodeGenerator = apply {
    this.listClassName = ClassName.bestGuess(name)
  }

  fun setClassName(name: String): KotlinCodeGenerator = apply {
    this.setClassName = ClassName.bestGuess(name)
  }

  fun mapClassName(name: String): KotlinCodeGenerator = apply {
    this.mapClassName = ClassName.bestGuess(name)
  }

  fun omitServiceClients(): KotlinCodeGenerator = apply { this.omitServiceClients = true }

  fun generateServer(): KotlinCodeGenerator = apply { this.generateServer = true }

  fun emitJvmName(): KotlinCodeGenerator = apply { this.emitJvmName = true }

  fun emitJvmStatic(): KotlinCodeGenerator = apply { this.emitJvmStatic = true }

  fun emitBigEnums(): KotlinCodeGenerator = apply { this.emitBigEnums = true }

  fun emitFileComment(emitFileComment: Boolean): KotlinCodeGenerator = apply {
    this.emitFileComment = emitFileComment
  }

  fun failOnUnknownEnumValues(value: Boolean = true): KotlinCodeGenerator = apply {
    this.failOnUnknownEnumValues = value
  }

  private object NoTypeProcessor : TypeProcessor {
    override fun process(typeSpec: TypeSpec) = typeSpec
  }

  // endregion Configuration
  private val specsByNamespace = LinkedHashMultimap.create<String, TypeSpec>()
  private val constantsByNamespace = LinkedHashMultimap.create<String, PropertySpec>()
  private val typedefsByNamespace = LinkedHashMultimap.create<String, TypeAliasSpec>()

  fun generate(schema: Schema): List<FileSpec> {
    specsByNamespace.clear()
    constantsByNamespace.clear()
    typedefsByNamespace.clear()

    schema.typedefs.forEach { typedefsByNamespace.put(it.kotlinNamespace, generateTypeAlias(it)) }
    schema.enums.forEach { specsByNamespace.put(it.kotlinNamespace, generateEnumClass(it)) }
    schema.structs.forEach {
      specsByNamespace.put(it.kotlinNamespace, generateDataClass(schema, it))
    }
    schema.unions.forEach {
      // NOTE: We can't adequately represent empty unions with sealed classes, because one can't
      //       _isntantiate_ an empty sealed class.  As an ugly hack, we represent empty unions
      //       as a plain-old class.  We could technically make it an object, but I don't really
      //       care enough to do it.  Empty unions themselves are ugly, so this ugly hack is
      // fitting.
      val spec =
          if (it.fields.isNotEmpty()) {
            generateSealedClass(schema, it)
          } else {
            generateDataClass(schema, it)
          }
      specsByNamespace.put(it.kotlinNamespace, spec)
    }
    schema.exceptions.forEach {
      specsByNamespace.put(it.kotlinNamespace, generateDataClass(schema, it))
    }

    val constantNameAllocators = mutableMapOf<String, NameAllocator>()
    schema.constants.forEach {
      val ns = it.kotlinNamespace
      val allocator = constantNameAllocators.getOrPut(ns) { NameAllocator() }
      val property = generateConstantProperty(schema, allocator, it)
      constantsByNamespace.put(ns, property)
    }

    if (!omitServiceClients) {
      schema.services.forEach {
        val iface = generateCoroServiceInterface(it)
        val impl = generateCoroServiceImplementation(schema, it, iface)

        specsByNamespace.put(it.kotlinNamespace, iface)
        specsByNamespace.put(it.kotlinNamespace, impl)
      }
    }

    if (generateServer) {
      schema.services.forEach {
        val iface = generateCoroServiceInterface(it)
        specsByNamespace.put(getServerNamespaceFor(it), iface)
        specsByNamespace.put(it.kotlinNamespace, generateProcessorImplementation(schema, it, iface))
      }
    }

    return when (outputStyle) {
      OutputStyle.FILE_PER_NAMESPACE -> {
        val namespaces =
            mutableSetOf<String>().apply {
              addAll(specsByNamespace.keys())
              addAll(constantsByNamespace.keys())
            }

        val fileSpecsByNamespace =
            namespaces.map { it to makeFileSpecBuilder(it, "ThriftTypes") }.toMap()

        fileSpecsByNamespace.map { (ns, fileSpec) ->
          typedefsByNamespace[ns].forEach { fileSpec.addTypeAlias(it) }
          constantsByNamespace[ns].forEach { fileSpec.addProperty(it) }
          specsByNamespace[ns].mapNotNull { processor.process(it) }.forEach { fileSpec.addType(it) }
          fileSpec.build()
        }
      }

      OutputStyle.FILE_PER_TYPE -> {
        sequence {
              val types = specsByNamespace.entries().asSequence()
              for ((ns, type) in types) {
                val processedType = processor.process(type) ?: continue
                val name =
                    processedType.name
                        ?: throw AssertionError("Top-level TypeSpecs must have names")
                val spec = makeFileSpecBuilder(ns, name).addType(processedType).build()

                yield(spec)
              }

              for ((ns, aliases) in typedefsByNamespace.asMap().entries) {
                val spec = makeFileSpecBuilder(ns, "Typedefs")
                aliases.forEach { spec.addTypeAlias(it) }
                yield(spec.build())
              }

              for ((ns, props) in constantsByNamespace.asMap().entries) {
                val spec = makeFileSpecBuilder(ns, "Constants")
                props.forEach { spec.addProperty(it) }
                yield(spec.build())
              }
            }
            .toList()
      }
    }
  }

  private fun getServerNamespaceFor(serviceType: ServiceType) =
      "${serviceType.kotlinNamespace}.server"

  private fun getServerTypeName(serviceType: ServiceType) =
      ClassName(getServerNamespaceFor(serviceType), serviceType.name)

  // region Aliases

  internal fun generateTypeAlias(typedef: TypedefType): TypeAliasSpec {
    return TypeAliasSpec.builder(typedef.name, typedef.oldType.typeName).run {
      if (typedef.hasJavadoc) {
        addKdoc("%L", typedef.documentation)
      }
      build()
    }
  }

  // endregion Aliases

  // region Enums

  internal fun generateEnumClass(enumType: EnumType): TypeSpec {
    val typeBuilder = TypeSpec.enumBuilder(enumType.name)

    if (!emitBigEnums) {
      typeBuilder
          .addProperty(PropertySpec.builder("value", INT).jvmField().initializer("value").build())
          .primaryConstructor(FunSpec.constructorBuilder().addParameter("value", INT).build())
    }

    if (enumType.isDeprecated) typeBuilder.addAnnotation(makeDeprecated())
    if (enumType.hasJavadoc) typeBuilder.addKdoc("%L", enumType.documentation)

    if (parcelize) {
      typeBuilder.addAnnotation(makeParcelable())
      typeBuilder.addAnnotation(suppressLint("ParcelCreator")) // Android Studio bug
    }

    val findByValue =
        FunSpec.builder("findByValue")
            .addParameter("value", INT)
            .returns(enumType.typeName.copy(nullable = true))
            .apply { if (emitJvmStatic) jvmStatic() }
            .beginControlFlow("return when (%N)", "value")

    val nameAllocator = nameAllocators[enumType]
    for (member in enumType.members) {
      val enumMemberSpec =
          TypeSpec.anonymousClassBuilder().apply {
            if (!emitBigEnums) {
              addSuperclassConstructorParameter("%L", member.value)
            }
          }

      if (member.isDeprecated) enumMemberSpec.addAnnotation(makeDeprecated())
      if (member.hasJavadoc) enumMemberSpec.addKdoc("%L", member.documentation)

      val name = nameAllocator.get(member)
      typeBuilder.addEnumConstant(name, enumMemberSpec.build())
      findByValue.addStatement("%L -> %L", member.value, name)
    }

    val companion =
        TypeSpec.companionObjectBuilder()
            .addFunction(findByValue.addStatement("else -> null").endControlFlow().build())
            .build()

    if (emitBigEnums) {
      // Generate the function to map an enum to an int value
      val valueFn =
          FunSpec.builder("value")
              .returns(Int::class)
              .apply {
                beginControlFlow("return when (this)")
                for (member in enumType.members) {
                  val name = nameAllocator[member]
                  addStatement("%L -> %L", name, member.value)
                }
                endControlFlow()
              }
              .build()
      // For convenience, also generate a property which calls through to the function
      // so that Kotlin call-sites don't need to change when transitioning to big enums.
      val valueProperty =
          PropertySpec.builder("value", Int::class)
              .getter(FunSpec.getterBuilder().addStatement("return %N()", "value").build())
              .build()
      typeBuilder.addFunction(valueFn)
      typeBuilder.addProperty(valueProperty)
    }

    return typeBuilder.addType(companion).build()
  }

  // endregion Enums

  // region Structs

  // used to tag things with the corresponding thrift field id
  // in order to be able to refer to them later
  data class FieldIdMarker(val fieldId: Int)

  internal fun generateDataClass(schema: Schema, struct: StructType): TypeSpec {
    val structClassName = ClassName(struct.kotlinNamespace, struct.name)
    val typeBuilder =
        TypeSpec.classBuilder(structClassName).apply {
          if (struct.fields.isNotEmpty()) {
            addModifiers(KModifier.DATA)
          }

          if (struct.isDeprecated) addAnnotation(makeDeprecated())
          if (struct.hasJavadoc) addKdoc("%L", struct.documentation)
          if (struct.isException) superclass(ClassNames.EXCEPTION)
          if (parcelize) {
            addAnnotation(makeParcelable())
            addAnnotation(suppressLint("ParcelCreator")) // Android Studio bug with Parcelize
          }
        }

    val ctorBuilder = FunSpec.constructorBuilder()

    val companionBuilder = TypeSpec.companionObjectBuilder()

    val nameAllocator = nameAllocators[struct]
    for (field in struct.fields) {
      val fieldName = nameAllocator.get(field)
      val typeName =
          field.type.typeName.let { if (!field.required) it.copy(nullable = true) else it }

      val thriftField =
          AnnotationSpec.builder(ThriftField::class).let { anno ->
            anno.addMember("fieldId = ${field.id}")
            if (field.required) anno.addMember("isRequired = true")
            if (field.optional) anno.addMember("isOptional = true")

            field.typedefName?.let { anno.addMember("typedefName = %S", it) }

            anno.build()
          }

      val param = ParameterSpec.builder(fieldName, typeName)

      field.defaultValue?.let { param.defaultValue(renderConstValue(schema, field.type, it)) }

      val prop =
          PropertySpec.builder(fieldName, typeName)
              .initializer(fieldName)
              .jvmField()
              .addAnnotation(thriftField)

      if (field.hasJavadoc) prop.addKdoc("%L", field.documentation)
      if (field.isObfuscated) prop.addAnnotation(Obfuscated::class)
      if (field.isRedacted) prop.addAnnotation(Redacted::class)

      ctorBuilder.addParameter(param.build())
      typeBuilder.addProperty(prop.build())
    }

    val adapterTypeName = ClassName(struct.kotlinNamespace, struct.name, "${struct.name}Adapter")
    val adapterInterfaceTypeName = Adapter::class.asTypeName().parameterizedBy(struct.typeName)

    typeBuilder.addType(generateAdapterFor(struct, adapterTypeName, adapterInterfaceTypeName))

    companionBuilder.addProperty(
        PropertySpec.builder("ADAPTER", adapterInterfaceTypeName)
            .initializer("%T()", adapterTypeName)
            .jvmField()
            .build())

    if (struct.fields.any { it.isObfuscated || it.isRedacted } || struct.fields.isEmpty()) {
      typeBuilder.addFunction(generateToString(struct))
    }

    if (struct.fields.isEmpty()) {
      typeBuilder.addFunction(
          FunSpec.builder("hashCode")
              .addModifiers(KModifier.OVERRIDE)
              .returns(INT)
              .addStatement("return %S.hashCode()", structClassName.canonicalName)
              .build())

      typeBuilder.addFunction(
          FunSpec.builder("equals")
              .addModifiers(KModifier.OVERRIDE)
              .addParameter("other", Any::class.asTypeName().copy(nullable = true))
              .returns(BOOLEAN)
              .addStatement("return other is %T", structClassName)
              .build())
    }

    if (shouldImplementStruct) {
      typeBuilder
          .addSuperinterface(Struct::class)
          .addFunction(
              FunSpec.builder("write")
                  .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
                  .addParameter("protocol", Protocol::class)
                  .addStatement("%L.write(protocol, this)", nameAllocator.get(Tags.ADAPTER))
                  .build())
    }

    return typeBuilder
        .primaryConstructor(ctorBuilder.build())
        .addType(companionBuilder.build())
        .build()
  }

  internal fun generateSealedClass(schema: Schema, struct: StructType): TypeSpec {
    if (struct.fields.isEmpty()) {
      error("Cannot create an empty sealed class (type=${struct.name})")
    }

    val structClassName = ClassName(struct.kotlinNamespace, struct.name)
    val typeBuilder =
        TypeSpec.classBuilder(structClassName).apply {
          addModifiers(KModifier.SEALED)

          if (struct.isDeprecated) addAnnotation(makeDeprecated())
          if (struct.hasJavadoc) addKdoc("%L", struct.documentation)
        }

    var defaultValueTypeName: ClassName? = null
    val nameAllocator = nameAllocators[struct]
    for (field in struct.fields) {
      val name = nameAllocator.get(field)
      val sealedName = FieldNamingPolicy.PASCAL.apply(name)
      val type = field.type
      val typeName = type.typeName

      val propertySpec = PropertySpec.builder("value", typeName).initializer("value")

      val propConstructor = FunSpec.constructorBuilder().addParameter("value", typeName)

      val dataPropBuilder =
          TypeSpec.classBuilder(sealedName)
              .addModifiers(KModifier.DATA)
              .superclass(structClassName)
              .addProperty(propertySpec.build())
              .primaryConstructor(propConstructor.build())
              .tag(FieldIdMarker(field.id))

      val toStringBody = buildCodeBlock {
        add("return \"${struct.name}($name=")
        when {
          field.isRedacted -> add("<REDACTED>")
          !field.isObfuscated -> add("\$value")
          else -> {
            when (type) {
              is ListType -> {
                val elementName = type.elementType.name
                add(
                    "\${%T.summarizeCollection(%N, %S, %S)}",
                    ObfuscationUtil::class,
                    "value",
                    "list",
                    elementName)
              }
              is SetType -> {
                val elementName = type.elementType.name
                add(
                    "\${%T.summarizeCollection(%N, %S, %S)}",
                    ObfuscationUtil::class,
                    "value",
                    "set",
                    elementName)
              }
              is MapType -> {
                val keyName = type.keyType.name
                val valName = type.valueType.name
                add(
                    "\${%T.summarizeMap(%N, %S, %S)}",
                    ObfuscationUtil::class,
                    "value",
                    keyName,
                    valName)
              }
              else -> {
                add("\${%T.hash(%N)}", ObfuscationUtil::class, "value")
              }
            }
          }
        }
        add(")\"")
      }

      dataPropBuilder.addFunction(
          FunSpec.builder("toString")
              .addModifiers(KModifier.OVERRIDE)
              .returns(String::class)
              .addCode(toStringBody)
              .build())

      typeBuilder.addType(dataPropBuilder.build())

      if (field.defaultValue != null) {
        check(defaultValueTypeName == null) {
          "Error in thrifty-schema; unions may not have > 1 default value"
        }
        defaultValueTypeName = structClassName.nestedClass(sealedName)
      }
    }

    val adapterInterfaceTypeName = Adapter::class.asTypeName().parameterizedBy(struct.typeName)

    val adapterTypeName = ClassName(struct.kotlinNamespace, struct.name, "${struct.name}Adapter")

    typeBuilder.addType(generateAdapterForSealed(struct, adapterTypeName, adapterInterfaceTypeName))

    val companionBuilder = TypeSpec.companionObjectBuilder()
    companionBuilder.addProperty(
        PropertySpec.builder("ADAPTER", adapterInterfaceTypeName)
            .initializer("%T()", adapterTypeName)
            .jvmField()
            .build())

    // Unions may have a single default value.  If present, we'll add a DEFAULT
    // member
    struct.fields
        .singleOrNull { it.defaultValue != null }
        ?.let { field ->
          checkNotNull(defaultValueTypeName)

          val defaultValue = field.defaultValue!!
          val renderedValue = renderConstValue(schema, field.type, defaultValue)
          val propBuilder =
              PropertySpec.builder("DEFAULT", structClassName)
                  .jvmField()
                  .initializer("%T(%L)", defaultValueTypeName, renderedValue)

          companionBuilder.addProperty(propBuilder.build())
        }

    if (shouldImplementStruct) {
      typeBuilder
          .addSuperinterface(Struct::class)
          .addFunction(
              FunSpec.builder("write")
                  .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
                  .addParameter("protocol", Protocol::class)
                  .addStatement("%L.write(protocol, this)", nameAllocator.get(Tags.ADAPTER))
                  .build())
    }

    return typeBuilder.addType(companionBuilder.build()).build()
  }

  // endregion Structs

  // region Redaction/obfuscation

  internal fun generateToString(struct: StructType): FunSpec {

    val block = buildCodeBlock {
      add("return \"${struct.name}(")

      val nameAllocator = nameAllocators[struct]
      for ((ix, field) in struct.fields.withIndex()) {
        if (ix != 0) {
          add(",·")
        }
        val fieldName = nameAllocator.get(field)
        add("$fieldName=")

        when {
          field.isRedacted -> add("<REDACTED>")
          field.isObfuscated -> {
            val type = field.type
            when (type) {
              is ListType -> {
                val elementName = type.elementType.name
                add(
                    "\${%T.summarizeCollection($fieldName, %S, %S)}",
                    ObfuscationUtil::class,
                    "list",
                    elementName)
              }
              is SetType -> {
                val elementName = type.elementType.name
                add(
                    "\${%T.summarizeCollection($fieldName, %S, %S)}",
                    ObfuscationUtil::class,
                    "set",
                    elementName)
              }
              is MapType -> {
                val keyName = type.keyType.name
                val valName = type.valueType.name
                add(
                    "\${%T.summarizeMap($fieldName, %S, %S)}",
                    ObfuscationUtil::class,
                    keyName,
                    valName)
              }
              else -> {
                add("\${%T.hash($fieldName)}", ObfuscationUtil::class)
              }
            }
          }
          else -> add("\$$fieldName")
        }
      }

      add(")\"")
    }

    return FunSpec.builder("toString")
        .returns(String::class)
        .addModifiers(KModifier.OVERRIDE)
        .addCode(block)
        .build()
  }

  // endregion Redaction/obfuscation

  // region Adapters

  /** Generates an adapter for the given struct type. */
  internal fun generateAdapterFor(
      struct: StructType,
      adapterName: ClassName,
      adapterInterfaceName: TypeName
  ): TypeSpec {
    val adapter =
        TypeSpec.classBuilder(adapterName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(adapterInterfaceName)

    val reader =
        FunSpec.builder("read").apply {
          addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
          returns(struct.typeName)
          addParameter("protocol", Protocol::class)
        }

    val writer =
        FunSpec.builder("write")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("protocol", Protocol::class)
            .addParameter("struct", struct.typeName)

    // Writer first, b/c it is easier

    val nameAllocator = nameAllocators[struct]

    writer.addStatement("protocol.writeStructBegin(%S)", struct.name)
    for (field in struct.fields) {
      val name = nameAllocator.get(field)
      val fieldType = field.type

      if (!field.required) {
        writer.beginControlFlow("if (struct.%N != null)", name)
      }

      writer.addStatement(
          "protocol.writeFieldBegin(%S, %L, %T.%L)",
          field.name,
          field.id,
          TType::class,
          fieldType.typeCodeName)

      generateWriteCall(writer, "struct.$name", fieldType)

      writer.addStatement("protocol.writeFieldEnd()")

      if (!field.required) {
        writer.endControlFlow()
      }
    }
    writer.addStatement("protocol.writeFieldStop()")
    writer.addStatement("protocol.writeStructEnd()")

    // Reader next

    fun localFieldName(field: Field): String {
      return "_local_${field.name}"
    }

    for (field in struct.fields) {
      reader.addStatement("var %N: %T? = null", localFieldName(field), field.type.typeName)
    }

    reader.addStatement("protocol.readStructBegin()")
    reader.beginControlFlow("while (true)")

    reader.addStatement("val fieldMeta = protocol.readFieldBegin()")

    reader.beginControlFlow("if (fieldMeta.typeId == %T.STOP)", TType::class)
    reader.addStatement("break")
    reader.endControlFlow()

    if (struct.fields.isNotEmpty()) {
      reader.beginControlFlow("when (fieldMeta.fieldId.toInt())")

      for (field in struct.fields) {
        val name = nameAllocator.get(field)
        val fieldType = field.type

        reader.addCode {
          addStatement("${field.id}·->·{⇥")
          beginControlFlow("if (fieldMeta.typeId == %T.%L)", TType::class, fieldType.typeCodeName)

          val effectiveFailOnUnknownValues =
              if (fieldType.isEnum) {
                failOnUnknownEnumValues || field.required
              } else {
                failOnUnknownEnumValues
              }
          generateReadCall(
              this, name, fieldType, failOnUnknownEnumValues = effectiveFailOnUnknownValues)

          if (effectiveFailOnUnknownValues || !fieldType.isEnum) {
            addStatement("%N = $name", localFieldName(field))
          } else {
            beginControlFlow("$name?.let")
            addStatement("%N = it", localFieldName(field))
            endControlFlow()
          }
          nextControlFlow("else")
          addStatement("%T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
          endControlFlow()
          addStatement("⇤}")
        }
      }

      reader.addStatement("else·-> %T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
      reader.endControlFlow() // when (fieldMeta.fieldId.toInt())
    } else {
      reader.addStatement("%T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
    }

    reader.addStatement("protocol.readFieldEnd()")
    reader.endControlFlow() // while (true)
    reader.addStatement("protocol.readStructEnd()")

    val block = CodeBlock.builder()
    block.add("«return %T(", struct.typeName)

    val hasRequiredField = struct.fields.any { it.required }
    val newlinePerParam = (hasRequiredField && struct.fields.size > 1) || struct.fields.size > 2
    val separator = if (newlinePerParam) System.lineSeparator() else "·"

    if (newlinePerParam) {
      block.add(System.lineSeparator())
    }

    for ((ix, field) in struct.fields.withIndex()) {
      if (ix > 0) {
        block.add(",$separator")
      }

      block.add("%N = ", nameAllocator.get(field))
      if (field.required) {
        block.add(
            "checkNotNull(%N)·{·%S·}",
            localFieldName(field),
            "Required field '${nameAllocator.get(field)}' is missing")
      } else {
        block.add("%N", localFieldName(field))
      }
    }

    block.add(")»%L", System.lineSeparator())

    reader.addCode(block.build())

    return adapter.addFunction(reader.build()).addFunction(writer.build()).build()
  }

  /** Generates an adapter for the given struct type. */
  internal fun generateAdapterForSealed(
      struct: StructType,
      adapterName: ClassName,
      adapterInterfaceName: TypeName,
  ): TypeSpec {
    val adapter =
        TypeSpec.classBuilder(adapterName)
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(adapterInterfaceName)

    val reader =
        FunSpec.builder("read").apply {
          addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
          returns(struct.typeName)
          addParameter("protocol", Protocol::class)
        }

    val writer =
        FunSpec.builder("write")
            .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
            .addParameter("protocol", Protocol::class)
            .addParameter("struct", struct.typeName)

    // Writer

    val nameAllocator = nameAllocators[struct]

    writer.addStatement("protocol.writeStructBegin(%S)", struct.name)
    writer.beginControlFlow("when (struct)")
    for (field in struct.fields) {
      val name = nameAllocator.get(field)
      val fieldType = field.type
      val typeName = FieldNamingPolicy.PASCAL.apply(name)

      writer.beginControlFlow("is $typeName ->")

      writer.addStatement(
          "protocol.writeFieldBegin(%S, %L, %T.%L)",
          field.name,
          field.id,
          TType::class,
          fieldType.typeCodeName)

      generateWriteCall(writer, "struct.value", fieldType)

      writer.addStatement("protocol.writeFieldEnd()")

      writer.endControlFlow()
    }
    writer.endControlFlow()
    writer.addStatement("protocol.writeFieldStop()")
    writer.addStatement("protocol.writeStructEnd()")

    // Reader

    val localResult = nameAllocator[Tags.RESULT]
    reader.addStatement("protocol.readStructBegin()")
    val init =
        if (struct.fields.any { it.defaultValue != null }) {
          // TODO: This assumes that the client and server share the same belief about the default
          // value.  Is that sound?
          CodeBlock.of("DEFAULT")
        } else {
          CodeBlock.of("null")
        }

    reader.addStatement("var %N : ${struct.name}? = %L", localResult, init)
    reader.beginControlFlow("while (true)")

    reader.addStatement("val fieldMeta = protocol.readFieldBegin()")

    reader.beginControlFlow("if (fieldMeta.typeId == %T.STOP)", TType::class)
    reader.addStatement("break")
    reader.endControlFlow()

    if (struct.fields.isNotEmpty()) {
      reader.beginControlFlow("when (fieldMeta.fieldId.toInt())")

      for (field in struct.fields) {
        val name = nameAllocator.get(field)
        val fieldType = field.type
        val typeName = FieldNamingPolicy.PASCAL.apply(name)

        reader.addCode {
          addStatement("${field.id}·->·{⇥")
          beginControlFlow("if (fieldMeta.typeId == %T.%L)", TType::class, fieldType.typeCodeName)

          generateReadCall(this, name, fieldType)

          addStatement("%N = $typeName($name)", localResult)

          nextControlFlow("else")
          addStatement("%T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
          endControlFlow()
          addStatement("⇤}")
        }
      }

      reader.addStatement("else·->·%T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
      reader.endControlFlow() // when (fieldMeta.fieldId.toInt())
    } else {
      reader.addStatement("%T.skip(protocol, fieldMeta.typeId)", ProtocolUtil::class)
    }

    reader.addStatement("protocol.readFieldEnd()")
    reader.endControlFlow() // while (true)
    reader.addStatement("protocol.readStructEnd()")

    reader.addStatement("return %N ?: error(%S)", localResult, "unreadable")

    return adapter.addFunction(reader.build()).addFunction(writer.build()).build()
  }

  private fun generateWriteCall(writer: FunSpec.Builder, name: String, type: ThriftType) {

    // Assumptions:
    // - writer has a parameter "protocol" that is a Protocol

    fun generateRecursiveWrite(source: String, type: ThriftType, scope: Int) {
      type.accept(
          object : ThriftType.Visitor<Unit> {
            override fun visitVoid(voidType: BuiltinType) {
              error("Cannot write void, wat r u doing")
            }

            override fun visitBool(boolType: BuiltinType) {
              writer.addStatement("%N.writeBool(%L)", "protocol", source)
            }

            override fun visitByte(byteType: BuiltinType) {
              writer.addStatement("%N.writeByte(%L)", "protocol", source)
            }

            override fun visitI16(i16Type: BuiltinType) {
              writer.addStatement("%N.writeI16(%L)", "protocol", source)
            }

            override fun visitI32(i32Type: BuiltinType) {
              writer.addStatement("%N.writeI32(%L)", "protocol", source)
            }

            override fun visitI64(i64Type: BuiltinType) {
              writer.addStatement("%N.writeI64(%L)", "protocol", source)
            }

            override fun visitDouble(doubleType: BuiltinType) {
              writer.addStatement("%N.writeDouble(%L)", "protocol", source)
            }

            override fun visitString(stringType: BuiltinType) {
              writer.addStatement("%N.writeString(%L)", "protocol", source)
            }

            override fun visitBinary(binaryType: BuiltinType) {
              writer.addStatement("%N.writeBinary(%L)", "protocol", source)
            }

            override fun visitEnum(enumType: EnumType) {
              writer.addStatement("%N.writeI32(%L.value)", "protocol", source)
            }

            override fun visitList(listType: ListType) {
              val elementType = listType.elementType
              writer.addStatement(
                  "%N.writeListBegin(%T.%L, %L.size)",
                  "protocol",
                  TType::class,
                  elementType.typeCodeName,
                  source)

              val iterator = "item$scope"
              writer.beginControlFlow("for ($iterator in %L)", source)

              generateRecursiveWrite(iterator, elementType, scope + 1)

              writer.endControlFlow()

              writer.addStatement("%N.writeListEnd()", "protocol")
            }

            override fun visitSet(setType: SetType) {
              val elementType = setType.elementType
              writer.addStatement(
                  "%N.writeSetBegin(%T.%L, %L.size)",
                  "protocol",
                  TType::class,
                  elementType.typeCodeName,
                  source)

              val iterator = "item$scope"
              writer.beginControlFlow("for ($iterator in %L)", source)

              generateRecursiveWrite(iterator, elementType, scope + 1)

              writer.endControlFlow()

              writer.addStatement("%N.writeSetEnd()", "protocol")
            }

            override fun visitMap(mapType: MapType) {
              val keyType = mapType.keyType
              val valType = mapType.valueType

              writer.addStatement(
                  "%1N.writeMapBegin(%2T.%3L, %2T.%4L, %5L.size)",
                  "protocol",
                  TType::class,
                  keyType.typeCodeName,
                  valType.typeCodeName,
                  source)

              val keyIter = "key$scope"
              val valIter = "val$scope"
              writer.beginControlFlow("for (($keyIter, $valIter) in %L)", source)

              generateRecursiveWrite(keyIter, keyType, scope + 1)
              generateRecursiveWrite(valIter, valType, scope + 1)

              writer.endControlFlow()

              writer.addStatement("%N.writeMapEnd()", "protocol")
            }

            override fun visitStruct(structType: StructType) {
              writer.addStatement(
                  "%T.ADAPTER.write(%N, %L)", structType.typeName, "protocol", source)
            }

            override fun visitTypedef(typedefType: TypedefType) {
              typedefType.oldType.accept(this)
            }

            override fun visitService(serviceType: ServiceType) {
              error("Cannot write a service, wat r u doing")
            }
          })
    }

    generateRecursiveWrite(name, type, 0)
  }

  private fun generateReadCall(
      block: CodeBlock.Builder,
      name: String,
      type: ThriftType,
      scope: Int = 0,
      localNamePrefix: String = "",
      failOnUnknownEnumValues: Boolean = true
  ): CodeBlock.Builder {
    type.accept(
        object : ThriftType.Visitor<Unit> {
          override fun visitVoid(voidType: BuiltinType) {
            error("Cannot read a void, wat r u doing")
          }

          override fun visitBool(boolType: BuiltinType) {
            block.addStatement("val $name = protocol.readBool()")
          }

          override fun visitByte(byteType: BuiltinType) {
            block.addStatement("val $name = protocol.readByte()")
          }

          override fun visitI16(i16Type: BuiltinType) {
            block.addStatement("val $name = protocol.readI16()")
          }

          override fun visitI32(i32Type: BuiltinType) {
            block.addStatement("val $name = protocol.readI32()")
          }

          override fun visitI64(i64Type: BuiltinType) {
            block.addStatement("val $name = protocol.readI64()")
          }

          override fun visitDouble(doubleType: BuiltinType) {
            block.addStatement("val $name = protocol.readDouble()")
          }

          override fun visitString(stringType: BuiltinType) {
            block.addStatement("val $name = protocol.readString()")
          }

          override fun visitBinary(binaryType: BuiltinType) {
            block.addStatement("val $name = protocol.readBinary()")
          }

          override fun visitEnum(enumType: EnumType) {
            block.beginControlFlow("val $name = protocol.readI32().let")
            if (failOnUnknownEnumValues) {
              block.addStatement(
                  "%1T.findByValue(it) ?: " +
                      "throw %2T(%3T.PROTOCOL_ERROR, \"Unexpected·value·for·enum·type·%1T:·\$it\")",
                  enumType.typeName,
                  ThriftException::class,
                  ThriftException.Kind::class)
            } else {
              block.addStatement("%1T.findByValue(it)", enumType.typeName)
            }
            block.endControlFlow()
          }

          override fun visitList(listType: ListType) {
            val elementType = listType.elementType
            val listImplClassName = listClassName ?: ClassNames.ARRAY_LIST
            val listImplType = listImplClassName.parameterizedBy(elementType.typeName)
            val listMeta =
                if (localNamePrefix.isNotEmpty()) {
                  "${localNamePrefix}_list$scope"
                } else {
                  "list$scope"
                }
            block.addStatement("val $listMeta = protocol.readListBegin()")
            block.addStatement("val $name = %T($listMeta.size)", listImplType)

            block.beginControlFlow("for (i$scope in 0 until $listMeta.size)")
            generateReadCall(
                block = block,
                name = "item$scope",
                type = elementType,
                scope = scope + 1,
                localNamePrefix = "list$scope")
            block.addStatement("$name += item$scope")
            block.endControlFlow()

            block.addStatement("protocol.readListEnd()")
          }

          override fun visitSet(setType: SetType) {
            val elementType = setType.elementType
            val setImplClassName = setClassName ?: ClassNames.LINKED_HASH_SET
            val setImplType = setImplClassName.parameterizedBy(elementType.typeName)
            val setMeta =
                if (localNamePrefix.isNotEmpty()) {
                  "${localNamePrefix}_set$scope"
                } else {
                  "set$scope"
                }

            block.addStatement("val $setMeta = protocol.readSetBegin()")
            block.addStatement("val $name = %T($setMeta.size)", setImplType)

            block.beginControlFlow("for (i$scope in 0 until $setMeta.size)")
            generateReadCall(
                block = block,
                name = "item$scope",
                type = elementType,
                scope = scope + 1,
                localNamePrefix = "set$scope")
            block.addStatement("$name += item$scope")
            block.endControlFlow()

            block.addStatement("protocol.readSetEnd()")
          }

          override fun visitMap(mapType: MapType) {
            val keyType = mapType.keyType
            val valType = mapType.valueType
            val mapImplClassName = mapClassName ?: ClassNames.LINKED_HASH_MAP
            val mapImplType = mapImplClassName.parameterizedBy(keyType.typeName, valType.typeName)
            val mapMeta =
                if (localNamePrefix.isNotEmpty()) {
                  "${localNamePrefix}_map$scope"
                } else {
                  "map$scope"
                }

            block.addStatement("val $mapMeta = protocol.readMapBegin()")
            block.addStatement("val $name = %T($mapMeta.size)", mapImplType)

            block.beginControlFlow("for (i$scope in 0 until $mapMeta.size)")

            val keyName = "key$scope"
            val valName = "val$scope"

            generateReadCall(block, keyName, keyType, scope + 1, localNamePrefix = keyName)
            generateReadCall(block, valName, valType, scope + 1, localNamePrefix = valName)

            block.addStatement("$name[$keyName] = $valName")
            block.endControlFlow()

            block.addStatement("protocol.readMapEnd()")
          }

          override fun visitStruct(structType: StructType) {
            block.addStatement("val $name = %T.ADAPTER.read(protocol)", structType.typeName)
          }

          override fun visitTypedef(typedefType: TypedefType) {
            typedefType.trueType.accept(this)
          }

          override fun visitService(serviceType: ServiceType) {
            error("cannot read a service, wat r u doing")
          }
        })
    return block
  }

  // endregion Adapters

  // region Constants

  internal fun generateConstantProperty(
      schema: Schema,
      allocator: NameAllocator,
      constant: Constant
  ): PropertySpec {
    val type = constant.type
    val typeName = type.typeName
    val propName = allocator.newName(constant.name, constant)
    val propBuilder = PropertySpec.builder(propName, typeName)

    if (constant.isDeprecated) propBuilder.addAnnotation(makeDeprecated())
    if (constant.hasJavadoc) propBuilder.addKdoc("%L", constant.documentation)

    val canBeConst =
        type.accept(
            object : ThriftType.Visitor<Boolean> {
              // JVM primitives and strings can be constants
              override fun visitBool(boolType: BuiltinType) = true

              override fun visitByte(byteType: BuiltinType) = true

              override fun visitI16(i16Type: BuiltinType) = true

              override fun visitI32(i32Type: BuiltinType) = true

              override fun visitI64(i64Type: BuiltinType) = true

              override fun visitDouble(doubleType: BuiltinType) = true

              override fun visitString(stringType: BuiltinType) = true

              // Everything else, cannot...
              override fun visitBinary(binaryType: BuiltinType) = false

              override fun visitEnum(enumType: EnumType) = false

              override fun visitList(listType: ListType) = false

              override fun visitSet(setType: SetType) = false

              override fun visitMap(mapType: MapType) = false

              override fun visitStruct(structType: StructType) = false

              // ...except, possibly, a typedef
              override fun visitTypedef(typedefType: TypedefType) =
                  typedefType.trueType.accept(this)

              // These make no sense
              override fun visitService(serviceType: ServiceType): Boolean {
                error("Cannot have a const value of a service type")
              }

              override fun visitVoid(voidType: BuiltinType): Boolean {
                error("Cannot have a const value of void")
              }
            })

    if (canBeConst) {
      propBuilder.addModifiers(KModifier.CONST)
    }

    propBuilder.initializer(renderConstValue(schema, type, constant.value))

    return propBuilder.build()
  }

  internal fun renderConstValue(
      schema: Schema,
      thriftType: ThriftType,
      valueElement: ConstValueElement
  ): CodeBlock {
    fun recursivelyRenderConstValue(
        block: CodeBlock.Builder,
        type: ThriftType,
        value: ConstValueElement
    ) {
      type.accept(
          object : ThriftType.Visitor<Unit> {
            override fun visitVoid(voidType: BuiltinType) {
              error("Can't have void as a constant")
            }

            override fun visitBool(boolType: BuiltinType) {
              if (value is IdentifierValueElement && value.value in listOf("true", "false")) {
                block.add("%L", value.value)
              } else if (value is IntValueElement) {
                block.add("%L", value.value != 0L)
              } else {
                constOrError("Invalid boolean constant")
              }
            }

            override fun visitByte(byteType: BuiltinType) {
              if (value is IntValueElement) {
                block.add("%L", value.value)
              } else {
                constOrError("Invalid byte constant")
              }
            }

            override fun visitI16(i16Type: BuiltinType) {
              if (value is IntValueElement) {
                block.add("%L", value.value)
              } else {
                constOrError("Invalid I16 constant")
              }
            }

            override fun visitI32(i32Type: BuiltinType) {
              if (value is IntValueElement) {
                block.add("%L", value.value)
              } else {
                constOrError("Invalid I32 constant")
              }
            }

            override fun visitI64(i64Type: BuiltinType) {
              if (value is IntValueElement) {
                block.add("%L", value.value)
              } else {
                constOrError("Invalid I64 constant")
              }
            }

            override fun visitDouble(doubleType: BuiltinType) {
              when (value) {
                is IntValueElement -> block.add("%L.toDouble()", value.value)
                is DoubleValueElement -> block.add("%L", value.value)
                else -> constOrError("Invalid double constant")
              }
            }

            override fun visitString(stringType: BuiltinType) {
              if (value is LiteralValueElement) {
                block.add("%S", value.value)
              } else {
                constOrError("Invalid string constant")
              }
            }

            override fun visitBinary(binaryType: BuiltinType) {
              // TODO: Implement support for binary constants in the ANTLR grammar
              if (value is LiteralValueElement) {
                block.add("%T.decodeHex(%S)", ByteString::class, value.value)
              } else {
                constOrError("Invalid binary constant")
              }
            }

            override fun visitEnum(enumType: EnumType) {
              val member =
                  try {
                    when (value) {
                      // Enum references may or may not be scoped with their typename; either way,
                      // we must remove
                      // the type reference to get the member name on its own.
                      is IdentifierValueElement ->
                          enumType.findMemberByName(value.value.split(".").last())
                      is IntValueElement -> enumType.findMemberById(value.value.toInt())
                      else -> throw AssertionError("Value kind $value is not possibly an enum")
                    }
                  } catch (e: NoSuchElementException) {
                    null
                  }

              if (member != null) {
                block.add("${enumType.typeName}.%L", member.name)
              } else {
                constOrError("Invalid enum constant")
              }
            }

            override fun visitList(listType: ListType) {
              visitCollection(
                  listType.elementType,
                  listClassName,
                  "listOf",
                  "emptyList",
                  "Invalid list constant")
            }

            override fun visitSet(setType: SetType) {
              visitCollection(
                  setType.elementType, setClassName, "setOf", "emptySet", "Invalid set constant")
            }

            private fun visitCollection(
                elementType: ThriftType,
                customClassName: ClassName?,
                factoryMethod: String,
                emptyFactory: String,
                error: String
            ) {

              if (value is ListValueElement) {
                if (value.value.isEmpty()) {
                  block.add("%L()", emptyFactory)
                  return
                }

                if (customClassName != null) {
                  val concreteName = customClassName.parameterizedBy(elementType.typeName)
                  emitCustomCollection(elementType, value, concreteName)
                } else {
                  emitDefaultCollection(elementType, value, factoryMethod)
                }
              } else {
                constOrError(error)
              }
            }

            private fun emitCustomCollection(
                elementType: ThriftType,
                value: ListValueElement,
                collectionType: TypeName
            ) {
              block.add("%T(%L).apply·{⇥\n", collectionType, value.value.size)

              for (element in value.value) {
                block.add("add(")
                recursivelyRenderConstValue(block, elementType, element)
                block.add(")\n")
              }

              block.add("⇤}")
            }

            private fun emitDefaultCollection(
                elementType: ThriftType,
                value: ListValueElement,
                factoryMethod: String
            ) {
              block.add("$factoryMethod(⇥")

              for ((n, elementValue) in value.value.withIndex()) {
                if (n > 0) {
                  block.add(", ")
                }
                recursivelyRenderConstValue(block, elementType, elementValue)
              }

              block.add("⇤)")
            }

            override fun visitMap(mapType: MapType) {
              val keyType = mapType.keyType
              val valueType = mapType.valueType
              if (value is MapValueElement) {
                if (value.value.isEmpty()) {
                  block.add("emptyMap()")
                  return
                }

                val customType = mapClassName
                if (customType != null) {
                  val concreteType =
                      customType.parameterizedBy(keyType.typeName, valueType.typeName)
                  emitCustomMap(mapType, value, concreteType)
                } else {
                  emitDefaultMap(mapType, value)
                }
              } else {
                constOrError("Invalid map constant")
              }
            }

            private fun emitDefaultMap(mapType: MapType, value: MapValueElement) {
              val keyType = mapType.keyType
              val valueType = mapType.valueType

              block.add("mapOf(⇥")

              var n = 0
              for ((k, v) in value.value) {
                if (n++ > 0) {
                  block.add(",·")
                }
                recursivelyRenderConstValue(block, keyType, k)
                block.add("·to·")
                recursivelyRenderConstValue(block, valueType, v)
              }

              block.add("⇤)")
            }

            private fun emitCustomMap(
                mapType: MapType,
                value: MapValueElement,
                mapTypeName: TypeName
            ) {
              val keyType = mapType.keyType
              val valueType = mapType.valueType

              block.add("%T(%L).apply·{\n⇥", mapTypeName, value.value.size)

              for ((k, v) in value.value) {
                block.add("put(")
                recursivelyRenderConstValue(block, keyType, k)
                block.add(", ")
                recursivelyRenderConstValue(block, valueType, v)
                block.add(")\n")
              }

              block.add("⇤}")
            }

            override fun visitStruct(structType: StructType) {
              if (value !is MapValueElement) {
                constOrError("Invalid struct constant")
                return
              }

              val className = ClassName(structType.kotlinNamespace, structType.name)
              if (structType.fields.isEmpty()) {
                block.add("%T()", className)
                return
              }

              val names = nameAllocators[structType]
              val fieldValues = value.value.mapKeys { (it.key as LiteralValueElement).value }

              block.add("%T(\n⇥", className)

              for (field in structType.fields) {
                val fieldValue = fieldValues[field.name] ?: field.defaultValue
                if (fieldValue != null) {
                  block.add("%L = ", names[field])
                  recursivelyRenderConstValue(block, field.type, fieldValue)
                  block.add(",\n")
                } else {
                  check(!field.required) { "Missing value for required field '${field.name}'" }
                  block.add("%L = null,\n", names[field])
                }
              }

              block.add("⇤)")
            }

            override fun visitTypedef(typedefType: TypedefType) {
              typedefType.trueType.accept(this)
            }

            override fun visitService(serviceType: ServiceType) {
              throw AssertionError("Cannot have a const value of a service type, wat r u doing")
            }

            private fun constOrError(error: String) {
              val message = "$error: $value at ${value.location}"
              if (value !is IdentifierValueElement) {
                throw IllegalStateException(message)
              }

              val name: String
              val expectedProgram: String?

              val text = value.value
              val ix = text.indexOf(".")
              if (ix != -1) {
                expectedProgram = text.substring(0, ix)
                name = text.substring(ix + 1)
              } else {
                expectedProgram = null
                name = text
              }

              val c =
                  schema.constants.asSequence().firstOrNull {
                    it.name == name &&
                        it.type.trueType == type.trueType &&
                        (expectedProgram == null || expectedProgram == it.location.programName)
                  } ?: throw IllegalStateException(message)

              val packageName =
                  c.getNamespaceFor(NamespaceScope.KOTLIN, NamespaceScope.JAVA, NamespaceScope.ALL)
                      ?: throw IllegalStateException(
                          "No JVM namespace found for ${c.name} at ${c.location}")

              block.add("$packageName.$name")
            }
          })
    }

    return buildCodeBlock { recursivelyRenderConstValue(this, thriftType, valueElement) }
  }

  // endregion Constants

  // region Services

  internal fun generateProcessorImplementation(
      schema: Schema,
      serviceType: ServiceType,
      serviceInterface: TypeSpec
  ): TypeSpec {
    val serverTypeName = serviceType.name + "Processor"
    val type =
        TypeSpec.classBuilder(serverTypeName).apply {
          primaryConstructor(
              FunSpec.constructorBuilder()
                  .addParameter("handler", getServerTypeName(serviceType))
                  .addParameter(
                      ParameterSpec.builder("errorHandler", ErrorHandler::class)
                          .defaultValue("%T", DefaultErrorHandler::class)
                          .build())
                  .build())
          addProperty(
              PropertySpec.builder("errorHandler", ErrorHandler::class)
                  .initializer("errorHandler")
                  .addModifiers(KModifier.PRIVATE)
                  .build())
          addProperty(
              PropertySpec.builder("handler", getServerTypeName(serviceType))
                  .initializer("handler")
                  .addModifiers(KModifier.PRIVATE)
                  .build())
          addSuperinterface(Processor::class)
        }

    val spec =
        FunSpec.builder("process").apply {
          addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
          addParameter("input", Protocol::class)
          addParameter("output", Protocol::class)
        }

    spec.addCode {
      beginControlFlow(
          "input.%M { msg ->", MemberName("com.bendb.thrifty.service.server", "readMessage"))
      beginControlFlow("val call = when(msg.name)")
    }

    serviceInterface.funSpecs.zip(serviceType.methods).forEach { (interfaceFun, method) ->
      val argsDataClass = generateDataClass(schema, method.argsStruct)
      val resultDataClass =
          if (method.resultStruct.fields.isEmpty()) generateDataClass(schema, method.resultStruct)
          else generateSealedClass(schema, method.resultStruct)
      specsByNamespace.put(serviceType.kotlinNamespace, argsDataClass)
      specsByNamespace.put(serviceType.kotlinNamespace, resultDataClass)

      val call =
          buildServerCallType(serviceType, method, interfaceFun, argsDataClass, resultDataClass)
      type.addType(call)

      spec.addCode {
        addStatement(
            "%S -> %T()",
            method.name,
            ClassName(serviceType.kotlinNamespace, serverTypeName, callTypeName(method)))
      }
    }

    spec.addCode {
      beginControlFlow("else -> ")
      addStatement(
          """throw %T("%L")""",
          ClassNames.ILLEGAL_ARGUMENT_EXCEPTION,
          "Unknown method \${msg.name}")
      endControlFlow()
      endControlFlow()

      addStatement("call.process(msg, input, output, errorHandler, handler)")
      endControlFlow()
    }

    type.addFunction(spec.build())

    return type.build()
  }

  private fun buildServerCallType(
      serviceType: ServiceType,
      method: ServiceMethod,
      interfaceFun: FunSpec,
      argsDataClass: TypeSpec,
      resultDataClass: TypeSpec
  ): TypeSpec {
    val callName = callTypeName(method)
    val nameAllocator = nameAllocators[method]

    val argsTypeName = method.argsStruct.typeName
    val handlerTypeName = getServerTypeName(serviceType)
    val superType = ServerCall::class.asTypeName().parameterizedBy(argsTypeName, handlerTypeName)

    return TypeSpec.classBuilder(callName).run {
      addSuperinterface(superType)
      addModifiers(KModifier.PRIVATE)

      val getResultThrowsAnnotation =
          AnnotationSpec.builder(Throws::class)
              .apply { method.exceptions.forEach { this.addMember("%T::class", it.type.typeName) } }
              .build()

      addProperty(
          PropertySpec.builder("oneWay", Boolean::class, KModifier.OVERRIDE)
              .initializer("%L", method.oneWay)
              .build())

      val getResult =
          FunSpec.builder("getResult")
              .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
              .addAnnotation(getResultThrowsAnnotation)
              .returns(Struct::class)
              .addParameter("args", argsTypeName)
              .addParameter("handler", handlerTypeName)

      getResult.addCode {
        if (method.exceptions.isNotEmpty()) {
          wrapInThriftExceptionHandler(
              method.exceptions, resultDataClass, method.resultStruct.typeName) {
                callHandler(
                    argsDataClass,
                    resultDataClass,
                    interfaceFun,
                    method,
                    method.resultStruct.typeName)
              }
        } else {
          callHandler(
              argsDataClass, resultDataClass, interfaceFun, method, method.resultStruct.typeName)
        }
      }

      addFunction(getResult.build())

      // Add receive method
      val recv =
          FunSpec.builder(nameAllocator.get(Tags.RECEIVE))
              .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
              .returns(argsTypeName)
              .addParameter("protocol", Protocol::class)
              .addAnnotation(
                  AnnotationSpec.builder(Throws::class)
                      .addMember("%T::class", ClassNames.EXCEPTION)
                      .build())

      recv.addCode {
        generateReadCall(this, "res", method.argsStruct.trueType)
        addStatement("return res")
      }

      addFunction(recv.build())

      build()
    }
  }

  private fun CodeBlock.Builder.callHandler(
      argsDataClass: TypeSpec,
      resultDataClass: TypeSpec,
      interfaceFun: FunSpec,
      method: ServiceMethod,
      parentTypeName: TypeName
  ) {
    val names = argsDataClass.propertySpecs.map { it }
    val format = names.joinToString(separator = ", ") { "args.%N" }

    if (method.returnType == BuiltinType.VOID) {
      addStatement("handler.%N($format)", interfaceFun, *names.toTypedArray())
      addStatement("return %T", ServerCall::class.asTypeName().nestedClass("Empty"))
    } else {
      val concreteResultTypeName =
          resultDataClass.typeSpecs.single { it.tag<FieldIdMarker>()?.fieldId == 0 }.name
      addStatement(
          "return %T.%N(handler.%N($format))",
          parentTypeName,
          concreteResultTypeName,
          interfaceFun,
          *names.toTypedArray())
    }
  }

  private fun CodeBlock.Builder.wrapInThriftExceptionHandler(
      exceptions: List<Field>,
      resultDataClass: TypeSpec,
      parentTypeName: TypeName,
      block: CodeBlock.Builder.() -> Unit
  ) {
    beginControlFlow("try")
    block()
    endControlFlow()

    exceptions.forEach { field ->
      beginControlFlow("catch (e: %T)", field.type.typeName)
      val concreteResultTypeName =
          resultDataClass.typeSpecs.single { it.tag<FieldIdMarker>()?.fieldId == field.id }.name
      addStatement("return %T.%N(e)", parentTypeName, concreteResultTypeName)
      endControlFlow()
    }
  }

  private fun callTypeName(method: ServiceMethod) = method.name.capitalize() + "ServerCall"

  internal fun generateCoroServiceInterface(serviceType: ServiceType): TypeSpec {
    val type =
        TypeSpec.interfaceBuilder(serviceType.name).apply {
          if (serviceType.hasJavadoc) addKdoc("%L", serviceType.documentation)
          if (serviceType.isDeprecated) addAnnotation(makeDeprecated())

          serviceType.extendsService?.let { addSuperinterface(it.typeName) }
        }

    val allocator = nameAllocators[serviceType]
    for (method in serviceType.methods) {
      val name = allocator.get(method)
      val funSpec =
          FunSpec.builder(name).apply {
            addModifiers(KModifier.SUSPEND, KModifier.ABSTRACT)

            if (method.hasJavadoc) addKdoc("%L", method.documentation)
            if (method.isDeprecated) addAnnotation(makeDeprecated())

            val methodNameAllocator = nameAllocators[method]
            for (param in method.parameters) {
              val paramName = methodNameAllocator.get(param)
              val paramSpec =
                  ParameterSpec.builder(paramName, param.type.typeName).apply {
                    if (param.isDeprecated) addAnnotation(makeDeprecated())
                  }
              addParameter(paramSpec.build())
            }

            returns(method.returnType.typeName)
          }

      type.addFunction(funSpec.build())
    }

    return type.build()
  }

  internal fun generateCoroServiceImplementation(
      schema: Schema,
      serviceType: ServiceType,
      serviceInterface: TypeSpec
  ): TypeSpec {
    val type =
        TypeSpec.classBuilder(serviceType.name + "Client").apply {
          val baseType = serviceType.extendsService as? ServiceType
          val baseClassName =
              if (baseType != null) {
                ClassName(baseType.kotlinNamespace, baseType.name + "Client")
              } else {
                AsyncClientBase::class.asClassName()
              }

          superclass(baseClassName)
          addSuperinterface(ClassName(serviceType.kotlinNamespace, serviceType.name))

          // If any servces extend this, then this needs to be open.
          if (schema.services.any { it.extendsService == serviceType }) {
            addModifiers(KModifier.OPEN)
          }

          primaryConstructor(
              FunSpec.constructorBuilder()
                  .addParameter("protocol", Protocol::class)
                  .addParameter(
                      ParameterSpec.builder("dispatcher", ClassNames.COROUTINE_DISPATCHER)
                          .defaultValue(
                              "%T.%M",
                              ClassNames.DISPATCHERS,
                              MemberName("kotlinx.coroutines", "IO"))
                          .build())
                  .build())

          addSuperclassConstructorParameter("protocol", Protocol::class)
          addSuperclassConstructorParameter("dispatcher", ClassNames.COROUTINE_DISPATCHER)
        }

    for ((index, interfaceFun) in serviceInterface.funSpecs.withIndex()) {
      val method = serviceType.methods[index]
      val call = buildCallType(schema, method)
      val callName = nameAllocators[method].newName("call")
      val resultType = interfaceFun.returnType ?: UNIT

      val spec =
          FunSpec.builder(interfaceFun.name).apply {
            addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
            returns(resultType)

            for (param in interfaceFun.parameters) {
              addParameter(param)
            }

            addCode("«val %N = %N(", callName, call)
            for ((i, param) in interfaceFun.parameters.withIndex()) {
              if (i > 0) {
                addCode(", ")
              }
              addCode("%N", param.name)
            }
            addCode(")»\n")
            addStatement("return executeMethodCall(%N)", callName)
          }

      type.addType(call)
      type.addFunction(spec.build())
    }

    return type.build()
  }

  private fun buildCallType(schema: Schema, method: ServiceMethod): TypeSpec {
    val callName = method.name.capitalize() + "Call"
    val returnType = method.returnType
    val resultType = returnType.typeName
    val hasResult = resultType != UNIT
    val messageType = if (method.oneWay) "ONEWAY" else "CALL"
    val nameAllocator = nameAllocators[method]
    val superclassTypeName = MethodCall::class.asTypeName().parameterizedBy(resultType)

    return TypeSpec.classBuilder(callName).run {
      addModifiers(KModifier.PRIVATE)
      superclass(superclassTypeName)

      addSuperclassConstructorParameter("%S", method.name)
      addSuperclassConstructorParameter("%T.%L", TMessageType::class, messageType)

      // Add ctor
      val ctor = FunSpec.constructorBuilder()

      for (param in method.parameters) {
        val name = nameAllocator.get(param)
        val type = param.type
        val typeName = type.typeName

        val defaultValue = param.defaultValue
        val hasDefaultValue = defaultValue != null
        val propertyType =
            when {
              param.required -> typeName
              hasDefaultValue -> typeName
              else -> typeName.copy(nullable = true)
            }

        val ctorParam = ParameterSpec.builder(name, propertyType)
        if (defaultValue != null) {
          ctorParam.defaultValue(renderConstValue(schema, type, defaultValue))
        } else if (typeName.isNullable) {
          ctorParam.defaultValue("null")
        }

        ctor.addParameter(ctorParam.build())

        addProperty(
            PropertySpec.builder(name, typeName)
                .addModifiers(KModifier.PRIVATE)
                .initializer(name)
                .build())
      }

      primaryConstructor(ctor.build())

      // Add send method
      val send =
          FunSpec.builder(nameAllocator.get(Tags.SEND))
              .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
              .addAnnotation(
                  AnnotationSpec.builder(ClassNames.THROWS)
                      .addMember("%T::class", ClassNames.IO_EXCEPTION)
                      .build())
              .addParameter("protocol", Protocol::class)
              .addStatement("protocol.writeStructBegin(%S)", "args")

      for (param in method.parameters) {
        val name = nameAllocator.get(param)
        val type = param.type
        val typeCodeName = type.typeCodeName
        val optional = !param.required

        if (optional) {
          send.beginControlFlow("if (this.%N != null)", name)
        }

        send.addStatement(
            "protocol.writeFieldBegin(%S, %L, %T.%L)",
            param.name, // Make sure to send the Thrift name, not the allocated field name
            param.id,
            TType::class,
            typeCodeName)

        generateWriteCall(send, "this.$name", type)

        send.addStatement("protocol.writeFieldEnd()")

        if (optional) {
          send.endControlFlow()
        }
      }

      send.addStatement("protocol.writeFieldStop()")
      send.addStatement("protocol.writeStructEnd()")
      addFunction(send.build())

      // Add receive method
      val recv =
          FunSpec.builder(nameAllocator.get(Tags.RECEIVE))
              .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
              .addParameter("protocol", Protocol::class)
              .addParameter("metadata", MessageMetadata::class)
              .addAnnotation(
                  AnnotationSpec.builder(ClassNames.THROWS)
                      .addMember("%T::class", ClassNames.EXCEPTION)
                      .build())

      val maybeResultType = resultType.copy(nullable = true)
      val resultName = nameAllocator.get(Tags.RESULT)
      if (hasResult) {
        recv.returns(resultType.copy(nullable = false))
        recv.addStatement("var %N: %T = null", resultName, maybeResultType)
      }

      for (ex in method.exceptions) {
        recv.addStatement(
            "var %N: %T = null", nameAllocator.get(ex), ex.type.typeName.copy(nullable = true))
      }

      val fieldMeta = nameAllocator.get(Tags.FIELD)
      recv
          .addStatement("protocol.readStructBegin()")
          .beginControlFlow("while (true)")
          .addStatement("val %N = protocol.readFieldBegin()", fieldMeta)
          .beginControlFlow("if (%N.typeId == %T.STOP)", fieldMeta, TType::class)
          .addStatement("break")
          .endControlFlow() // if (fieldMeta.typeId == TType.STOP)

      val readsSomething = hasResult || method.exceptions.isNotEmpty()
      if (readsSomething) {
        recv.beginControlFlow("when (%N.fieldId.toInt())", fieldMeta)
      }

      if (hasResult) {
        recv.addCode {
          addStatement("0 -> {⇥")
          beginControlFlow(
              "if (%N.typeId == %T.%L)", fieldMeta, TType::class, returnType.typeCodeName)

          generateReadCall(this, "value", returnType)
          addStatement("%N = value", resultName)

          nextControlFlow("else")
          addStatement("%T.skip(protocol, %N.typeId)", ProtocolUtil::class, fieldMeta)
          endControlFlow()
          addStatement("⇤}")
        }
      }

      for (exn in method.exceptions) {
        val name = nameAllocator.get(exn)
        val type = exn.type
        recv.addCode {
          addStatement("${exn.id}·->·{⇥")
          beginControlFlow("if (%N.typeId == %T.%L)", fieldMeta, TType::class, type.typeCodeName)

          generateReadCall(this, "value", type)
          addStatement("$name = value")

          nextControlFlow("else")
          addStatement("%T.skip(protocol, %N.typeId)", ProtocolUtil::class, fieldMeta)
          endControlFlow()
          addStatement("⇤}")
        }
      }

      if (readsSomething) {
        recv.addStatement("else·-> %T.skip(protocol, %N.typeId)", ProtocolUtil::class, fieldMeta)
        recv.endControlFlow()
      } else {
        recv.addStatement("%T.skip(protocol, %N.typeId)", ProtocolUtil::class, fieldMeta)
      }

      recv.addStatement("protocol.readFieldEnd()")
      recv.endControlFlow() // while (true)
      recv.addStatement("protocol.readStructEnd()")

      for (exn in method.exceptions) {
        val name = nameAllocator.get(exn)
        recv.addStatement("if (%1N != null) throw %1N", name)
      }

      if (hasResult) {
        recv.addStatement(
            "return %N ?: throw %T(%T.%L, %S)",
            resultName,
            ThriftException::class,
            ThriftException.Kind::class,
            ThriftException.Kind.MISSING_RESULT.name,
            "Missing result")
      }

      // At this point, any exceptions have been thrown, and any results have been
      // returned.  If control reaches here, then the return type is Unit and there
      // is nothing to do.
      addFunction(recv.build())

      build()
    }
  }

  // endregion Services

  private inline fun FunSpec.Builder.addCode(fn: CodeBlock.Builder.() -> Unit) {
    addCode(buildCodeBlock(fn))
  }

  private inline fun buildCodeBlock(fn: CodeBlock.Builder.() -> Unit): CodeBlock {
    val block = CodeBlock.builder()
    block.fn()
    return block.build()
  }

  private fun makeFileSpecBuilder(packageName: String, fileName: String): FileSpec.Builder {
    return FileSpec.builder(packageName, fileName).apply {
      if (emitJvmName) {
        addAnnotation(
            AnnotationSpec.builder(JvmName::class)
                .useSiteTarget(FILE)
                .addMember("%S", fileName)
                .build())
      }

      if (emitFileComment) {
        addFileComment(FILE_COMMENT + DATE_FORMATTER.format(Instant.now()))
      }
    }
  }

  private fun makeDeprecated(): AnnotationSpec {
    return AnnotationSpec.builder(Deprecated::class)
        .addMember("message = %S", "Deprecated in source .thrift")
        .build()
  }

  private fun makeParcelable(): AnnotationSpec {
    return AnnotationSpec.builder(ClassName("kotlinx.android.parcel", "Parcelize")).build()
  }

  @Suppress("SameParameterValue")
  private fun suppressLint(toSuppress: String): AnnotationSpec {
    return AnnotationSpec.builder(ClassName("android.annotation", "SuppressLint"))
        .addMember("%S", toSuppress)
        .build()
  }

  private fun String.capitalize(): String {
    return this.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
  }

  companion object {
    private const val FILE_COMMENT =
        "Automatically generated by the Thrifty compiler; do not edit!\nGenerated on: "

    private val DATE_FORMATTER = DateTimeFormatter.ISO_INSTANT
  }
}
