import {
  EmptySerializersModule991ju6pz9b79 as EmptySerializersModule,
  Decoder23nde051s631g as Decoder,
  CompositeDecoder2tzm7wpwkr0og as CompositeDecoder,
  SerializerFactory1qv9hivitncuv as SerializerFactory,
  serializer1x79l67jvwntn as serializer,
  InlinePrimitiveDescriptor3i6ccn1a4fw94 as InlinePrimitiveDescriptor,
  SEALED_getInstance2brzxtciy6zxg as SEALED_getInstance,
  buildSerialDescriptor2873qmkp8r2ib as buildSerialDescriptor,
  KSerializerzf77vz1967fq as KSerializer,
  MapSerializer11kmegt3g5c1g as MapSerializer,
  SerialDescriptor2pelqekb5ic3a as SerialDescriptor,
  ListSerializer1hxuk9dx5n9du as ListSerializer,
  STRING_getInstance2md3nu8ka1da4 as STRING_getInstance,
  ENUM_getInstance3qjro8veu1n4s as ENUM_getInstance,
  PrimitiveSerialDescriptor3egfp53lutxj2 as PrimitiveSerialDescriptor,
  serializer2lw83vwvpnyms as serializer_0,
  get_isNullable36pbikm8xb7bz as get_isNullable,
  get_isInline5x26qrhi9qs6 as get_isInline,
  get_annotationshjxdbdcl8kmv as get_annotations,
  Encoderqvmrpqtq8hnu as Encoder,
  CompositeEncoderknecpkexzn3v as CompositeEncoder,
  ElementMarker33ojvsajwmzts as ElementMarker,
  SerializationExceptioneqrdve3ts2n9 as SerializationException,
  SerializationException_init_$Init$2xy772o85qyhy as SerializationException_init_$Init$,
  CLASS_getInstanceycyg9nehd7tt as CLASS_getInstance,
  LIST_getInstance31r89469rgwkw as LIST_getInstance,
  SealedClassSerializeriwipiibk55zc as SealedClassSerializer,
  jsonCachedSerialNameslxufy2gu43jt as jsonCachedSerialNames,
  ENUMlmq49cvwy4ow as ENUM,
  PrimitiveKindndgbuh6is7ze as PrimitiveKind,
  PolymorphicKindla9gurooefwb as PolymorphicKind,
  CONTEXTUAL_getInstance3iftz58z4hzud as CONTEXTUAL_getInstance,
  MAP_getInstance242ky2ofgg8p5 as MAP_getInstance,
  contextual3hpp1gupsu4al as contextual,
  SerializersModuleCollector3dddz14wd7brg as SerializersModuleCollector,
  AbstractDecoder35guh02ubh2hm as AbstractDecoder,
  AbstractPolymorphicSerializer1ccxwp48nfy58 as AbstractPolymorphicSerializer,
  DeserializationStrategy1z3z5pj9f7zc8 as DeserializationStrategy,
  findPolymorphicSerializer1nm87hvemahcj as findPolymorphicSerializer,
  MissingFieldException24tqif29emcmi as MissingFieldException,
  AbstractEncoder2gxtu3xmy3f8j as AbstractEncoder,
  OBJECT_getInstanceoefnxtom5aro as OBJECT_getInstance,
  findPolymorphicSerializerk638ixyjovk5 as findPolymorphicSerializer_0,
  SerializationStrategyh6ouydnm6hci as SerializationStrategy,
  serializer3ikrxnm8b29d6 as serializer_1,
  serializer36584sjyg5661 as serializer_2,
  serializer1q7c5q67ysppr as serializer_3,
  NamedValueDecoderzk26ztf92xbq as NamedValueDecoder,
  getContextualDescriptor2n1gf3b895yb8 as getContextualDescriptor,
} from './kotlinx-serialization-kotlinx-serialization-core.mjs';
import {
  protoOf180f3jzyo7rfj as protoOf,
  initMetadataForObject1cxne3s9w65el as initMetadataForObject,
  VOID7hggqo3abtya as VOID,
  Unit_instanceat14thlj150z as Unit_instance,
  initMetadataForClassbxx6q50dy2s7 as initMetadataForClass,
  toString1pkumu07cwy4m as toString,
  IllegalArgumentException_init_$Create$sfel9vrfa7w2 as IllegalArgumentException_init_$Create$,
  charSequenceLength3278n89t01tmv as charSequenceLength,
  charSequenceGet1vxk1y5n17t1z as charSequenceGet,
  _Char___init__impl__6a9atx367vrx0avsvbg as _Char___init__impl__6a9atx,
  equals2au1ep9vhcato as equals,
  toString30pk9tzaqopn as toString_0,
  Enum3alwj03lh1n41 as Enum,
  initMetadataForInterface1egvbzx539z91 as initMetadataForInterface,
  initMetadataForCompanion1wyw17z38v6ac as initMetadataForCompanion,
  StringBuilder_init_$Create$zzhi687lupah as StringBuilder_init_$Create$,
  hashCodeq5arwsb9dgti as hashCode,
  joinToString1cxrrlmo0chqs as joinToString,
  THROW_CCE2g6jy02ryeudk as THROW_CCE,
  KtMap140uvy3s5zad8 as KtMap,
  KtList3hktaavzmj137 as KtList,
  getKClassFromExpression3vpejubogshaw as getKClassFromExpression,
  getBooleanHashCode1bbj3u6b3v0a7 as getBooleanHashCode,
  getStringHashCode26igk1bx568vk as getStringHashCode,
  NumberFormatException_init_$Create$22b1m03hjdq4h as NumberFormatException_init_$Create$,
  numberRangeToNumber25vse2rgp6rs8 as numberRangeToNumber,
  ClosedRangehokgr73im9z3 as ClosedRange,
  isInterface3d6p8outrmvmk as isInterface,
  contains2c50nlxg7en7o as contains,
  toDouble1kn912gjoizjp as toDouble,
  StringCompanionObject_instance1kavzb69ik84p as StringCompanionObject_instance,
  noWhenBranchMatchedException2a6r7ubxgky5j as noWhenBranchMatchedException,
  toLongOrNullutqivezb0wx1 as toLongOrNull,
  toULongOrNullojoyxi0i9tgj as toULongOrNull,
  ULong3f9k7s38t3rfp as ULong,
  Companion_getInstance2r8lbbjwejmb0 as Companion_getInstance,
  _ULong___get_data__impl__fggpzb77rs34yu2akv as _ULong___get_data__impl__fggpzb,
  toDoubleOrNullkxwozihadygj as toDoubleOrNull,
  toBooleanStrictOrNull2j0md398tkvbj as toBooleanStrictOrNull,
  IllegalStateException_init_$Create$1nm9y6jd3c9iy as IllegalStateException_init_$Create$,
  lazy2hsh8ze7j6ikd as lazy,
  KProperty1ca4yb4wlo496 as KProperty1,
  getPropertyCallableRef1ajb9in178r5r as getPropertyCallableRef,
  toLongw1zpgk99d84b as toLong,
  _UInt___init__impl__l7qpdl2x2ja0qtrydmg as _UInt___init__impl__l7qpdl,
  UInt__toString_impl_dbgl21zkdo3skdvztx as UInt__toString_impl_dbgl21,
  _ULong___init__impl__c78o9k1neui78jb9mlg as _ULong___init__impl__c78o9k,
  ULong__toString_impl_f9au7k260qy52yi92xo as ULong__toString_impl_f9au7k,
  _UByte___init__impl__g9hnc43i8t7wecfr79w as _UByte___init__impl__g9hnc4,
  UByte__toString_impl_v72jg3ruswpcjtpyk as UByte__toString_impl_v72jg,
  _UShort___init__impl__jigrne18f2wrm3i4dpy as _UShort___init__impl__jigrne,
  UShort__toString_impl_edaoee32kikxrf4p0yn as UShort__toString_impl_edaoee,
  captureStack1fzi4aczwc4hg as captureStack,
  charSequenceSubSequence1iwpdba8s3jc7 as charSequenceSubSequence,
  coerceAtLeast2bkz8m9ik7hep as coerceAtLeast,
  coerceAtMost322komnqp70ag as coerceAtMost,
  LinkedHashMap_init_$Create$2gfxg59gogbfz as LinkedHashMap_init_$Create$,
  ArrayList_init_$Create$2rimpp4x9tjd3 as ArrayList_init_$Create$,
  singleOrNullrknfaxokm1sl as singleOrNull,
  emptyMapr06gerzljqtm as emptyMap,
  getValue48kllevslyh6 as getValue,
  fillArrayVali8eppxapiek4 as fillArrayVal,
  copyOf2ng0t8oizk6it as copyOf,
  copyOf3rutauicler23 as copyOf_0,
  DeepRecursiveFunction3r49v8igsve1g as DeepRecursiveFunction,
  invoke246lvi6tzooz1 as invoke,
  CoroutineImpl2sn3kjnwmfr10 as CoroutineImpl,
  DeepRecursiveScope1pqaydvh4vdcu as DeepRecursiveScope,
  Unitkvevlwgzwiuc as Unit,
  get_COROUTINE_SUSPENDED3ujt3p13qm4iy as get_COROUTINE_SUSPENDED,
  initMetadataForLambda3af3he42mmnh as initMetadataForLambda,
  initMetadataForCoroutine1i7lbatuf5bnt as initMetadataForCoroutine,
  getKClass1s3j9wy1cofik as getKClass,
  ensureNotNull1e947j3ixpazm as ensureNotNull,
  substringBefore3n7kj60w69hju as substringBefore,
  removeSuffix3d61x5lsuvuho as removeSuffix,
  substringAfter1hku067gwr5ve as substringAfter,
  contains3ue2qo8xhmpf1 as contains_0,
  plus17rl43at52ays as plus,
  IllegalArgumentException2asla15b5jaob as IllegalArgumentException,
  isFinite1tx0gn65nl9tj as isFinite,
  isFinite2t9l5a275mxm6 as isFinite_0,
  toUInt21lx0mz8wkp7c as toUInt,
  _UInt___get_data__impl__f0vqqw1wfqgh301wggv as _UInt___get_data__impl__f0vqqw,
  toULong266mnyksbttkw as toULong,
  toUByteh6p4wmqswkrs as toUByte,
  _UByte___get_data__impl__jof9qr1jhbnu489etgz as _UByte___get_data__impl__jof9qr,
  toUShort7yqspfnhrot4 as toUShort,
  _UShort___get_data__impl__g02453buibbzrpmeid as _UShort___get_data__impl__g0245,
  objectCreate1ve4bgxiu4x98 as objectCreate,
  toString14fd7g605f91p as toString_1,
  Companion_getInstance316n37o3eume as Companion_getInstance_0,
  Companion_getInstance16xypb6k4bny3 as Companion_getInstance_1,
  Companion_getInstance3956vha054dts as Companion_getInstance_2,
  setOf45ia9pnfhe90 as setOf,
  Char__toInt_impl_vasixd17daqndsyg0ov as Char__toInt_impl_vasixd,
  numberToChar93r9buh19yek as numberToChar,
  equals2v6cggk171b6e as equals_0,
  startsWith26w8qjqapeeq6 as startsWith,
  toByte4i43936u611k as toByte,
  toShort36kaw0zjdq3ex as toShort,
  single29ec4rh52687r as single,
  Char19o2r8palgjof as Char,
  emptySetcxexqki71qfa as emptySet,
  plus1ogy4liedzq5j as plus_0,
  toInt2q8uldh7sc951 as toInt,
  toList3jhuyej2anx2q as toList,
  enumEntries20mr21zbe3az4 as enumEntries,
  last1vo29oleiqj36 as last,
  removeLast3759euu1xvfa3 as removeLast,
  lastIndexOf2d52xhix5ymjr as lastIndexOf,
  Long2qws0ah9gnpki as Long,
  Char__minus_impl_a2frrh15zl6plytfjgc as Char__minus_impl_a2frrh,
  numberToLong1a4cndvg6c52s as numberToLong,
  charArray2ujmm1qusno00 as charArray,
  indexOfwa4w6635jewi as indexOf,
  indexOf1xbs558u7wr52 as indexOf_0,
  StringBuilder_init_$Create$1ckbfpj83l95o as StringBuilder_init_$Create$_0,
  HashMap_init_$Create$3l32t8ezheps1 as HashMap_init_$Create$,
} from './kotlin-kotlin-stdlib.mjs';
//region block: imports
var imul = Math.imul;
//endregion
//region block: pre-declaration
initMetadataForClass(Json, 'Json');
initMetadataForObject(Default, 'Default', VOID, Json);
initMetadataForClass(JsonBuilder, 'JsonBuilder');
initMetadataForClass(JsonImpl, 'JsonImpl', VOID, Json);
initMetadataForClass(JsonClassDiscriminator, 'JsonClassDiscriminator');
initMetadataForClass(JsonNames, 'JsonNames');
initMetadataForClass(JsonConfiguration, 'JsonConfiguration');
initMetadataForClass(ClassDiscriminatorMode, 'ClassDiscriminatorMode', VOID, Enum);
initMetadataForInterface(JsonDecoder, 'JsonDecoder', VOID, VOID, [Decoder, CompositeDecoder]);
initMetadataForCompanion(Companion);
initMetadataForClass(JsonElement, 'JsonElement', VOID, VOID, VOID, VOID, VOID, {0: JsonElementSerializer_getInstance});
initMetadataForClass(JsonObject, 'JsonObject', VOID, JsonElement, [JsonElement, KtMap], VOID, VOID, {0: JsonObjectSerializer_getInstance});
initMetadataForCompanion(Companion_0);
initMetadataForCompanion(Companion_1);
initMetadataForClass(JsonArray, 'JsonArray', VOID, JsonElement, [JsonElement, KtList], VOID, VOID, {0: JsonArraySerializer_getInstance});
initMetadataForCompanion(Companion_2);
initMetadataForClass(JsonPrimitive, 'JsonPrimitive', VOID, JsonElement, VOID, VOID, VOID, {0: JsonPrimitiveSerializer_getInstance});
initMetadataForObject(JsonNull, 'JsonNull', VOID, JsonPrimitive, [JsonPrimitive, SerializerFactory], VOID, VOID, {0: JsonNullSerializer_getInstance});
initMetadataForClass(JsonLiteral, 'JsonLiteral', VOID, JsonPrimitive);
initMetadataForObject(JsonElementSerializer, 'JsonElementSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonObjectDescriptor, 'JsonObjectDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonObjectSerializer, 'JsonObjectSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonArrayDescriptor, 'JsonArrayDescriptor', VOID, VOID, [SerialDescriptor]);
initMetadataForObject(JsonArraySerializer, 'JsonArraySerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonPrimitiveSerializer, 'JsonPrimitiveSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonNullSerializer, 'JsonNullSerializer', VOID, VOID, [KSerializer]);
initMetadataForObject(JsonLiteralSerializer, 'JsonLiteralSerializer', VOID, VOID, [KSerializer]);
initMetadataForClass(defer$1, VOID, VOID, VOID, [SerialDescriptor]);
initMetadataForInterface(JsonEncoder, 'JsonEncoder', VOID, VOID, [Encoder, CompositeEncoder]);
initMetadataForClass(Composer, 'Composer');
initMetadataForClass(ComposerForUnsignedNumbers, 'ComposerForUnsignedNumbers', VOID, Composer);
initMetadataForClass(ComposerForUnquotedLiterals, 'ComposerForUnquotedLiterals', VOID, Composer);
initMetadataForClass(ComposerWithPrettyPrint, 'ComposerWithPrettyPrint', VOID, Composer);
initMetadataForClass(JsonElementMarker, 'JsonElementMarker');
initMetadataForClass(JsonException, 'JsonException', VOID, SerializationException);
initMetadataForClass(JsonEncodingException, 'JsonEncodingException', VOID, JsonException);
initMetadataForClass(JsonDecodingException, 'JsonDecodingException', VOID, JsonException);
initMetadataForObject(Tombstone, 'Tombstone');
initMetadataForClass(JsonPath, 'JsonPath', JsonPath);
initMetadataForLambda(JsonTreeReader$readDeepRecursive$slambda, CoroutineImpl, VOID, [2]);
initMetadataForCoroutine($readObjectCOROUTINE$0, CoroutineImpl);
initMetadataForClass(JsonTreeReader, 'JsonTreeReader', VOID, VOID, VOID, [0]);
initMetadataForClass(PolymorphismValidator, 'PolymorphismValidator', VOID, VOID, [SerializersModuleCollector]);
initMetadataForClass(Key, 'Key', Key);
initMetadataForClass(DescriptorSchemaCache, 'DescriptorSchemaCache', DescriptorSchemaCache);
initMetadataForClass(DiscriminatorHolder, 'DiscriminatorHolder');
initMetadataForClass(StreamingJsonDecoder, 'StreamingJsonDecoder', VOID, AbstractDecoder, [JsonDecoder, AbstractDecoder]);
initMetadataForClass(JsonDecoderForUnsignedTypes, 'JsonDecoderForUnsignedTypes', VOID, AbstractDecoder);
initMetadataForClass(StreamingJsonEncoder, 'StreamingJsonEncoder', VOID, AbstractEncoder, [JsonEncoder, AbstractEncoder]);
initMetadataForClass(AbstractJsonTreeDecoder, 'AbstractJsonTreeDecoder', VOID, NamedValueDecoder, [NamedValueDecoder, JsonDecoder]);
initMetadataForClass(JsonTreeDecoder, 'JsonTreeDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonTreeListDecoder, 'JsonTreeListDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonPrimitiveDecoder, 'JsonPrimitiveDecoder', VOID, AbstractJsonTreeDecoder);
initMetadataForClass(JsonTreeMapDecoder, 'JsonTreeMapDecoder', VOID, JsonTreeDecoder);
initMetadataForClass(WriteMode, 'WriteMode', VOID, Enum);
initMetadataForClass(AbstractJsonLexer, 'AbstractJsonLexer');
initMetadataForObject(CharMappings, 'CharMappings');
initMetadataForClass(StringJsonLexer, 'StringJsonLexer', VOID, AbstractJsonLexer);
initMetadataForClass(StringJsonLexerWithComments, 'StringJsonLexerWithComments', VOID, StringJsonLexer);
initMetadataForClass(JsonToStringWriter, 'JsonToStringWriter', JsonToStringWriter);
//endregion
function Default() {
  Default_instance = this;
  Json.call(this, new JsonConfiguration(), EmptySerializersModule());
}
var Default_instance;
function Default_getInstance() {
  if (Default_instance == null)
    new Default();
  return Default_instance;
}
function Json(configuration, serializersModule) {
  Default_getInstance();
  this.h13_1 = configuration;
  this.i13_1 = serializersModule;
  this.j13_1 = new DescriptorSchemaCache();
}
protoOf(Json).sn = function () {
  return this.i13_1;
};
protoOf(Json).k13 = function (serializer, value) {
  var result = new JsonToStringWriter();
  try {
    encodeByWriter(this, result, serializer, value);
    return result.toString();
  }finally {
    result.n13();
  }
};
protoOf(Json).l13 = function (deserializer, string) {
  var lexer = StringJsonLexer_0(this, string);
  var input = new StreamingJsonDecoder(this, WriteMode_OBJ_getInstance(), lexer, deserializer.gk(), null);
  var result = input.cn(deserializer);
  lexer.a14();
  return result;
};
function Json_0(from, builderAction) {
  from = from === VOID ? Default_getInstance() : from;
  var builder = new JsonBuilder(from);
  builderAction(builder);
  var conf = builder.t14();
  return new JsonImpl(conf, builder.s14_1);
}
function JsonBuilder(json) {
  this.b14_1 = json.h13_1.u14_1;
  this.c14_1 = json.h13_1.z14_1;
  this.d14_1 = json.h13_1.v14_1;
  this.e14_1 = json.h13_1.w14_1;
  this.f14_1 = json.h13_1.y14_1;
  this.g14_1 = json.h13_1.a15_1;
  this.h14_1 = json.h13_1.b15_1;
  this.i14_1 = json.h13_1.d15_1;
  this.j14_1 = json.h13_1.k15_1;
  this.k14_1 = json.h13_1.f15_1;
  this.l14_1 = json.h13_1.g15_1;
  this.m14_1 = json.h13_1.h15_1;
  this.n14_1 = json.h13_1.i15_1;
  this.o14_1 = json.h13_1.j15_1;
  this.p14_1 = json.h13_1.e15_1;
  this.q14_1 = json.h13_1.x14_1;
  this.r14_1 = json.h13_1.c15_1;
  this.s14_1 = json.sn();
}
protoOf(JsonBuilder).t14 = function () {
  if (this.r14_1) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(this.i14_1 === 'type')) {
      // Inline function 'kotlinx.serialization.json.JsonBuilder.build.<anonymous>' call
      var message = 'Class discriminator should not be specified when array polymorphism is specified';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!this.j14_1.equals(ClassDiscriminatorMode_POLYMORPHIC_getInstance())) {
      // Inline function 'kotlinx.serialization.json.JsonBuilder.build.<anonymous>' call
      var message_0 = 'useArrayPolymorphism option can only be used if classDiscriminatorMode in a default POLYMORPHIC state.';
      throw IllegalArgumentException_init_$Create$(toString(message_0));
    }
  }
  if (!this.f14_1) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(this.g14_1 === '    ')) {
      // Inline function 'kotlinx.serialization.json.JsonBuilder.build.<anonymous>' call
      var message_1 = 'Indent should not be specified when default printing mode is used';
      throw IllegalArgumentException_init_$Create$(toString(message_1));
    }
  } else if (!(this.g14_1 === '    ')) {
    var tmp$ret$4;
    $l$block: {
      // Inline function 'kotlin.text.all' call
      var indexedObject = this.g14_1;
      var inductionVariable = 0;
      while (inductionVariable < charSequenceLength(indexedObject)) {
        var element = charSequenceGet(indexedObject, inductionVariable);
        inductionVariable = inductionVariable + 1 | 0;
        // Inline function 'kotlinx.serialization.json.JsonBuilder.build.<anonymous>' call
        if (!(element === _Char___init__impl__6a9atx(32) || element === _Char___init__impl__6a9atx(9) || element === _Char___init__impl__6a9atx(13) || element === _Char___init__impl__6a9atx(10))) {
          tmp$ret$4 = false;
          break $l$block;
        }
      }
      tmp$ret$4 = true;
    }
    var allWhitespaces = tmp$ret$4;
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!allWhitespaces) {
      // Inline function 'kotlinx.serialization.json.JsonBuilder.build.<anonymous>' call
      var message_2 = 'Only whitespace, tab, newline and carriage return are allowed as pretty print symbols. Had ' + this.g14_1;
      throw IllegalArgumentException_init_$Create$(toString(message_2));
    }
  }
  return new JsonConfiguration(this.b14_1, this.d14_1, this.e14_1, this.q14_1, this.f14_1, this.c14_1, this.g14_1, this.h14_1, this.r14_1, this.i14_1, this.p14_1, this.k14_1, this.l14_1, this.m14_1, this.n14_1, this.o14_1, this.j14_1);
};
function validateConfiguration($this) {
  if (equals($this.sn(), EmptySerializersModule()))
    return Unit_instance;
  var collector = new PolymorphismValidator($this.h13_1.c15_1, $this.h13_1.d15_1);
  $this.sn().p12(collector);
}
function JsonImpl(configuration, module_0) {
  Json.call(this, configuration, module_0);
  validateConfiguration(this);
}
function JsonClassDiscriminator() {
}
function JsonNames() {
}
function JsonConfiguration(encodeDefaults, ignoreUnknownKeys, isLenient, allowStructuredMapKeys, prettyPrint, explicitNulls, prettyPrintIndent, coerceInputValues, useArrayPolymorphism, classDiscriminator, allowSpecialFloatingPointValues, useAlternativeNames, namingStrategy, decodeEnumsCaseInsensitive, allowTrailingComma, allowComments, classDiscriminatorMode) {
  encodeDefaults = encodeDefaults === VOID ? false : encodeDefaults;
  ignoreUnknownKeys = ignoreUnknownKeys === VOID ? false : ignoreUnknownKeys;
  isLenient = isLenient === VOID ? false : isLenient;
  allowStructuredMapKeys = allowStructuredMapKeys === VOID ? false : allowStructuredMapKeys;
  prettyPrint = prettyPrint === VOID ? false : prettyPrint;
  explicitNulls = explicitNulls === VOID ? true : explicitNulls;
  prettyPrintIndent = prettyPrintIndent === VOID ? '    ' : prettyPrintIndent;
  coerceInputValues = coerceInputValues === VOID ? false : coerceInputValues;
  useArrayPolymorphism = useArrayPolymorphism === VOID ? false : useArrayPolymorphism;
  classDiscriminator = classDiscriminator === VOID ? 'type' : classDiscriminator;
  allowSpecialFloatingPointValues = allowSpecialFloatingPointValues === VOID ? false : allowSpecialFloatingPointValues;
  useAlternativeNames = useAlternativeNames === VOID ? true : useAlternativeNames;
  namingStrategy = namingStrategy === VOID ? null : namingStrategy;
  decodeEnumsCaseInsensitive = decodeEnumsCaseInsensitive === VOID ? false : decodeEnumsCaseInsensitive;
  allowTrailingComma = allowTrailingComma === VOID ? false : allowTrailingComma;
  allowComments = allowComments === VOID ? false : allowComments;
  classDiscriminatorMode = classDiscriminatorMode === VOID ? ClassDiscriminatorMode_POLYMORPHIC_getInstance() : classDiscriminatorMode;
  this.u14_1 = encodeDefaults;
  this.v14_1 = ignoreUnknownKeys;
  this.w14_1 = isLenient;
  this.x14_1 = allowStructuredMapKeys;
  this.y14_1 = prettyPrint;
  this.z14_1 = explicitNulls;
  this.a15_1 = prettyPrintIndent;
  this.b15_1 = coerceInputValues;
  this.c15_1 = useArrayPolymorphism;
  this.d15_1 = classDiscriminator;
  this.e15_1 = allowSpecialFloatingPointValues;
  this.f15_1 = useAlternativeNames;
  this.g15_1 = namingStrategy;
  this.h15_1 = decodeEnumsCaseInsensitive;
  this.i15_1 = allowTrailingComma;
  this.j15_1 = allowComments;
  this.k15_1 = classDiscriminatorMode;
}
protoOf(JsonConfiguration).toString = function () {
  return 'JsonConfiguration(encodeDefaults=' + this.u14_1 + ', ignoreUnknownKeys=' + this.v14_1 + ', isLenient=' + this.w14_1 + ', ' + ('allowStructuredMapKeys=' + this.x14_1 + ', prettyPrint=' + this.y14_1 + ', explicitNulls=' + this.z14_1 + ', ') + ("prettyPrintIndent='" + this.a15_1 + "', coerceInputValues=" + this.b15_1 + ', useArrayPolymorphism=' + this.c15_1 + ', ') + ("classDiscriminator='" + this.d15_1 + "', allowSpecialFloatingPointValues=" + this.e15_1 + ', ') + ('useAlternativeNames=' + this.f15_1 + ', namingStrategy=' + toString_0(this.g15_1) + ', decodeEnumsCaseInsensitive=' + this.h15_1 + ', ') + ('allowTrailingComma=' + this.i15_1 + ', allowComments=' + this.j15_1 + ', classDiscriminatorMode=' + this.k15_1.toString() + ')');
};
var ClassDiscriminatorMode_NONE_instance;
var ClassDiscriminatorMode_ALL_JSON_OBJECTS_instance;
var ClassDiscriminatorMode_POLYMORPHIC_instance;
var ClassDiscriminatorMode_entriesInitialized;
function ClassDiscriminatorMode_initEntries() {
  if (ClassDiscriminatorMode_entriesInitialized)
    return Unit_instance;
  ClassDiscriminatorMode_entriesInitialized = true;
  ClassDiscriminatorMode_NONE_instance = new ClassDiscriminatorMode('NONE', 0);
  ClassDiscriminatorMode_ALL_JSON_OBJECTS_instance = new ClassDiscriminatorMode('ALL_JSON_OBJECTS', 1);
  ClassDiscriminatorMode_POLYMORPHIC_instance = new ClassDiscriminatorMode('POLYMORPHIC', 2);
}
function ClassDiscriminatorMode(name, ordinal) {
  Enum.call(this, name, ordinal);
}
function ClassDiscriminatorMode_NONE_getInstance() {
  ClassDiscriminatorMode_initEntries();
  return ClassDiscriminatorMode_NONE_instance;
}
function ClassDiscriminatorMode_POLYMORPHIC_getInstance() {
  ClassDiscriminatorMode_initEntries();
  return ClassDiscriminatorMode_POLYMORPHIC_instance;
}
function JsonDecoder() {
}
function get_jsonUnquotedLiteralDescriptor() {
  _init_properties_JsonElement_kt__7cbdc2();
  return jsonUnquotedLiteralDescriptor;
}
var jsonUnquotedLiteralDescriptor;
function Companion() {
}
var Companion_instance;
function Companion_getInstance_3() {
  return Companion_instance;
}
function JsonObject$toString$lambda(_destruct__k2r9zo) {
  // Inline function 'kotlin.collections.component1' call
  var k = _destruct__k2r9zo.y1();
  // Inline function 'kotlin.collections.component2' call
  var v = _destruct__k2r9zo.z1();
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.contracts.contract' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  // Inline function 'kotlin.contracts.contract' call
  // Inline function 'kotlinx.serialization.json.JsonObject.toString.<anonymous>.<anonymous>' call
  printQuoted(this_0, k);
  this_0.b8(_Char___init__impl__6a9atx(58));
  this_0.z7(v);
  return this_0.toString();
}
function JsonObject(content) {
  JsonElement.call(this);
  this.n15_1 = content;
}
protoOf(JsonObject).equals = function (other) {
  return equals(this.n15_1, other);
};
protoOf(JsonObject).hashCode = function () {
  return hashCode(this.n15_1);
};
protoOf(JsonObject).toString = function () {
  var tmp = this.n15_1.f2();
  return joinToString(tmp, ',', '{', '}', VOID, VOID, JsonObject$toString$lambda);
};
protoOf(JsonObject).o15 = function (key) {
  return this.n15_1.a2(key);
};
protoOf(JsonObject).a2 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return false;
  return this.o15((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).fc = function (key) {
  return this.n15_1.c2(key);
};
protoOf(JsonObject).c2 = function (key) {
  if (!(!(key == null) ? typeof key === 'string' : false))
    return null;
  return this.fc((!(key == null) ? typeof key === 'string' : false) ? key : THROW_CCE());
};
protoOf(JsonObject).n = function () {
  return this.n15_1.n();
};
protoOf(JsonObject).f2 = function () {
  return this.n15_1.f2();
};
protoOf(JsonObject).d2 = function () {
  return this.n15_1.d2();
};
protoOf(JsonObject).m = function () {
  return this.n15_1.m();
};
protoOf(JsonObject).e2 = function () {
  return this.n15_1.e2();
};
function Companion_0() {
}
var Companion_instance_0;
function Companion_getInstance_4() {
  return Companion_instance_0;
}
function JsonElement() {
}
function Companion_1() {
}
var Companion_instance_1;
function Companion_getInstance_5() {
  return Companion_instance_1;
}
function JsonArray(content) {
  JsonElement.call(this);
  this.p15_1 = content;
}
protoOf(JsonArray).equals = function (other) {
  return equals(this.p15_1, other);
};
protoOf(JsonArray).hashCode = function () {
  return hashCode(this.p15_1);
};
protoOf(JsonArray).toString = function () {
  return joinToString(this.p15_1, ',', '[', ']');
};
protoOf(JsonArray).q15 = function (element) {
  return this.p15_1.u(element);
};
protoOf(JsonArray).u = function (element) {
  if (!(element instanceof JsonElement))
    return false;
  return this.q15(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).r15 = function (elements) {
  return this.p15_1.w1(elements);
};
protoOf(JsonArray).w1 = function (elements) {
  return this.r15(elements);
};
protoOf(JsonArray).o = function (index) {
  return this.p15_1.o(index);
};
protoOf(JsonArray).s15 = function (element) {
  return this.p15_1.x(element);
};
protoOf(JsonArray).x = function (element) {
  if (!(element instanceof JsonElement))
    return -1;
  return this.s15(element instanceof JsonElement ? element : THROW_CCE());
};
protoOf(JsonArray).n = function () {
  return this.p15_1.n();
};
protoOf(JsonArray).j = function () {
  return this.p15_1.j();
};
protoOf(JsonArray).p = function (index) {
  return this.p15_1.p(index);
};
protoOf(JsonArray).x1 = function (fromIndex, toIndex) {
  return this.p15_1.x1(fromIndex, toIndex);
};
protoOf(JsonArray).m = function () {
  return this.p15_1.m();
};
function Companion_2() {
}
var Companion_instance_2;
function Companion_getInstance_6() {
  return Companion_instance_2;
}
function JsonPrimitive() {
  JsonElement.call(this);
}
protoOf(JsonPrimitive).toString = function () {
  return this.t15();
};
function JsonNull() {
  JsonNull_instance = this;
  JsonPrimitive.call(this);
  this.u15_1 = 'null';
}
protoOf(JsonNull).t15 = function () {
  return this.u15_1;
};
protoOf(JsonNull).v15 = function () {
  return JsonNullSerializer_getInstance();
};
protoOf(JsonNull).xu = function (typeParamsSerializers) {
  return this.v15();
};
var JsonNull_instance;
function JsonNull_getInstance() {
  if (JsonNull_instance == null)
    new JsonNull();
  return JsonNull_instance;
}
function JsonPrimitive_0(value) {
  _init_properties_JsonElement_kt__7cbdc2();
  if (value == null)
    return JsonNull_getInstance();
  return new JsonLiteral(value, true);
}
function JsonLiteral(body, isString, coerceToInlineType) {
  coerceToInlineType = coerceToInlineType === VOID ? null : coerceToInlineType;
  JsonPrimitive.call(this);
  this.w15_1 = isString;
  this.x15_1 = coerceToInlineType;
  this.y15_1 = toString(body);
  if (!(this.x15_1 == null)) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!this.x15_1.ml()) {
      // Inline function 'kotlin.require.<anonymous>' call
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$(toString(message));
    }
  }
}
protoOf(JsonLiteral).t15 = function () {
  return this.y15_1;
};
protoOf(JsonLiteral).toString = function () {
  var tmp;
  if (this.w15_1) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$();
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlinx.serialization.json.JsonLiteral.toString.<anonymous>' call
    printQuoted(this_0, this.y15_1);
    tmp = this_0.toString();
  } else {
    tmp = this.y15_1;
  }
  return tmp;
};
protoOf(JsonLiteral).equals = function (other) {
  if (this === other)
    return true;
  if (other == null || !getKClassFromExpression(this).equals(getKClassFromExpression(other)))
    return false;
  if (!(other instanceof JsonLiteral))
    THROW_CCE();
  if (!(this.w15_1 === other.w15_1))
    return false;
  if (!(this.y15_1 === other.y15_1))
    return false;
  return true;
};
protoOf(JsonLiteral).hashCode = function () {
  var result = getBooleanHashCode(this.w15_1);
  result = imul(31, result) + getStringHashCode(this.y15_1) | 0;
  return result;
};
function get_booleanOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toBooleanStrictOrNull_0(_this__u8e3s4.t15());
}
function get_int(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    // Inline function 'kotlinx.serialization.json.<get-int>.<anonymous>' call
    tmp = (new StringJsonLexer(_this__u8e3s4.t15())).z15();
  } catch ($p) {
    var tmp_0;
    if ($p instanceof JsonDecodingException) {
      var e = $p;
      throw NumberFormatException_init_$Create$(e.message);
    } else {
      throw $p;
    }
  }
  var result = tmp;
  // Inline function 'kotlin.ranges.contains' call
  var this_0 = numberRangeToNumber(-2147483648, 2147483647);
  if (!contains(isInterface(this_0, ClosedRange) ? this_0 : THROW_CCE(), result))
    throw NumberFormatException_init_$Create$(_this__u8e3s4.t15() + ' is not an Int');
  return result.g1();
}
function get_long(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlinx.serialization.json.mapExceptions' call
  var tmp;
  try {
    // Inline function 'kotlinx.serialization.json.<get-long>.<anonymous>' call
    tmp = (new StringJsonLexer(_this__u8e3s4.t15())).z15();
  } catch ($p) {
    var tmp_0;
    if ($p instanceof JsonDecodingException) {
      var e = $p;
      throw NumberFormatException_init_$Create$(e.message);
    } else {
      throw $p;
    }
  }
  return tmp;
}
function get_float(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  // Inline function 'kotlin.text.toFloat' call
  var this_0 = _this__u8e3s4.t15();
  // Inline function 'kotlin.js.unsafeCast' call
  // Inline function 'kotlin.js.asDynamic' call
  return toDouble(this_0);
}
function get_double(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  return toDouble(_this__u8e3s4.t15());
}
function get_contentOrNull(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp;
  if (_this__u8e3s4 instanceof JsonNull) {
    tmp = null;
  } else {
    tmp = _this__u8e3s4.t15();
  }
  return tmp;
}
function get_jsonPrimitive(_this__u8e3s4) {
  _init_properties_JsonElement_kt__7cbdc2();
  var tmp0_elvis_lhs = _this__u8e3s4 instanceof JsonPrimitive ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    error(_this__u8e3s4, 'JsonPrimitive');
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function error(_this__u8e3s4, element) {
  _init_properties_JsonElement_kt__7cbdc2();
  throw IllegalArgumentException_init_$Create$('Element ' + toString(getKClassFromExpression(_this__u8e3s4)) + ' is not a ' + element);
}
var properties_initialized_JsonElement_kt_abxy8s;
function _init_properties_JsonElement_kt__7cbdc2() {
  if (!properties_initialized_JsonElement_kt_abxy8s) {
    properties_initialized_JsonElement_kt_abxy8s = true;
    jsonUnquotedLiteralDescriptor = InlinePrimitiveDescriptor('kotlinx.serialization.json.JsonUnquotedLiteral', serializer(StringCompanionObject_instance));
  }
}
function JsonElementSerializer$descriptor$lambda($this$buildSerialDescriptor) {
  $this$buildSerialDescriptor.rk('JsonPrimitive', defer(JsonElementSerializer$descriptor$lambda$lambda));
  $this$buildSerialDescriptor.rk('JsonNull', defer(JsonElementSerializer$descriptor$lambda$lambda_0));
  $this$buildSerialDescriptor.rk('JsonLiteral', defer(JsonElementSerializer$descriptor$lambda$lambda_1));
  $this$buildSerialDescriptor.rk('JsonObject', defer(JsonElementSerializer$descriptor$lambda$lambda_2));
  $this$buildSerialDescriptor.rk('JsonArray', defer(JsonElementSerializer$descriptor$lambda$lambda_3));
  return Unit_instance;
}
function JsonElementSerializer$descriptor$lambda$lambda() {
  return JsonPrimitiveSerializer_getInstance().a16_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_0() {
  return JsonNullSerializer_getInstance().b16_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_1() {
  return JsonLiteralSerializer_getInstance().c16_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_2() {
  return JsonObjectSerializer_getInstance().d16_1;
}
function JsonElementSerializer$descriptor$lambda$lambda_3() {
  return JsonArraySerializer_getInstance().e16_1;
}
function JsonElementSerializer() {
  JsonElementSerializer_instance = this;
  var tmp = this;
  var tmp_0 = SEALED_getInstance();
  tmp.f16_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonElement', tmp_0, [], JsonElementSerializer$descriptor$lambda);
}
protoOf(JsonElementSerializer).gk = function () {
  return this.f16_1;
};
protoOf(JsonElementSerializer).g16 = function (encoder, value) {
  verify(encoder);
  if (value instanceof JsonPrimitive) {
    encoder.uo(JsonPrimitiveSerializer_getInstance(), value);
  } else {
    if (value instanceof JsonObject) {
      encoder.uo(JsonObjectSerializer_getInstance(), value);
    } else {
      if (value instanceof JsonArray) {
        encoder.uo(JsonArraySerializer_getInstance(), value);
      } else {
        noWhenBranchMatchedException();
      }
    }
  }
};
protoOf(JsonElementSerializer).hk = function (encoder, value) {
  return this.g16(encoder, value instanceof JsonElement ? value : THROW_CCE());
};
protoOf(JsonElementSerializer).ik = function (decoder) {
  var input = asJsonDecoder(decoder);
  return input.m15();
};
var JsonElementSerializer_instance;
function JsonElementSerializer_getInstance() {
  if (JsonElementSerializer_instance == null)
    new JsonElementSerializer();
  return JsonElementSerializer_instance;
}
function JsonObjectDescriptor() {
  JsonObjectDescriptor_instance = this;
  this.h16_1 = MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).gk();
  this.i16_1 = 'kotlinx.serialization.json.JsonObject';
}
protoOf(JsonObjectDescriptor).kl = function () {
  return this.i16_1;
};
protoOf(JsonObjectDescriptor).pl = function (index) {
  return this.h16_1.pl(index);
};
protoOf(JsonObjectDescriptor).ql = function (name) {
  return this.h16_1.ql(name);
};
protoOf(JsonObjectDescriptor).rl = function (index) {
  return this.h16_1.rl(index);
};
protoOf(JsonObjectDescriptor).sl = function (index) {
  return this.h16_1.sl(index);
};
protoOf(JsonObjectDescriptor).tl = function (index) {
  return this.h16_1.tl(index);
};
protoOf(JsonObjectDescriptor).ll = function () {
  return this.h16_1.ll();
};
protoOf(JsonObjectDescriptor).gl = function () {
  return this.h16_1.gl();
};
protoOf(JsonObjectDescriptor).ml = function () {
  return this.h16_1.ml();
};
protoOf(JsonObjectDescriptor).nl = function () {
  return this.h16_1.nl();
};
protoOf(JsonObjectDescriptor).ol = function () {
  return this.h16_1.ol();
};
var JsonObjectDescriptor_instance;
function JsonObjectDescriptor_getInstance() {
  if (JsonObjectDescriptor_instance == null)
    new JsonObjectDescriptor();
  return JsonObjectDescriptor_instance;
}
function JsonObjectSerializer() {
  JsonObjectSerializer_instance = this;
  this.d16_1 = JsonObjectDescriptor_getInstance();
}
protoOf(JsonObjectSerializer).gk = function () {
  return this.d16_1;
};
protoOf(JsonObjectSerializer).j16 = function (encoder, value) {
  verify(encoder);
  MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).hk(encoder, value);
};
protoOf(JsonObjectSerializer).hk = function (encoder, value) {
  return this.j16(encoder, value instanceof JsonObject ? value : THROW_CCE());
};
protoOf(JsonObjectSerializer).ik = function (decoder) {
  verify_0(decoder);
  return new JsonObject(MapSerializer(serializer(StringCompanionObject_instance), JsonElementSerializer_getInstance()).ik(decoder));
};
var JsonObjectSerializer_instance;
function JsonObjectSerializer_getInstance() {
  if (JsonObjectSerializer_instance == null)
    new JsonObjectSerializer();
  return JsonObjectSerializer_instance;
}
function JsonArrayDescriptor() {
  JsonArrayDescriptor_instance = this;
  this.k16_1 = ListSerializer(JsonElementSerializer_getInstance()).gk();
  this.l16_1 = 'kotlinx.serialization.json.JsonArray';
}
protoOf(JsonArrayDescriptor).kl = function () {
  return this.l16_1;
};
protoOf(JsonArrayDescriptor).pl = function (index) {
  return this.k16_1.pl(index);
};
protoOf(JsonArrayDescriptor).ql = function (name) {
  return this.k16_1.ql(name);
};
protoOf(JsonArrayDescriptor).rl = function (index) {
  return this.k16_1.rl(index);
};
protoOf(JsonArrayDescriptor).sl = function (index) {
  return this.k16_1.sl(index);
};
protoOf(JsonArrayDescriptor).tl = function (index) {
  return this.k16_1.tl(index);
};
protoOf(JsonArrayDescriptor).ll = function () {
  return this.k16_1.ll();
};
protoOf(JsonArrayDescriptor).gl = function () {
  return this.k16_1.gl();
};
protoOf(JsonArrayDescriptor).ml = function () {
  return this.k16_1.ml();
};
protoOf(JsonArrayDescriptor).nl = function () {
  return this.k16_1.nl();
};
protoOf(JsonArrayDescriptor).ol = function () {
  return this.k16_1.ol();
};
var JsonArrayDescriptor_instance;
function JsonArrayDescriptor_getInstance() {
  if (JsonArrayDescriptor_instance == null)
    new JsonArrayDescriptor();
  return JsonArrayDescriptor_instance;
}
function JsonArraySerializer() {
  JsonArraySerializer_instance = this;
  this.e16_1 = JsonArrayDescriptor_getInstance();
}
protoOf(JsonArraySerializer).gk = function () {
  return this.e16_1;
};
protoOf(JsonArraySerializer).m16 = function (encoder, value) {
  verify(encoder);
  ListSerializer(JsonElementSerializer_getInstance()).hk(encoder, value);
};
protoOf(JsonArraySerializer).hk = function (encoder, value) {
  return this.m16(encoder, value instanceof JsonArray ? value : THROW_CCE());
};
protoOf(JsonArraySerializer).ik = function (decoder) {
  verify_0(decoder);
  return new JsonArray(ListSerializer(JsonElementSerializer_getInstance()).ik(decoder));
};
var JsonArraySerializer_instance;
function JsonArraySerializer_getInstance() {
  if (JsonArraySerializer_instance == null)
    new JsonArraySerializer();
  return JsonArraySerializer_instance;
}
function JsonPrimitiveSerializer() {
  JsonPrimitiveSerializer_instance = this;
  this.a16_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonPrimitive', STRING_getInstance(), []);
}
protoOf(JsonPrimitiveSerializer).gk = function () {
  return this.a16_1;
};
protoOf(JsonPrimitiveSerializer).n16 = function (encoder, value) {
  verify(encoder);
  var tmp;
  if (value instanceof JsonNull) {
    encoder.uo(JsonNullSerializer_getInstance(), JsonNull_getInstance());
    tmp = Unit_instance;
  } else {
    var tmp_0 = JsonLiteralSerializer_getInstance();
    encoder.uo(tmp_0, value instanceof JsonLiteral ? value : THROW_CCE());
    tmp = Unit_instance;
  }
  return tmp;
};
protoOf(JsonPrimitiveSerializer).hk = function (encoder, value) {
  return this.n16(encoder, value instanceof JsonPrimitive ? value : THROW_CCE());
};
protoOf(JsonPrimitiveSerializer).ik = function (decoder) {
  var result = asJsonDecoder(decoder).m15();
  if (!(result instanceof JsonPrimitive))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonPrimitive, had ' + toString(getKClassFromExpression(result)), toString(result));
  return result;
};
var JsonPrimitiveSerializer_instance;
function JsonPrimitiveSerializer_getInstance() {
  if (JsonPrimitiveSerializer_instance == null)
    new JsonPrimitiveSerializer();
  return JsonPrimitiveSerializer_instance;
}
function defer(deferred) {
  return new defer$1(deferred);
}
function JsonNullSerializer() {
  JsonNullSerializer_instance = this;
  this.b16_1 = buildSerialDescriptor('kotlinx.serialization.json.JsonNull', ENUM_getInstance(), []);
}
protoOf(JsonNullSerializer).gk = function () {
  return this.b16_1;
};
protoOf(JsonNullSerializer).o16 = function (encoder, value) {
  verify(encoder);
  encoder.yn();
};
protoOf(JsonNullSerializer).hk = function (encoder, value) {
  return this.o16(encoder, value instanceof JsonNull ? value : THROW_CCE());
};
protoOf(JsonNullSerializer).ik = function (decoder) {
  verify_0(decoder);
  if (decoder.pm()) {
    throw new JsonDecodingException("Expected 'null' literal");
  }
  decoder.qm();
  return JsonNull_getInstance();
};
var JsonNullSerializer_instance;
function JsonNullSerializer_getInstance() {
  if (JsonNullSerializer_instance == null)
    new JsonNullSerializer();
  return JsonNullSerializer_instance;
}
function JsonLiteralSerializer() {
  JsonLiteralSerializer_instance = this;
  this.c16_1 = PrimitiveSerialDescriptor('kotlinx.serialization.json.JsonLiteral', STRING_getInstance());
}
protoOf(JsonLiteralSerializer).gk = function () {
  return this.c16_1;
};
protoOf(JsonLiteralSerializer).p16 = function (encoder, value) {
  verify(encoder);
  if (value.w15_1) {
    return encoder.ho(value.y15_1);
  }
  if (!(value.x15_1 == null)) {
    return encoder.io(value.x15_1).ho(value.y15_1);
  }
  var tmp0_safe_receiver = toLongOrNull(value.y15_1);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    return encoder.do(tmp0_safe_receiver);
  }
  var tmp1_safe_receiver = toULongOrNull(value.y15_1);
  var tmp = tmp1_safe_receiver;
  if ((tmp == null ? null : new ULong(tmp)) == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    var tmp_0 = tmp1_safe_receiver;
    // Inline function 'kotlin.contracts.contract' call
    var it = (tmp_0 == null ? null : new ULong(tmp_0)).mj_1;
    var tmp_1 = encoder.io(serializer_0(Companion_getInstance()).gk());
    // Inline function 'kotlin.ULong.toLong' call
    var tmp$ret$1 = _ULong___get_data__impl__fggpzb(it);
    tmp_1.do(tmp$ret$1);
    return Unit_instance;
  }
  var tmp2_safe_receiver = toDoubleOrNull(value.y15_1);
  if (tmp2_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    return encoder.fo(tmp2_safe_receiver);
  }
  var tmp3_safe_receiver = toBooleanStrictOrNull(value.y15_1);
  if (tmp3_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    return encoder.zn(tmp3_safe_receiver);
  }
  encoder.ho(value.y15_1);
};
protoOf(JsonLiteralSerializer).hk = function (encoder, value) {
  return this.p16(encoder, value instanceof JsonLiteral ? value : THROW_CCE());
};
protoOf(JsonLiteralSerializer).ik = function (decoder) {
  var result = asJsonDecoder(decoder).m15();
  if (!(result instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, 'Unexpected JSON element, expected JsonLiteral, had ' + toString(getKClassFromExpression(result)), toString(result));
  return result;
};
var JsonLiteralSerializer_instance;
function JsonLiteralSerializer_getInstance() {
  if (JsonLiteralSerializer_instance == null)
    new JsonLiteralSerializer();
  return JsonLiteralSerializer_instance;
}
function verify(encoder) {
  asJsonEncoder(encoder);
}
function asJsonDecoder(_this__u8e3s4) {
  var tmp0_elvis_lhs = isInterface(_this__u8e3s4, JsonDecoder) ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalStateException_init_$Create$('This serializer can be used only with Json format.' + ('Expected Decoder to be JsonDecoder, got ' + toString(getKClassFromExpression(_this__u8e3s4))));
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function verify_0(decoder) {
  asJsonDecoder(decoder);
}
function asJsonEncoder(_this__u8e3s4) {
  var tmp0_elvis_lhs = isInterface(_this__u8e3s4, JsonEncoder) ? _this__u8e3s4 : null;
  var tmp;
  if (tmp0_elvis_lhs == null) {
    throw IllegalStateException_init_$Create$('This serializer can be used only with Json format.' + ('Expected Encoder to be JsonEncoder, got ' + toString(getKClassFromExpression(_this__u8e3s4))));
  } else {
    tmp = tmp0_elvis_lhs;
  }
  return tmp;
}
function _get_original__l7ku1m($this) {
  // Inline function 'kotlin.getValue' call
  var this_0 = $this.q16_1;
  original$factory();
  return this_0.z1();
}
function defer$1($deferred) {
  this.q16_1 = lazy($deferred);
}
protoOf(defer$1).kl = function () {
  return _get_original__l7ku1m(this).kl();
};
protoOf(defer$1).ll = function () {
  return _get_original__l7ku1m(this).ll();
};
protoOf(defer$1).nl = function () {
  return _get_original__l7ku1m(this).nl();
};
protoOf(defer$1).pl = function (index) {
  return _get_original__l7ku1m(this).pl(index);
};
protoOf(defer$1).ql = function (name) {
  return _get_original__l7ku1m(this).ql(name);
};
protoOf(defer$1).rl = function (index) {
  return _get_original__l7ku1m(this).rl(index);
};
protoOf(defer$1).sl = function (index) {
  return _get_original__l7ku1m(this).sl(index);
};
protoOf(defer$1).tl = function (index) {
  return _get_original__l7ku1m(this).tl(index);
};
function original$factory() {
  return getPropertyCallableRef('original', 1, KProperty1, function (receiver) {
    return _get_original__l7ku1m(receiver);
  }, null);
}
function JsonEncoder() {
}
function Composer(writer) {
  this.r16_1 = writer;
  this.s16_1 = true;
}
protoOf(Composer).t16 = function () {
  this.s16_1 = true;
};
protoOf(Composer).u16 = function () {
  return Unit_instance;
};
protoOf(Composer).v16 = function () {
  this.s16_1 = false;
};
protoOf(Composer).w16 = function () {
  this.s16_1 = false;
};
protoOf(Composer).x16 = function () {
  return Unit_instance;
};
protoOf(Composer).y16 = function (v) {
  return this.r16_1.z16(v);
};
protoOf(Composer).a17 = function (v) {
  return this.r16_1.b17(v);
};
protoOf(Composer).c17 = function (v) {
  return this.r16_1.b17(v.toString());
};
protoOf(Composer).d17 = function (v) {
  return this.r16_1.b17(v.toString());
};
protoOf(Composer).e17 = function (v) {
  return this.r16_1.f17(toLong(v));
};
protoOf(Composer).g17 = function (v) {
  return this.r16_1.f17(toLong(v));
};
protoOf(Composer).h17 = function (v) {
  return this.r16_1.f17(toLong(v));
};
protoOf(Composer).i17 = function (v) {
  return this.r16_1.f17(v);
};
protoOf(Composer).j17 = function (v) {
  return this.r16_1.b17(v.toString());
};
protoOf(Composer).k17 = function (value) {
  return this.r16_1.l17(value);
};
function Composer_0(sb, json) {
  return json.h13_1.y14_1 ? new ComposerWithPrettyPrint(sb, json) : new Composer(sb);
}
function ComposerForUnsignedNumbers(writer, forceQuoting) {
  Composer.call(this, writer);
  this.o17_1 = forceQuoting;
}
protoOf(ComposerForUnsignedNumbers).h17 = function (v) {
  if (this.o17_1) {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$0 = _UInt___init__impl__l7qpdl(v);
    this.k17(UInt__toString_impl_dbgl21(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUInt' call
    var tmp$ret$1 = _UInt___init__impl__l7qpdl(v);
    this.a17(UInt__toString_impl_dbgl21(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).i17 = function (v) {
  if (this.o17_1) {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$0 = _ULong___init__impl__c78o9k(v);
    this.k17(ULong__toString_impl_f9au7k(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toULong' call
    var tmp$ret$1 = _ULong___init__impl__c78o9k(v);
    this.a17(ULong__toString_impl_f9au7k(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).e17 = function (v) {
  if (this.o17_1) {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$0 = _UByte___init__impl__g9hnc4(v);
    this.k17(UByte__toString_impl_v72jg(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUByte' call
    var tmp$ret$1 = _UByte___init__impl__g9hnc4(v);
    this.a17(UByte__toString_impl_v72jg(tmp$ret$1));
  }
};
protoOf(ComposerForUnsignedNumbers).g17 = function (v) {
  if (this.o17_1) {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$0 = _UShort___init__impl__jigrne(v);
    this.k17(UShort__toString_impl_edaoee(tmp$ret$0));
  } else {
    // Inline function 'kotlin.toUShort' call
    var tmp$ret$1 = _UShort___init__impl__jigrne(v);
    this.a17(UShort__toString_impl_edaoee(tmp$ret$1));
  }
};
function ComposerForUnquotedLiterals(writer, forceQuoting) {
  Composer.call(this, writer);
  this.r17_1 = forceQuoting;
}
protoOf(ComposerForUnquotedLiterals).k17 = function (value) {
  if (this.r17_1) {
    protoOf(Composer).k17.call(this, value);
  } else {
    protoOf(Composer).a17.call(this, value);
  }
};
function ComposerWithPrettyPrint(writer, json) {
  Composer.call(this, writer);
  this.u17_1 = json;
  this.v17_1 = 0;
}
protoOf(ComposerWithPrettyPrint).t16 = function () {
  this.s16_1 = true;
  this.v17_1 = this.v17_1 + 1 | 0;
};
protoOf(ComposerWithPrettyPrint).u16 = function () {
  this.v17_1 = this.v17_1 - 1 | 0;
};
protoOf(ComposerWithPrettyPrint).v16 = function () {
  this.s16_1 = false;
  this.a17('\n');
  // Inline function 'kotlin.repeat' call
  var times = this.v17_1;
  // Inline function 'kotlin.contracts.contract' call
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlinx.serialization.json.internal.ComposerWithPrettyPrint.nextItem.<anonymous>' call
      this.a17(this.u17_1.h13_1.a15_1);
    }
     while (inductionVariable < times);
};
protoOf(ComposerWithPrettyPrint).w16 = function () {
  if (this.s16_1)
    this.s16_1 = false;
  else {
    this.v16();
  }
};
protoOf(ComposerWithPrettyPrint).x16 = function () {
  this.y16(_Char___init__impl__6a9atx(32));
};
function readIfAbsent($this, descriptor, index) {
  $this.x17_1 = (!descriptor.tl(index) && descriptor.sl(index).gl());
  return $this.x17_1;
}
function JsonElementMarker$readIfAbsent$ref($boundThis) {
  var l = function (p0, p1) {
    return readIfAbsent($boundThis, p0, p1);
  };
  l.callableName = 'readIfAbsent';
  return l;
}
function JsonElementMarker(descriptor) {
  var tmp = this;
  tmp.w17_1 = new ElementMarker(descriptor, JsonElementMarker$readIfAbsent$ref(this));
  this.x17_1 = false;
}
protoOf(JsonElementMarker).y17 = function (index) {
  this.w17_1.it(index);
};
protoOf(JsonElementMarker).z17 = function () {
  return this.w17_1.jt();
};
function invalidTrailingComma(_this__u8e3s4, entity) {
  entity = entity === VOID ? 'object' : entity;
  _this__u8e3s4.a18('Trailing comma before the end of JSON ' + entity, _this__u8e3s4.w13_1 - 1 | 0, "Trailing commas are non-complaint JSON and not allowed by default. Use 'allowTrailingCommas = true' in 'Json {}' builder to support them.");
}
function throwInvalidFloatingPointDecoded(_this__u8e3s4, result) {
  _this__u8e3s4.b18('Unexpected special floating-point value ' + toString(result) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification', VOID, "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'");
}
function JsonEncodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonEncodingException);
}
function InvalidKeyKindException(keyDescriptor) {
  return new JsonEncodingException("Value of type '" + keyDescriptor.kl() + "' can't be used in JSON as a key in the map. " + ("It should have either primitive or enum kind, but its kind is '" + keyDescriptor.ll().toString() + "'.\n") + "Use 'allowStructuredMapKeys = true' in 'Json {}' builder to convert such maps to [key1, value1, key2, value2,...] arrays.");
}
function JsonDecodingException(message) {
  JsonException.call(this, message);
  captureStack(this, JsonDecodingException);
}
function JsonDecodingException_0(offset, message, input) {
  return JsonDecodingException_1(offset, message + '\nJSON input: ' + toString(minify(input, offset)));
}
function InvalidFloatingPointDecoded(value, key, output) {
  return JsonDecodingException_1(-1, unexpectedFpErrorMessage(value, key, output));
}
function UnknownKeyException(key, input) {
  return JsonDecodingException_1(-1, "Encountered an unknown key '" + key + "'.\n" + "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.\n" + ('Current input: ' + toString(minify(input))));
}
function InvalidFloatingPointEncoded(value, output) {
  return new JsonEncodingException('Unexpected special floating-point value ' + toString(value) + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification. ' + "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'\n" + ('Current output: ' + toString(minify(output))));
}
function JsonException(message) {
  SerializationException_init_$Init$(message, this);
  captureStack(this, JsonException);
}
function unexpectedFpErrorMessage(value, key, output) {
  return 'Unexpected special floating-point value ' + toString(value) + ' with key ' + key + '. By default, ' + 'non-finite floating point values are prohibited because they do not conform JSON specification. ' + "It is possible to deserialize them using 'JsonBuilder.allowSpecialFloatingPointValues = true'\n" + ('Current output: ' + toString(minify(output)));
}
function JsonDecodingException_1(offset, message) {
  return new JsonDecodingException(offset >= 0 ? 'Unexpected JSON token at offset ' + offset + ': ' + message : message);
}
function minify(_this__u8e3s4, offset) {
  offset = offset === VOID ? -1 : offset;
  if (charSequenceLength(_this__u8e3s4) < 200)
    return _this__u8e3s4;
  if (offset === -1) {
    var start = charSequenceLength(_this__u8e3s4) - 60 | 0;
    if (start <= 0)
      return _this__u8e3s4;
    // Inline function 'kotlin.text.substring' call
    var endIndex = charSequenceLength(_this__u8e3s4);
    return '.....' + toString(charSequenceSubSequence(_this__u8e3s4, start, endIndex));
  }
  var start_0 = offset - 30 | 0;
  var end = offset + 30 | 0;
  var prefix = start_0 <= 0 ? '' : '.....';
  var suffix = end >= charSequenceLength(_this__u8e3s4) ? '' : '.....';
  // Inline function 'kotlin.text.substring' call
  var startIndex = coerceAtLeast(start_0, 0);
  var endIndex_0 = coerceAtMost(end, charSequenceLength(_this__u8e3s4));
  return prefix + toString(charSequenceSubSequence(_this__u8e3s4, startIndex, endIndex_0)) + suffix;
}
function get_JsonDeserializationNamesKey() {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return JsonDeserializationNamesKey;
}
var JsonDeserializationNamesKey;
function get_JsonSerializationNamesKey() {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return JsonSerializationNamesKey;
}
var JsonSerializationNamesKey;
function getJsonNameIndex(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  if (decodeCaseInsensitive(json, _this__u8e3s4)) {
    // Inline function 'kotlin.text.lowercase' call
    // Inline function 'kotlin.js.asDynamic' call
    var tmp$ret$1 = name.toLowerCase();
    return getJsonNameIndexSlowPath(_this__u8e3s4, json, tmp$ret$1);
  }
  var strategy = namingStrategy(_this__u8e3s4, json);
  if (!(strategy == null))
    return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
  var index = _this__u8e3s4.ql(name);
  if (!(index === -3))
    return index;
  if (!json.h13_1.f15_1)
    return index;
  return getJsonNameIndexSlowPath(_this__u8e3s4, json, name);
}
function getJsonElementName(_this__u8e3s4, json, index) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var strategy = namingStrategy(_this__u8e3s4, json);
  return strategy == null ? _this__u8e3s4.pl(index) : serializationNamesIndices(_this__u8e3s4, json, strategy)[index];
}
function namingStrategy(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return equals(_this__u8e3s4.ll(), CLASS_getInstance()) ? json.h13_1.g15_1 : null;
}
function deserializationNamesMap(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(_this__u8e3s4);
  var tmp_0 = get_JsonDeserializationNamesKey();
  return tmp.d18(descriptor, tmp_0, deserializationNamesMap$lambda(descriptor, _this__u8e3s4));
}
function decodeCaseInsensitive(_this__u8e3s4, descriptor) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  return _this__u8e3s4.h13_1.h15_1 && equals(descriptor.ll(), ENUM_getInstance());
}
function getJsonNameIndexSlowPath(_this__u8e3s4, json, name) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp0_elvis_lhs = deserializationNamesMap(json, _this__u8e3s4).c2(name);
  return tmp0_elvis_lhs == null ? -3 : tmp0_elvis_lhs;
}
function serializationNamesIndices(_this__u8e3s4, json, strategy) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  var tmp = get_schemaCache(json);
  var tmp_0 = get_JsonSerializationNamesKey();
  return tmp.d18(_this__u8e3s4, tmp_0, serializationNamesIndices$lambda(_this__u8e3s4, strategy));
}
function buildDeserializationNamesMap(_this__u8e3s4, json) {
  _init_properties_JsonNamesMap_kt__cbbp0k();
  // Inline function 'kotlin.collections.mutableMapOf' call
  var builder = LinkedHashMap_init_$Create$();
  var useLowercaseEnums = decodeCaseInsensitive(json, _this__u8e3s4);
  var strategyForClasses = namingStrategy(_this__u8e3s4, json);
  var inductionVariable = 0;
  var last = _this__u8e3s4.nl();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.collections.filterIsInstance' call
      // Inline function 'kotlin.collections.filterIsInstanceTo' call
      var this_0 = _this__u8e3s4.rl(i);
      var destination = ArrayList_init_$Create$();
      var tmp0_iterator = this_0.j();
      while (tmp0_iterator.k()) {
        var element = tmp0_iterator.l();
        if (element instanceof JsonNames) {
          destination.h(element);
        }
      }
      var tmp0_safe_receiver = singleOrNull(destination);
      var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.e18_1;
      if (tmp1_safe_receiver == null)
        null;
      else {
        // Inline function 'kotlin.collections.forEach' call
        var inductionVariable_0 = 0;
        var last_0 = tmp1_safe_receiver.length;
        while (inductionVariable_0 < last_0) {
          var element_0 = tmp1_safe_receiver[inductionVariable_0];
          inductionVariable_0 = inductionVariable_0 + 1 | 0;
          // Inline function 'kotlinx.serialization.json.internal.buildDeserializationNamesMap.<anonymous>' call
          var tmp;
          if (useLowercaseEnums) {
            // Inline function 'kotlin.text.lowercase' call
            // Inline function 'kotlin.js.asDynamic' call
            tmp = element_0.toLowerCase();
          } else {
            tmp = element_0;
          }
          buildDeserializationNamesMap$putOrThrow(builder, _this__u8e3s4, tmp, i);
        }
      }
      var tmp_0;
      if (useLowercaseEnums) {
        // Inline function 'kotlin.text.lowercase' call
        // Inline function 'kotlin.js.asDynamic' call
        tmp_0 = _this__u8e3s4.pl(i).toLowerCase();
      } else if (!(strategyForClasses == null)) {
        tmp_0 = strategyForClasses.f18(_this__u8e3s4, i, _this__u8e3s4.pl(i));
      } else {
        tmp_0 = null;
      }
      var nameToPut = tmp_0;
      if (nameToPut == null)
        null;
      else {
        // Inline function 'kotlin.let' call
        // Inline function 'kotlin.contracts.contract' call
        buildDeserializationNamesMap$putOrThrow(builder, _this__u8e3s4, nameToPut, i);
      }
    }
     while (inductionVariable < last);
  // Inline function 'kotlin.collections.ifEmpty' call
  // Inline function 'kotlin.contracts.contract' call
  var tmp_1;
  if (builder.n()) {
    // Inline function 'kotlinx.serialization.json.internal.buildDeserializationNamesMap.<anonymous>' call
    tmp_1 = emptyMap();
  } else {
    tmp_1 = builder;
  }
  return tmp_1;
}
function buildDeserializationNamesMap$putOrThrow(_this__u8e3s4, $this_buildDeserializationNamesMap, name, index) {
  var entity = equals($this_buildDeserializationNamesMap.ll(), ENUM_getInstance()) ? 'enum value' : 'property';
  // Inline function 'kotlin.collections.contains' call
  // Inline function 'kotlin.collections.containsKey' call
  if ((isInterface(_this__u8e3s4, KtMap) ? _this__u8e3s4 : THROW_CCE()).a2(name)) {
    throw new JsonException("The suggested name '" + name + "' for " + entity + ' ' + $this_buildDeserializationNamesMap.pl(index) + ' is already one of the names for ' + entity + ' ' + ($this_buildDeserializationNamesMap.pl(getValue(_this__u8e3s4, name)) + ' in ' + toString($this_buildDeserializationNamesMap)));
  }
  // Inline function 'kotlin.collections.set' call
  _this__u8e3s4.k2(name, index);
}
function deserializationNamesMap$lambda($descriptor, $this_deserializationNamesMap) {
  return function () {
    return buildDeserializationNamesMap($descriptor, $this_deserializationNamesMap);
  };
}
function serializationNamesIndices$lambda($this_serializationNamesIndices, $strategy) {
  return function () {
    var tmp = 0;
    var tmp_0 = $this_serializationNamesIndices.nl();
    // Inline function 'kotlin.arrayOfNulls' call
    var tmp_1 = fillArrayVal(Array(tmp_0), null);
    while (tmp < tmp_0) {
      var tmp_2 = tmp;
      var baseName = $this_serializationNamesIndices.pl(tmp_2);
      tmp_1[tmp_2] = $strategy.f18($this_serializationNamesIndices, tmp_2, baseName);
      tmp = tmp + 1 | 0;
    }
    return tmp_1;
  };
}
var properties_initialized_JsonNamesMap_kt_ljpf42;
function _init_properties_JsonNamesMap_kt__cbbp0k() {
  if (!properties_initialized_JsonNamesMap_kt_ljpf42) {
    properties_initialized_JsonNamesMap_kt_ljpf42 = true;
    JsonDeserializationNamesKey = new Key();
    JsonSerializationNamesKey = new Key();
  }
}
function Tombstone() {
}
var Tombstone_instance;
function Tombstone_getInstance() {
  return Tombstone_instance;
}
function resize($this) {
  var newSize = imul($this.i18_1, 2);
  $this.g18_1 = copyOf($this.g18_1, newSize);
  $this.h18_1 = copyOf_0($this.h18_1, newSize);
}
function JsonPath() {
  var tmp = this;
  // Inline function 'kotlin.arrayOfNulls' call
  tmp.g18_1 = fillArrayVal(Array(8), null);
  var tmp_0 = this;
  var tmp_1 = 0;
  var tmp_2 = new Int32Array(8);
  while (tmp_1 < 8) {
    tmp_2[tmp_1] = -1;
    tmp_1 = tmp_1 + 1 | 0;
  }
  tmp_0.h18_1 = tmp_2;
  this.i18_1 = -1;
}
protoOf(JsonPath).j18 = function (sd) {
  this.i18_1 = this.i18_1 + 1 | 0;
  var depth = this.i18_1;
  if (depth === this.g18_1.length) {
    resize(this);
  }
  this.g18_1[depth] = sd;
};
protoOf(JsonPath).k18 = function (index) {
  this.h18_1[this.i18_1] = index;
};
protoOf(JsonPath).l18 = function (key) {
  var tmp;
  if (!(this.h18_1[this.i18_1] === -2)) {
    this.i18_1 = this.i18_1 + 1 | 0;
    tmp = this.i18_1 === this.g18_1.length;
  } else {
    tmp = false;
  }
  if (tmp) {
    resize(this);
  }
  this.g18_1[this.i18_1] = key;
  this.h18_1[this.i18_1] = -2;
};
protoOf(JsonPath).m18 = function () {
  if (this.h18_1[this.i18_1] === -2) {
    this.g18_1[this.i18_1] = Tombstone_instance;
  }
};
protoOf(JsonPath).n18 = function () {
  var depth = this.i18_1;
  if (this.h18_1[depth] === -2) {
    this.h18_1[depth] = -1;
    this.i18_1 = this.i18_1 - 1 | 0;
  }
  if (!(this.i18_1 === -1)) {
    this.i18_1 = this.i18_1 - 1 | 0;
  }
};
protoOf(JsonPath).o18 = function () {
  // Inline function 'kotlin.text.buildString' call
  // Inline function 'kotlin.contracts.contract' call
  // Inline function 'kotlin.apply' call
  var this_0 = StringBuilder_init_$Create$();
  // Inline function 'kotlin.contracts.contract' call
  // Inline function 'kotlinx.serialization.json.internal.JsonPath.getPath.<anonymous>' call
  this_0.a8('$');
  // Inline function 'kotlin.repeat' call
  var times = this.i18_1 + 1 | 0;
  // Inline function 'kotlin.contracts.contract' call
  var inductionVariable = 0;
  if (inductionVariable < times)
    do {
      var index = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlinx.serialization.json.internal.JsonPath.getPath.<anonymous>.<anonymous>' call
      var element = this.g18_1[index];
      if (!(element == null) ? isInterface(element, SerialDescriptor) : false) {
        if (equals(element.ll(), LIST_getInstance())) {
          if (!(this.h18_1[index] === -1)) {
            this_0.a8('[');
            this_0.va(this.h18_1[index]);
            this_0.a8(']');
          }
        } else {
          var idx = this.h18_1[index];
          if (idx >= 0) {
            this_0.a8('.');
            this_0.a8(element.pl(idx));
          }
        }
      } else {
        if (!(element === Tombstone_instance)) {
          this_0.a8('[');
          this_0.a8("'");
          this_0.z7(element);
          this_0.a8("'");
          this_0.a8(']');
        }
      }
    }
     while (inductionVariable < times);
  return this_0.toString();
};
protoOf(JsonPath).toString = function () {
  return this.o18();
};
function encodeByWriter(json, writer, serializer, value) {
  var tmp = WriteMode_OBJ_getInstance();
  // Inline function 'kotlin.arrayOfNulls' call
  var size = get_entries().m();
  var tmp$ret$0 = fillArrayVal(Array(size), null);
  var encoder = StreamingJsonEncoder_init_$Create$(writer, json, tmp, tmp$ret$0);
  encoder.uo(serializer, value);
}
function readObject($this) {
  // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObjectImpl' call
  var lastToken = $this.y18_1.c19(6);
  if ($this.y18_1.d19() === 4) {
    $this.y18_1.b18('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.linkedMapOf' call
  var result = LinkedHashMap_init_$Create$();
  $l$loop: while ($this.y18_1.e19()) {
    var key = $this.z18_1 ? $this.y18_1.g19() : $this.y18_1.f19();
    $this.y18_1.c19(5);
    // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readObject.<anonymous>' call
    var element = $this.h19();
    // Inline function 'kotlin.collections.set' call
    result.k2(key, element);
    lastToken = $this.y18_1.i19();
    var tmp0_subject = lastToken;
    if (tmp0_subject !== 4)
      if (tmp0_subject === 7)
        break $l$loop;
      else {
        $this.y18_1.b18('Expected end of the object or comma');
      }
  }
  if (lastToken === 6) {
    $this.y18_1.c19(7);
  } else if (lastToken === 4) {
    if (!$this.a19_1) {
      invalidTrailingComma($this.y18_1);
    }
    $this.y18_1.c19(7);
  }
  return new JsonObject(result);
}
function readObject_0(_this__u8e3s4, $this, $completion) {
  var tmp = new $readObjectCOROUTINE$0($this, _this__u8e3s4, $completion);
  tmp.q8_1 = Unit_instance;
  tmp.r8_1 = null;
  return tmp.w8();
}
function readArray($this) {
  var lastToken = $this.y18_1.i19();
  if ($this.y18_1.d19() === 4) {
    $this.y18_1.b18('Unexpected leading comma');
  }
  // Inline function 'kotlin.collections.arrayListOf' call
  var result = ArrayList_init_$Create$();
  while ($this.y18_1.e19()) {
    var element = $this.h19();
    result.h(element);
    lastToken = $this.y18_1.i19();
    if (!(lastToken === 4)) {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
      var this_0 = $this.y18_1;
      var condition = lastToken === 9;
      var position = this_0.w13_1;
      if (!condition) {
        // Inline function 'kotlinx.serialization.json.internal.JsonTreeReader.readArray.<anonymous>' call
        var tmp$ret$1 = 'Expected end of the array or comma';
        this_0.b18(tmp$ret$1, position);
      }
    }
  }
  if (lastToken === 8) {
    $this.y18_1.c19(9);
  } else if (lastToken === 4) {
    if (!$this.a19_1) {
      invalidTrailingComma($this.y18_1, 'array');
    }
    $this.y18_1.c19(9);
  }
  return new JsonArray(result);
}
function readValue($this, isString) {
  var tmp;
  if ($this.z18_1 || !isString) {
    tmp = $this.y18_1.g19();
  } else {
    tmp = $this.y18_1.f19();
  }
  var string = tmp;
  if (!isString && string === 'null')
    return JsonNull_getInstance();
  return new JsonLiteral(string, isString);
}
function readDeepRecursive($this) {
  return invoke(new DeepRecursiveFunction(JsonTreeReader$readDeepRecursive$slambda_0($this, null)), Unit_instance);
}
function JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation) {
  this.f1a_1 = this$0;
  CoroutineImpl.call(this, resultContinuation);
}
protoOf(JsonTreeReader$readDeepRecursive$slambda).k1a = function ($this$DeepRecursiveFunction, it, $completion) {
  var tmp = this.l1a($this$DeepRecursiveFunction, it, $completion);
  tmp.q8_1 = Unit_instance;
  tmp.r8_1 = null;
  return tmp.w8();
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).c9 = function (p1, p2, $completion) {
  var tmp = p1 instanceof DeepRecursiveScope ? p1 : THROW_CCE();
  return this.k1a(tmp, p2 instanceof Unit ? p2 : THROW_CCE(), $completion);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).w8 = function () {
  var suspendResult = this.q8_1;
  $sm: do
    try {
      var tmp = this.o8_1;
      switch (tmp) {
        case 0:
          this.p8_1 = 3;
          this.i1a_1 = this.f1a_1.y18_1.d19();
          if (this.i1a_1 === 1) {
            this.j1a_1 = readValue(this.f1a_1, true);
            this.o8_1 = 2;
            continue $sm;
          } else {
            if (this.i1a_1 === 0) {
              this.j1a_1 = readValue(this.f1a_1, false);
              this.o8_1 = 2;
              continue $sm;
            } else {
              if (this.i1a_1 === 6) {
                this.o8_1 = 1;
                suspendResult = readObject_0(this.g1a_1, this.f1a_1, this);
                if (suspendResult === get_COROUTINE_SUSPENDED()) {
                  return suspendResult;
                }
                continue $sm;
              } else {
                if (this.i1a_1 === 8) {
                  this.j1a_1 = readArray(this.f1a_1);
                  this.o8_1 = 2;
                  continue $sm;
                } else {
                  var tmp_0 = this;
                  this.f1a_1.y18_1.b18("Can't begin reading element, unexpected token");
                }
              }
            }
          }

          break;
        case 1:
          this.j1a_1 = suspendResult;
          this.o8_1 = 2;
          continue $sm;
        case 2:
          return this.j1a_1;
        case 3:
          throw this.r8_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.p8_1 === 3) {
        throw e;
      } else {
        this.o8_1 = this.p8_1;
        this.r8_1 = e;
      }
    }
   while (true);
};
protoOf(JsonTreeReader$readDeepRecursive$slambda).l1a = function ($this$DeepRecursiveFunction, it, completion) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this.f1a_1, completion);
  i.g1a_1 = $this$DeepRecursiveFunction;
  i.h1a_1 = it;
  return i;
};
function JsonTreeReader$readDeepRecursive$slambda_0(this$0, resultContinuation) {
  var i = new JsonTreeReader$readDeepRecursive$slambda(this$0, resultContinuation);
  var l = function ($this$DeepRecursiveFunction, it, $completion) {
    return i.k1a($this$DeepRecursiveFunction, it, $completion);
  };
  l.$arity = 2;
  return l;
}
function $readObjectCOROUTINE$0(_this__u8e3s4, _this__u8e3s4_0, resultContinuation) {
  CoroutineImpl.call(this, resultContinuation);
  this.r19_1 = _this__u8e3s4;
  this.s19_1 = _this__u8e3s4_0;
}
protoOf($readObjectCOROUTINE$0).w8 = function () {
  var suspendResult = this.q8_1;
  $sm: do
    try {
      var tmp = this.o8_1;
      switch (tmp) {
        case 0:
          this.p8_1 = 5;
          var tmp_0 = this;
          tmp_0.t19_1 = this.r19_1;
          this.u19_1 = this.t19_1.y18_1.c19(6);
          if (this.t19_1.y18_1.d19() === 4) {
            this.t19_1.y18_1.b18('Unexpected leading comma');
          }

          var tmp_1 = this;
          tmp_1.v19_1 = LinkedHashMap_init_$Create$();
          this.o8_1 = 1;
          continue $sm;
        case 1:
          if (!this.t19_1.y18_1.e19()) {
            this.o8_1 = 4;
            continue $sm;
          }

          this.w19_1 = this.t19_1.z18_1 ? this.t19_1.y18_1.g19() : this.t19_1.y18_1.f19();
          this.t19_1.y18_1.c19(5);
          this.o8_1 = 2;
          suspendResult = this.s19_1.lh(Unit_instance, this);
          if (suspendResult === get_COROUTINE_SUSPENDED()) {
            return suspendResult;
          }

          continue $sm;
        case 2:
          var element = suspendResult;
          var this_0 = this.v19_1;
          var key = this.w19_1;
          this_0.k2(key, element);
          this.u19_1 = this.t19_1.y18_1.i19();
          var tmp0_subject = this.u19_1;
          if (tmp0_subject === 4) {
            this.o8_1 = 3;
            continue $sm;
          } else {
            if (tmp0_subject === 7) {
              this.o8_1 = 4;
              continue $sm;
            } else {
              this.t19_1.y18_1.b18('Expected end of the object or comma');
            }
          }

          break;
        case 3:
          this.o8_1 = 1;
          continue $sm;
        case 4:
          if (this.u19_1 === 6) {
            this.t19_1.y18_1.c19(7);
          } else if (this.u19_1 === 4) {
            if (!this.t19_1.a19_1) {
              invalidTrailingComma(this.t19_1.y18_1);
            }
            this.t19_1.y18_1.c19(7);
          }

          return new JsonObject(this.v19_1);
        case 5:
          throw this.r8_1;
      }
    } catch ($p) {
      var e = $p;
      if (this.p8_1 === 5) {
        throw e;
      } else {
        this.o8_1 = this.p8_1;
        this.r8_1 = e;
      }
    }
   while (true);
};
function JsonTreeReader(configuration, lexer) {
  this.y18_1 = lexer;
  this.z18_1 = configuration.w14_1;
  this.a19_1 = configuration.i15_1;
  this.b19_1 = 0;
}
protoOf(JsonTreeReader).h19 = function () {
  var token = this.y18_1.d19();
  var tmp;
  if (token === 1) {
    tmp = readValue(this, true);
  } else if (token === 0) {
    tmp = readValue(this, false);
  } else if (token === 6) {
    var tmp_0;
    this.b19_1 = this.b19_1 + 1 | 0;
    if (this.b19_1 === 200) {
      tmp_0 = readDeepRecursive(this);
    } else {
      tmp_0 = readObject(this);
    }
    var result = tmp_0;
    this.b19_1 = this.b19_1 - 1 | 0;
    tmp = result;
  } else if (token === 8) {
    tmp = readArray(this);
  } else {
    this.y18_1.b18('Cannot read Json element because of unexpected ' + tokenDescription(token));
  }
  return tmp;
};
function classDiscriminator(_this__u8e3s4, json) {
  var _iterator__ex2g4s = _this__u8e3s4.ol().j();
  while (_iterator__ex2g4s.k()) {
    var annotation = _iterator__ex2g4s.l();
    if (annotation instanceof JsonClassDiscriminator)
      return annotation.m1a_1;
  }
  return json.h13_1.d15_1;
}
function validateIfSealed(serializer, actualSerializer, classDiscriminator) {
  if (!(serializer instanceof SealedClassSerializer))
    return Unit_instance;
  if (jsonCachedSerialNames(actualSerializer.gk()).u(classDiscriminator)) {
    var baseName = serializer.gk().kl();
    var actualName = actualSerializer.gk().kl();
    // Inline function 'kotlin.error' call
    var message = "Sealed class '" + actualName + "' cannot be serialized as base class '" + baseName + "' because" + (" it has property name that conflicts with JSON class discriminator '" + classDiscriminator + "'. ") + 'You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation or fall back to array polymorphism';
    throw IllegalStateException_init_$Create$(toString(message));
  }
}
function checkKind(kind) {
  if (kind instanceof ENUM) {
    // Inline function 'kotlin.error' call
    var message = "Enums cannot be serialized polymorphically with 'type' parameter. You can use 'JsonBuilder.useArrayPolymorphism' instead";
    throw IllegalStateException_init_$Create$(toString(message));
  }
  if (kind instanceof PrimitiveKind) {
    // Inline function 'kotlin.error' call
    var message_0 = "Primitives cannot be serialized polymorphically with 'type' parameter. You can use 'JsonBuilder.useArrayPolymorphism' instead";
    throw IllegalStateException_init_$Create$(toString(message_0));
  }
  if (kind instanceof PolymorphicKind) {
    // Inline function 'kotlin.error' call
    var message_1 = 'Actual serializer for polymorphic cannot be polymorphic itself';
    throw IllegalStateException_init_$Create$(toString(message_1));
  }
}
function validateIfSealed$accessor$iffjvx(serializer, actualSerializer, classDiscriminator) {
  return validateIfSealed(serializer, actualSerializer, classDiscriminator);
}
function checkKind_0($this, descriptor, actualClass) {
  var kind = descriptor.ll();
  var tmp;
  if (kind instanceof PolymorphicKind) {
    tmp = true;
  } else {
    tmp = equals(kind, CONTEXTUAL_getInstance());
  }
  if (tmp) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.h9() + " can't be registered as a subclass for polymorphic serialization " + ('because its kind ' + kind.toString() + ' is not concrete. To work with multiple hierarchies, register it as a base class.'));
  }
  if ($this.n1a_1)
    return Unit_instance;
  var tmp_0;
  var tmp_1;
  if (equals(kind, LIST_getInstance()) || equals(kind, MAP_getInstance())) {
    tmp_1 = true;
  } else {
    tmp_1 = kind instanceof PrimitiveKind;
  }
  if (tmp_1) {
    tmp_0 = true;
  } else {
    tmp_0 = kind instanceof ENUM;
  }
  if (tmp_0) {
    throw IllegalArgumentException_init_$Create$('Serializer for ' + actualClass.h9() + ' of kind ' + kind.toString() + ' cannot be serialized polymorphically with class discriminator.');
  }
}
function checkDiscriminatorCollisions($this, descriptor, actualClass) {
  var inductionVariable = 0;
  var last = descriptor.nl();
  if (inductionVariable < last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var name = descriptor.pl(i);
      if (name === $this.o1a_1) {
        throw IllegalArgumentException_init_$Create$('Polymorphic serializer for ' + toString(actualClass) + " has property '" + name + "' that conflicts " + 'with JSON class discriminator. You can either change class discriminator in JsonConfiguration, ' + 'rename property with @SerialName annotation ' + 'or fall back to array polymorphism');
      }
    }
     while (inductionVariable < last);
}
function PolymorphismValidator(useArrayPolymorphism, discriminator) {
  this.n1a_1 = useArrayPolymorphism;
  this.o1a_1 = discriminator;
}
protoOf(PolymorphismValidator).y12 = function (kClass, provider) {
};
protoOf(PolymorphismValidator).b13 = function (baseClass, actualClass, actualSerializer) {
  var descriptor = actualSerializer.gk();
  checkKind_0(this, descriptor, actualClass);
  if (!this.n1a_1) {
    checkDiscriminatorCollisions(this, descriptor, actualClass);
  }
};
protoOf(PolymorphismValidator).c13 = function (baseClass, defaultSerializerProvider) {
};
protoOf(PolymorphismValidator).d13 = function (baseClass, defaultDeserializerProvider) {
};
function Key() {
}
function DescriptorSchemaCache() {
  this.c18_1 = createMapForCache(16);
}
protoOf(DescriptorSchemaCache).p1a = function (descriptor, key, value) {
  // Inline function 'kotlin.collections.set' call
  // Inline function 'kotlin.collections.getOrPut' call
  var this_0 = this.c18_1;
  var value_0 = this_0.c2(descriptor);
  var tmp;
  if (value_0 == null) {
    // Inline function 'kotlinx.serialization.json.internal.DescriptorSchemaCache.set.<anonymous>' call
    var answer = createMapForCache(2);
    this_0.k2(descriptor, answer);
    tmp = answer;
  } else {
    tmp = value_0;
  }
  var this_1 = tmp;
  var key_0 = key instanceof Key ? key : THROW_CCE();
  var value_1 = !(value == null) ? value : THROW_CCE();
  this_1.k2(key_0, value_1);
};
protoOf(DescriptorSchemaCache).d18 = function (descriptor, key, defaultValue) {
  var tmp0_safe_receiver = this.q1a(descriptor, key);
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    return tmp0_safe_receiver;
  }
  var value = defaultValue();
  this.p1a(descriptor, key, value);
  return value;
};
protoOf(DescriptorSchemaCache).q1a = function (descriptor, key) {
  var tmp0_safe_receiver = this.c18_1.c2(descriptor);
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    tmp = tmp0_safe_receiver.c2(key instanceof Key ? key : THROW_CCE());
  }
  var tmp_0 = tmp;
  return !(tmp_0 == null) ? tmp_0 : null;
};
function DiscriminatorHolder(discriminatorToSkip) {
  this.r1a_1 = discriminatorToSkip;
}
function trySkip(_this__u8e3s4, $this, unknownKey) {
  if (_this__u8e3s4 == null)
    return false;
  if (_this__u8e3s4.r1a_1 === unknownKey) {
    _this__u8e3s4.r1a_1 = null;
    return true;
  }
  return false;
}
function skipLeftoverElements($this, descriptor) {
  while (!($this.un(descriptor) === -1)) {
  }
}
function checkLeadingComma($this) {
  if ($this.q13_1.d19() === 4) {
    $this.q13_1.b18('Unexpected leading comma');
  }
}
function decodeMapIndex($this) {
  var hasComma = false;
  var decodingKey = !(($this.s13_1 % 2 | 0) === 0);
  if (decodingKey) {
    if (!($this.s13_1 === -1)) {
      hasComma = $this.q13_1.t1a();
    }
  } else {
    $this.q13_1.s1a(_Char___init__impl__6a9atx(58));
  }
  var tmp;
  if ($this.q13_1.e19()) {
    if (decodingKey) {
      if ($this.s13_1 === -1) {
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var this_0 = $this.q13_1;
        var condition = !hasComma;
        var position = this_0.w13_1;
        if (!condition) {
          // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeMapIndex.<anonymous>' call
          var tmp$ret$0 = 'Unexpected leading comma';
          this_0.b18(tmp$ret$0, position);
        }
      } else {
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.require' call
        var this_1 = $this.q13_1;
        var condition_0 = hasComma;
        var position_0 = this_1.w13_1;
        if (!condition_0) {
          // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeMapIndex.<anonymous>' call
          var tmp$ret$1 = 'Expected comma after the key-value pair';
          this_1.b18(tmp$ret$1, position_0);
        }
      }
    }
    $this.s13_1 = $this.s13_1 + 1 | 0;
    tmp = $this.s13_1;
  } else {
    if (hasComma && !$this.o13_1.h13_1.i15_1) {
      invalidTrailingComma($this.q13_1);
    }
    tmp = -1;
  }
  return tmp;
}
function coerceInputValue($this, descriptor, index) {
  var tmp$ret$1;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var this_0 = $this.o13_1;
    var isOptional = descriptor.tl(index);
    var elementDescriptor = descriptor.sl(index);
    var tmp;
    if (isOptional && !elementDescriptor.gl()) {
      // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.coerceInputValue.<anonymous>' call
      tmp = $this.q13_1.u1a(true);
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$1 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ll(), ENUM_getInstance())) {
      var tmp_0;
      if (elementDescriptor.gl()) {
        // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.coerceInputValue.<anonymous>' call
        tmp_0 = $this.q13_1.u1a(false);
      } else {
        tmp_0 = false;
      }
      if (tmp_0) {
        tmp$ret$1 = false;
        break $l$block_2;
      }
      // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.coerceInputValue.<anonymous>' call
      var tmp0_elvis_lhs = $this.q13_1.v1a($this.u13_1.w14_1);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$1 = false;
        break $l$block_2;
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_1;
      var enumIndex = getJsonNameIndex(elementDescriptor, this_0, enumValue);
      var coerceToNull = !this_0.h13_1.z14_1 && elementDescriptor.gl();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.coerceInputValue.<anonymous>' call
        $this.q13_1.f19();
        tmp$ret$1 = true;
        break $l$block_2;
      }
    }
    tmp$ret$1 = false;
  }
  return tmp$ret$1;
}
function decodeObjectIndex($this, descriptor) {
  var hasComma = $this.q13_1.t1a();
  while ($this.q13_1.e19()) {
    hasComma = false;
    var key = decodeStringKey($this);
    $this.q13_1.s1a(_Char___init__impl__6a9atx(58));
    var index = getJsonNameIndex(descriptor, $this.o13_1, key);
    var tmp;
    if (!(index === -3)) {
      var tmp_0;
      if ($this.u13_1.b15_1 && coerceInputValue($this, descriptor, index)) {
        hasComma = $this.q13_1.t1a();
        tmp_0 = false;
      } else {
        var tmp0_safe_receiver = $this.v13_1;
        if (tmp0_safe_receiver == null)
          null;
        else {
          tmp0_safe_receiver.y17(index);
        }
        return index;
      }
      tmp = tmp_0;
    } else {
      tmp = true;
    }
    var isUnknown = tmp;
    if (isUnknown) {
      hasComma = handleUnknown($this, key);
    }
  }
  if (hasComma && !$this.o13_1.h13_1.i15_1) {
    invalidTrailingComma($this.q13_1);
  }
  var tmp1_safe_receiver = $this.v13_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver.z17();
  return tmp2_elvis_lhs == null ? -1 : tmp2_elvis_lhs;
}
function handleUnknown($this, key) {
  if ($this.u13_1.v14_1 || trySkip($this.t13_1, $this, key)) {
    $this.q13_1.x1a($this.u13_1.w14_1);
  } else {
    $this.q13_1.w1a(key);
  }
  return $this.q13_1.t1a();
}
function decodeListIndex($this) {
  var hasComma = $this.q13_1.t1a();
  var tmp;
  if ($this.q13_1.e19()) {
    if (!($this.s13_1 === -1) && !hasComma) {
      $this.q13_1.b18('Expected end of the array or comma');
    }
    $this.s13_1 = $this.s13_1 + 1 | 0;
    tmp = $this.s13_1;
  } else {
    if (hasComma && !$this.o13_1.h13_1.i15_1) {
      invalidTrailingComma($this.q13_1, 'array');
    }
    tmp = -1;
  }
  return tmp;
}
function decodeStringKey($this) {
  var tmp;
  if ($this.u13_1.w14_1) {
    tmp = $this.q13_1.z1a();
  } else {
    tmp = $this.q13_1.y1a();
  }
  return tmp;
}
function StreamingJsonDecoder(json, mode, lexer, descriptor, discriminatorHolder) {
  AbstractDecoder.call(this);
  this.o13_1 = json;
  this.p13_1 = mode;
  this.q13_1 = lexer;
  this.r13_1 = this.o13_1.sn();
  this.s13_1 = -1;
  this.t13_1 = discriminatorHolder;
  this.u13_1 = this.o13_1.h13_1;
  this.v13_1 = this.u13_1.z14_1 ? null : new JsonElementMarker(descriptor);
}
protoOf(StreamingJsonDecoder).l15 = function () {
  return this.o13_1;
};
protoOf(StreamingJsonDecoder).sn = function () {
  return this.r13_1;
};
protoOf(StreamingJsonDecoder).m15 = function () {
  return (new JsonTreeReader(this.o13_1.h13_1, this.q13_1)).h19();
};
protoOf(StreamingJsonDecoder).cn = function (deserializer) {
  try {
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.o13_1.h13_1.c15_1;
    }
    if (tmp) {
      return deserializer.ik(this);
    }
    var discriminator = classDiscriminator(deserializer.gk(), this.o13_1);
    var tmp0_elvis_lhs = this.q13_1.a1b(discriminator, this.u13_1.w14_1);
    var tmp_0;
    if (tmp0_elvis_lhs == null) {
      var tmp$ret$0;
      $l$block: {
        // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
        var deserializer_0 = isInterface(deserializer, DeserializationStrategy) ? deserializer : THROW_CCE();
        var tmp_1;
        if (!(deserializer_0 instanceof AbstractPolymorphicSerializer)) {
          tmp_1 = true;
        } else {
          tmp_1 = this.l15().h13_1.c15_1;
        }
        if (tmp_1) {
          tmp$ret$0 = deserializer_0.ik(this);
          break $l$block;
        }
        var discriminator_0 = classDiscriminator(deserializer_0.gk(), this.l15());
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var value = this.m15();
        var serialName = deserializer_0.gk().kl();
        if (!(value instanceof JsonObject)) {
          var tmp_2 = getKClass(JsonObject).h9();
          var tmp_3 = getKClassFromExpression(value).h9();
          // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeSerializableValue.<anonymous>' call
          var tmp$ret$1 = this.q13_1.x13_1.o18();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$1, toString(value));
        }
        var jsonTree = value;
        var tmp0_safe_receiver = jsonTree.fc(discriminator_0);
        var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : get_jsonPrimitive(tmp0_safe_receiver);
        var type = tmp1_safe_receiver == null ? null : get_contentOrNull(tmp1_safe_receiver);
        var tmp_4;
        try {
          tmp_4 = findPolymorphicSerializer(deserializer_0, this, type);
        } catch ($p) {
          var tmp_5;
          if ($p instanceof SerializationException) {
            var it = $p;
            throw JsonDecodingException_0(-1, ensureNotNull(it.message), jsonTree.toString());
          } else {
            throw $p;
          }
        }
        var tmp_6 = tmp_4;
        var actualSerializer = isInterface(tmp_6, DeserializationStrategy) ? tmp_6 : THROW_CCE();
        tmp$ret$0 = readPolymorphicJson(this.l15(), discriminator_0, jsonTree, actualSerializer);
      }
      return tmp$ret$0;
    } else {
      tmp_0 = tmp0_elvis_lhs;
    }
    var type_0 = tmp_0;
    var tmp_7;
    try {
      tmp_7 = findPolymorphicSerializer(deserializer, this, type_0);
    } catch ($p) {
      var tmp_8;
      if ($p instanceof SerializationException) {
        var it_0 = $p;
        var message = removeSuffix(substringBefore(ensureNotNull(it_0.message), _Char___init__impl__6a9atx(10)), '.');
        var hint = substringAfter(ensureNotNull(it_0.message), _Char___init__impl__6a9atx(10), '');
        this.q13_1.b18(message, VOID, hint);
      } else {
        throw $p;
      }
      tmp_7 = tmp_8;
    }
    var tmp_9 = tmp_7;
    var actualSerializer_0 = isInterface(tmp_9, DeserializationStrategy) ? tmp_9 : THROW_CCE();
    this.t13_1 = new DiscriminatorHolder(discriminator);
    return actualSerializer_0.ik(this);
  } catch ($p) {
    if ($p instanceof MissingFieldException) {
      var e = $p;
      if (contains_0(ensureNotNull(e.message), 'at path'))
        throw e;
      throw new MissingFieldException(e.al_1, plus(e.message, ' at path: ') + this.q13_1.x13_1.o18(), e);
    } else {
      throw $p;
    }
  }
};
protoOf(StreamingJsonDecoder).dn = function (descriptor) {
  var newMode = switchMode(this.o13_1, descriptor);
  this.q13_1.x13_1.j18(descriptor);
  this.q13_1.s1a(newMode.d1b_1);
  checkLeadingComma(this);
  var tmp;
  switch (newMode.o2_1) {
    case 1:
    case 2:
    case 3:
      tmp = new StreamingJsonDecoder(this.o13_1, newMode, this.q13_1, descriptor, this.t13_1);
      break;
    default:
      var tmp_0;
      if (this.p13_1.equals(newMode) && this.o13_1.h13_1.z14_1) {
        tmp_0 = this;
      } else {
        tmp_0 = new StreamingJsonDecoder(this.o13_1, newMode, this.q13_1, descriptor, this.t13_1);
      }

      tmp = tmp_0;
      break;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).en = function (descriptor) {
  if (this.o13_1.h13_1.v14_1 && descriptor.nl() === 0) {
    skipLeftoverElements(this, descriptor);
  }
  if (this.q13_1.t1a() && !this.o13_1.h13_1.i15_1) {
    invalidTrailingComma(this.q13_1, '');
  }
  this.q13_1.s1a(this.p13_1.e1b_1);
  this.q13_1.x13_1.n18();
};
protoOf(StreamingJsonDecoder).pm = function () {
  var tmp;
  var tmp0_safe_receiver = this.v13_1;
  var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.x17_1;
  if (!(tmp1_elvis_lhs == null ? false : tmp1_elvis_lhs)) {
    tmp = !this.q13_1.f1b();
  } else {
    tmp = false;
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).qm = function () {
  return null;
};
protoOf(StreamingJsonDecoder).pn = function (descriptor, index, deserializer, previousValue) {
  var isMapKey = this.p13_1.equals(WriteMode_MAP_getInstance()) && (index & 1) === 0;
  if (isMapKey) {
    this.q13_1.x13_1.m18();
  }
  var value = protoOf(AbstractDecoder).pn.call(this, descriptor, index, deserializer, previousValue);
  if (isMapKey) {
    this.q13_1.x13_1.l18(value);
  }
  return value;
};
protoOf(StreamingJsonDecoder).un = function (descriptor) {
  var index;
  switch (this.p13_1.o2_1) {
    case 0:
      index = decodeObjectIndex(this, descriptor);
      break;
    case 2:
      index = decodeMapIndex(this);
      break;
    default:
      index = decodeListIndex(this);
      break;
  }
  if (!this.p13_1.equals(WriteMode_MAP_getInstance())) {
    this.q13_1.x13_1.k18(index);
  }
  return index;
};
protoOf(StreamingJsonDecoder).rm = function () {
  return this.q13_1.g1b();
};
protoOf(StreamingJsonDecoder).sm = function () {
  var value = this.q13_1.z15();
  if (!value.equals(toLong(value.g3()))) {
    this.q13_1.b18("Failed to parse byte for input '" + value.toString() + "'");
  }
  return value.g3();
};
protoOf(StreamingJsonDecoder).tm = function () {
  var value = this.q13_1.z15();
  if (!value.equals(toLong(value.h3()))) {
    this.q13_1.b18("Failed to parse short for input '" + value.toString() + "'");
  }
  return value.h3();
};
protoOf(StreamingJsonDecoder).um = function () {
  var value = this.q13_1.z15();
  if (!value.equals(toLong(value.g1()))) {
    this.q13_1.b18("Failed to parse int for input '" + value.toString() + "'");
  }
  return value.g1();
};
protoOf(StreamingJsonDecoder).vm = function () {
  return this.q13_1.z15();
};
protoOf(StreamingJsonDecoder).wm = function () {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.q13_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeFloat.<anonymous>' call
      // Inline function 'kotlin.text.toFloat' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp$ret$4 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'float' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$4;
  var specialFp = this.o13_1.h13_1.e15_1;
  if (specialFp || isFinite(result))
    return result;
  throwInvalidFloatingPointDecoded(this.q13_1, result);
};
protoOf(StreamingJsonDecoder).xm = function () {
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.q13_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeDouble.<anonymous>' call
      tmp$ret$1 = toDouble(input);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'double' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$1;
  var specialFp = this.o13_1.h13_1.e15_1;
  if (specialFp || isFinite_0(result))
    return result;
  throwInvalidFloatingPointDecoded(this.q13_1, result);
};
protoOf(StreamingJsonDecoder).ym = function () {
  var string = this.q13_1.g19();
  if (!(string.length === 1)) {
    this.q13_1.b18("Expected single char, but got '" + string + "'");
  }
  return charSequenceGet(string, 0);
};
protoOf(StreamingJsonDecoder).zm = function () {
  var tmp;
  if (this.u13_1.w14_1) {
    tmp = this.q13_1.z1a();
  } else {
    tmp = this.q13_1.f19();
  }
  return tmp;
};
protoOf(StreamingJsonDecoder).an = function (descriptor) {
  return get_isUnsignedNumber(descriptor) ? new JsonDecoderForUnsignedTypes(this.q13_1, this.o13_1) : protoOf(AbstractDecoder).an.call(this, descriptor);
};
function JsonDecoderForUnsignedTypes(lexer, json) {
  AbstractDecoder.call(this);
  this.h1b_1 = lexer;
  this.i1b_1 = json.sn();
}
protoOf(JsonDecoderForUnsignedTypes).sn = function () {
  return this.i1b_1;
};
protoOf(JsonDecoderForUnsignedTypes).un = function (descriptor) {
  var message = 'unsupported';
  throw IllegalStateException_init_$Create$(toString(message));
};
protoOf(JsonDecoderForUnsignedTypes).um = function () {
  var tmp$ret$2;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.h1b_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.JsonDecoderForUnsignedTypes.decodeInt.<anonymous>' call
      // Inline function 'kotlin.UInt.toInt' call
      var this_1 = toUInt(input);
      tmp$ret$2 = _UInt___get_data__impl__f0vqqw(this_1);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'UInt' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$2;
};
protoOf(JsonDecoderForUnsignedTypes).vm = function () {
  var tmp$ret$2;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.h1b_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.JsonDecoderForUnsignedTypes.decodeLong.<anonymous>' call
      // Inline function 'kotlin.ULong.toLong' call
      var this_1 = toULong(input);
      tmp$ret$2 = _ULong___get_data__impl__fggpzb(this_1);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'ULong' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$2;
};
protoOf(JsonDecoderForUnsignedTypes).sm = function () {
  var tmp$ret$2;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.h1b_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.JsonDecoderForUnsignedTypes.decodeByte.<anonymous>' call
      // Inline function 'kotlin.UByte.toByte' call
      var this_1 = toUByte(input);
      tmp$ret$2 = _UByte___get_data__impl__jof9qr(this_1);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'UByte' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$2;
};
protoOf(JsonDecoderForUnsignedTypes).tm = function () {
  var tmp$ret$2;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.parseString' call
    var this_0 = this.h1b_1;
    var input = this_0.g19();
    try {
      // Inline function 'kotlinx.serialization.json.internal.JsonDecoderForUnsignedTypes.decodeShort.<anonymous>' call
      // Inline function 'kotlin.UShort.toShort' call
      var this_1 = toUShort(input);
      tmp$ret$2 = _UShort___get_data__impl__g0245(this_1);
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        this_0.b18("Failed to parse type '" + 'UShort' + "' for input '" + input + "'");
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$2;
};
function get_unsignedNumberDescriptors() {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return unsignedNumberDescriptors;
}
var unsignedNumberDescriptors;
function StreamingJsonEncoder_init_$Init$(output, json, mode, modeReuseCache, $this) {
  StreamingJsonEncoder.call($this, Composer_0(output, json), json, mode, modeReuseCache);
  return $this;
}
function StreamingJsonEncoder_init_$Create$(output, json, mode, modeReuseCache) {
  return StreamingJsonEncoder_init_$Init$(output, json, mode, modeReuseCache, objectCreate(protoOf(StreamingJsonEncoder)));
}
function encodeTypeInfo($this, discriminator, serialName) {
  $this.p18_1.v16();
  $this.ho(discriminator);
  $this.p18_1.y16(_Char___init__impl__6a9atx(58));
  $this.p18_1.x16();
  $this.ho(serialName);
}
function StreamingJsonEncoder(composer, json, mode, modeReuseCache) {
  AbstractEncoder.call(this);
  this.p18_1 = composer;
  this.q18_1 = json;
  this.r18_1 = mode;
  this.s18_1 = modeReuseCache;
  this.t18_1 = this.q18_1.sn();
  this.u18_1 = this.q18_1.h13_1;
  this.v18_1 = false;
  this.w18_1 = null;
  this.x18_1 = null;
  var i = this.r18_1.o2_1;
  if (!(this.s18_1 == null)) {
    if (!(this.s18_1[i] === null) || !(this.s18_1[i] === this)) {
      this.s18_1[i] = this;
    }
  }
}
protoOf(StreamingJsonEncoder).l15 = function () {
  return this.q18_1;
};
protoOf(StreamingJsonEncoder).sn = function () {
  return this.t18_1;
};
protoOf(StreamingJsonEncoder).zo = function (descriptor, index) {
  return this.u18_1.u14_1;
};
protoOf(StreamingJsonEncoder).uo = function (serializer, value) {
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically' call
    if (this.l15().h13_1.c15_1) {
      serializer.hk(this, value);
      break $l$block;
    }
    var isPolymorphicSerializer = serializer instanceof AbstractPolymorphicSerializer;
    var tmp;
    if (isPolymorphicSerializer) {
      tmp = !this.l15().h13_1.k15_1.equals(ClassDiscriminatorMode_NONE_getInstance());
    } else {
      var tmp_0;
      switch (this.l15().h13_1.k15_1.o2_1) {
        case 0:
        case 2:
          tmp_0 = false;
          break;
        case 1:
          // Inline function 'kotlin.let' call

          // Inline function 'kotlin.contracts.contract' call

          // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically.<anonymous>' call

          var it = serializer.gk().ll();
          tmp_0 = equals(it, CLASS_getInstance()) || equals(it, OBJECT_getInstance());
          break;
        default:
          noWhenBranchMatchedException();
          break;
      }
      tmp = tmp_0;
    }
    var needDiscriminator = tmp;
    var baseClassDiscriminator = needDiscriminator ? classDiscriminator(serializer.gk(), this.l15()) : null;
    var tmp_1;
    if (isPolymorphicSerializer) {
      var casted = serializer instanceof AbstractPolymorphicSerializer ? serializer : THROW_CCE();
      $l$block_0: {
        // Inline function 'kotlin.requireNotNull' call
        // Inline function 'kotlin.contracts.contract' call
        if (value == null) {
          // Inline function 'kotlinx.serialization.json.internal.encodePolymorphically.<anonymous>' call
          var message = 'Value for serializer ' + toString(serializer.gk()) + ' should always be non-null. Please report issue to the kotlinx.serialization tracker.';
          throw IllegalArgumentException_init_$Create$(toString(message));
        } else {
          break $l$block_0;
        }
      }
      var actual = findPolymorphicSerializer_0(casted, this, value);
      if (!(baseClassDiscriminator == null)) {
        validateIfSealed$accessor$iffjvx(serializer, actual, baseClassDiscriminator);
      }
      checkKind(actual.gk().ll());
      tmp_1 = isInterface(actual, SerializationStrategy) ? actual : THROW_CCE();
    } else {
      tmp_1 = serializer;
    }
    var actualSerializer = tmp_1;
    if (!(baseClassDiscriminator == null)) {
      // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.encodeSerializableValue.<anonymous>' call
      var serialName = actualSerializer.gk().kl();
      this.w18_1 = baseClassDiscriminator;
      this.x18_1 = serialName;
    }
    actualSerializer.hk(this, value);
  }
};
protoOf(StreamingJsonEncoder).dn = function (descriptor) {
  var newMode = switchMode(this.q18_1, descriptor);
  if (!(newMode.d1b_1 === _Char___init__impl__6a9atx(0))) {
    this.p18_1.y16(newMode.d1b_1);
    this.p18_1.t16();
  }
  var discriminator = this.w18_1;
  if (!(discriminator == null)) {
    var tmp0_elvis_lhs = this.x18_1;
    encodeTypeInfo(this, discriminator, tmp0_elvis_lhs == null ? descriptor.kl() : tmp0_elvis_lhs);
    this.w18_1 = null;
    this.x18_1 = null;
  }
  if (this.r18_1.equals(newMode)) {
    return this;
  }
  var tmp1_safe_receiver = this.s18_1;
  var tmp2_elvis_lhs = tmp1_safe_receiver == null ? null : tmp1_safe_receiver[newMode.o2_1];
  return tmp2_elvis_lhs == null ? new StreamingJsonEncoder(this.p18_1, this.q18_1, newMode, this.s18_1) : tmp2_elvis_lhs;
};
protoOf(StreamingJsonEncoder).en = function (descriptor) {
  if (!(this.r18_1.e1b_1 === _Char___init__impl__6a9atx(0))) {
    this.p18_1.u16();
    this.p18_1.w16();
    this.p18_1.y16(this.r18_1.e1b_1);
  }
};
protoOf(StreamingJsonEncoder).wn = function (descriptor, index) {
  switch (this.r18_1.o2_1) {
    case 1:
      if (!this.p18_1.s16_1) {
        this.p18_1.y16(_Char___init__impl__6a9atx(44));
      }

      this.p18_1.v16();
      break;
    case 2:
      if (!this.p18_1.s16_1) {
        var tmp = this;
        var tmp_0;
        if ((index % 2 | 0) === 0) {
          this.p18_1.y16(_Char___init__impl__6a9atx(44));
          this.p18_1.v16();
          tmp_0 = true;
        } else {
          this.p18_1.y16(_Char___init__impl__6a9atx(58));
          this.p18_1.x16();
          tmp_0 = false;
        }
        tmp.v18_1 = tmp_0;
      } else {
        this.v18_1 = true;
        this.p18_1.v16();
      }

      break;
    case 3:
      if (index === 0)
        this.v18_1 = true;
      if (index === 1) {
        this.p18_1.y16(_Char___init__impl__6a9atx(44));
        this.p18_1.x16();
        this.v18_1 = false;
      }

      break;
    default:
      if (!this.p18_1.s16_1) {
        this.p18_1.y16(_Char___init__impl__6a9atx(44));
      }

      this.p18_1.v16();
      this.ho(getJsonElementName(descriptor, this.q18_1, index));
      this.p18_1.y16(_Char___init__impl__6a9atx(58));
      this.p18_1.x16();
      break;
  }
  return true;
};
protoOf(StreamingJsonEncoder).vo = function (descriptor, index, serializer, value) {
  if (!(value == null) || this.u18_1.z14_1) {
    protoOf(AbstractEncoder).vo.call(this, descriptor, index, serializer, value);
  }
};
protoOf(StreamingJsonEncoder).io = function (descriptor) {
  var tmp;
  if (get_isUnsignedNumber(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_0;
    var tmp_1 = this.p18_1;
    if (tmp_1 instanceof ComposerForUnsignedNumbers) {
      tmp_0 = this.p18_1;
    } else {
      var p0 = this.p18_1.r16_1;
      var p1 = this.v18_1;
      tmp_0 = new ComposerForUnsignedNumbers(p0, p1);
    }
    var tmp$ret$1 = tmp_0;
    tmp = new StreamingJsonEncoder(tmp$ret$1, this.q18_1, this.r18_1, null);
  } else if (get_isUnquotedLiteral(descriptor)) {
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.composerAs' call
    var tmp_2;
    var tmp_3 = this.p18_1;
    if (tmp_3 instanceof ComposerForUnquotedLiterals) {
      tmp_2 = this.p18_1;
    } else {
      var p0_0 = this.p18_1.r16_1;
      var p1_0 = this.v18_1;
      tmp_2 = new ComposerForUnquotedLiterals(p0_0, p1_0);
    }
    var tmp$ret$3 = tmp_2;
    tmp = new StreamingJsonEncoder(tmp$ret$3, this.q18_1, this.r18_1, null);
  } else if (!(this.w18_1 == null)) {
    // Inline function 'kotlin.apply' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlinx.serialization.json.internal.StreamingJsonEncoder.encodeInline.<anonymous>' call
    this.x18_1 = descriptor.kl();
    tmp = this;
  } else {
    tmp = protoOf(AbstractEncoder).io.call(this, descriptor);
  }
  return tmp;
};
protoOf(StreamingJsonEncoder).yn = function () {
  this.p18_1.a17('null');
};
protoOf(StreamingJsonEncoder).zn = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.j17(value);
  }
};
protoOf(StreamingJsonEncoder).ao = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.e17(value);
  }
};
protoOf(StreamingJsonEncoder).bo = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.g17(value);
  }
};
protoOf(StreamingJsonEncoder).co = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.h17(value);
  }
};
protoOf(StreamingJsonEncoder).do = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.i17(value);
  }
};
protoOf(StreamingJsonEncoder).eo = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.c17(value);
  }
  if (!this.u18_1.e15_1 && !isFinite(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.p18_1.r16_1));
  }
};
protoOf(StreamingJsonEncoder).fo = function (value) {
  if (this.v18_1) {
    this.ho(value.toString());
  } else {
    this.p18_1.d17(value);
  }
  if (!this.u18_1.e15_1 && !isFinite_0(value)) {
    throw InvalidFloatingPointEncoded(value, toString(this.p18_1.r16_1));
  }
};
protoOf(StreamingJsonEncoder).go = function (value) {
  this.ho(toString_1(value));
};
protoOf(StreamingJsonEncoder).ho = function (value) {
  return this.p18_1.k17(value);
};
function get_isUnsignedNumber(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.ml() && get_unsignedNumberDescriptors().u(_this__u8e3s4);
}
function get_isUnquotedLiteral(_this__u8e3s4) {
  _init_properties_StreamingJsonEncoder_kt__pn1bsi();
  return _this__u8e3s4.ml() && equals(_this__u8e3s4, get_jsonUnquotedLiteralDescriptor());
}
var properties_initialized_StreamingJsonEncoder_kt_6ifwwk;
function _init_properties_StreamingJsonEncoder_kt__pn1bsi() {
  if (!properties_initialized_StreamingJsonEncoder_kt_6ifwwk) {
    properties_initialized_StreamingJsonEncoder_kt_6ifwwk = true;
    unsignedNumberDescriptors = setOf([serializer_1(Companion_getInstance_0()).gk(), serializer_0(Companion_getInstance()).gk(), serializer_2(Companion_getInstance_1()).gk(), serializer_3(Companion_getInstance_2()).gk()]);
  }
}
function get_ESCAPE_STRINGS() {
  _init_properties_StringOps_kt__fcy1db();
  return ESCAPE_STRINGS;
}
var ESCAPE_STRINGS;
var ESCAPE_MARKERS;
function toHexChar(i) {
  _init_properties_StringOps_kt__fcy1db();
  var d = i & 15;
  var tmp;
  if (d < 10) {
    // Inline function 'kotlin.code' call
    var this_0 = _Char___init__impl__6a9atx(48);
    var tmp$ret$0 = Char__toInt_impl_vasixd(this_0);
    tmp = numberToChar(d + tmp$ret$0 | 0);
  } else {
    var tmp_0 = d - 10 | 0;
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(97);
    var tmp$ret$1 = Char__toInt_impl_vasixd(this_1);
    tmp = numberToChar(tmp_0 + tmp$ret$1 | 0);
  }
  return tmp;
}
function printQuoted(_this__u8e3s4, value) {
  _init_properties_StringOps_kt__fcy1db();
  _this__u8e3s4.b8(_Char___init__impl__6a9atx(34));
  var lastPos = 0;
  var inductionVariable = 0;
  var last = charSequenceLength(value) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'kotlin.code' call
      var this_0 = charSequenceGet(value, i);
      var c = Char__toInt_impl_vasixd(this_0);
      if (c < get_ESCAPE_STRINGS().length && !(get_ESCAPE_STRINGS()[c] == null)) {
        _this__u8e3s4.sa(value, lastPos, i);
        _this__u8e3s4.a8(get_ESCAPE_STRINGS()[c]);
        lastPos = i + 1 | 0;
      }
    }
     while (inductionVariable <= last);
  if (!(lastPos === 0))
    _this__u8e3s4.sa(value, lastPos, value.length);
  else
    _this__u8e3s4.a8(value);
  _this__u8e3s4.b8(_Char___init__impl__6a9atx(34));
}
function toBooleanStrictOrNull_0(_this__u8e3s4) {
  _init_properties_StringOps_kt__fcy1db();
  return equals_0(_this__u8e3s4, 'true', true) ? true : equals_0(_this__u8e3s4, 'false', true) ? false : null;
}
var properties_initialized_StringOps_kt_wzaea7;
function _init_properties_StringOps_kt__fcy1db() {
  if (!properties_initialized_StringOps_kt_wzaea7) {
    properties_initialized_StringOps_kt_wzaea7 = true;
    // Inline function 'kotlin.apply' call
    // Inline function 'kotlin.arrayOfNulls' call
    var this_0 = fillArrayVal(Array(93), null);
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlinx.serialization.json.internal.ESCAPE_STRINGS.<anonymous>' call
    var inductionVariable = 0;
    if (inductionVariable <= 31)
      do {
        var c = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        var c1 = toHexChar(c >> 12);
        var c2 = toHexChar(c >> 8);
        var c3 = toHexChar(c >> 4);
        var c4 = toHexChar(c);
        this_0[c] = '\\u' + toString_1(c1) + toString_1(c2) + toString_1(c3) + toString_1(c4);
      }
       while (inductionVariable <= 31);
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(34);
    this_0[Char__toInt_impl_vasixd(this_1)] = '\\"';
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(92);
    this_0[Char__toInt_impl_vasixd(this_2)] = '\\\\';
    // Inline function 'kotlin.code' call
    var this_3 = _Char___init__impl__6a9atx(9);
    this_0[Char__toInt_impl_vasixd(this_3)] = '\\t';
    // Inline function 'kotlin.code' call
    var this_4 = _Char___init__impl__6a9atx(8);
    this_0[Char__toInt_impl_vasixd(this_4)] = '\\b';
    // Inline function 'kotlin.code' call
    var this_5 = _Char___init__impl__6a9atx(10);
    this_0[Char__toInt_impl_vasixd(this_5)] = '\\n';
    // Inline function 'kotlin.code' call
    var this_6 = _Char___init__impl__6a9atx(13);
    this_0[Char__toInt_impl_vasixd(this_6)] = '\\r';
    this_0[12] = '\\f';
    ESCAPE_STRINGS = this_0;
    // Inline function 'kotlin.apply' call
    var this_7 = new Int8Array(93);
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlinx.serialization.json.internal.ESCAPE_MARKERS.<anonymous>' call
    var inductionVariable_0 = 0;
    if (inductionVariable_0 <= 31)
      do {
        var c_0 = inductionVariable_0;
        inductionVariable_0 = inductionVariable_0 + 1 | 0;
        this_7[c_0] = 1;
      }
       while (inductionVariable_0 <= 31);
    // Inline function 'kotlin.code' call
    var this_8 = _Char___init__impl__6a9atx(34);
    this_7[Char__toInt_impl_vasixd(this_8)] = 34;
    // Inline function 'kotlin.code' call
    var this_9 = _Char___init__impl__6a9atx(92);
    this_7[Char__toInt_impl_vasixd(this_9)] = 92;
    // Inline function 'kotlin.code' call
    var this_10 = _Char___init__impl__6a9atx(9);
    this_7[Char__toInt_impl_vasixd(this_10)] = 116;
    // Inline function 'kotlin.code' call
    var this_11 = _Char___init__impl__6a9atx(8);
    this_7[Char__toInt_impl_vasixd(this_11)] = 98;
    // Inline function 'kotlin.code' call
    var this_12 = _Char___init__impl__6a9atx(10);
    this_7[Char__toInt_impl_vasixd(this_12)] = 110;
    // Inline function 'kotlin.code' call
    var this_13 = _Char___init__impl__6a9atx(13);
    this_7[Char__toInt_impl_vasixd(this_13)] = 114;
    this_7[12] = 102;
    ESCAPE_MARKERS = this_7;
  }
}
function unparsedPrimitive($this, literal, primitive, tag) {
  var type = startsWith(primitive, 'i') ? 'an ' + primitive : 'a ' + primitive;
  throw JsonDecodingException_0(-1, "Failed to parse literal '" + literal.toString() + "' as " + type + ' value at element: ' + $this.p1b(tag), toString($this.q1b()));
}
function AbstractJsonTreeDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  NamedValueDecoder.call(this);
  this.l1b_1 = json;
  this.m1b_1 = value;
  this.n1b_1 = polymorphicDiscriminator;
  this.o1b_1 = this.l15().h13_1;
}
protoOf(AbstractJsonTreeDecoder).l15 = function () {
  return this.l1b_1;
};
protoOf(AbstractJsonTreeDecoder).z1 = function () {
  return this.m1b_1;
};
protoOf(AbstractJsonTreeDecoder).sn = function () {
  return this.l15().sn();
};
protoOf(AbstractJsonTreeDecoder).q1b = function () {
  var tmp0_safe_receiver = this.p10();
  var tmp;
  if (tmp0_safe_receiver == null) {
    tmp = null;
  } else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.currentObject.<anonymous>' call
    tmp = this.r1b(tmp0_safe_receiver);
  }
  var tmp1_elvis_lhs = tmp;
  return tmp1_elvis_lhs == null ? this.z1() : tmp1_elvis_lhs;
};
protoOf(AbstractJsonTreeDecoder).p1b = function (currentTag) {
  return this.r10() + ('.' + currentTag);
};
protoOf(AbstractJsonTreeDecoder).m15 = function () {
  return this.q1b();
};
protoOf(AbstractJsonTreeDecoder).cn = function (deserializer) {
  var tmp$ret$0;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.decodeSerializableValuePolymorphic' call
    var tmp;
    if (!(deserializer instanceof AbstractPolymorphicSerializer)) {
      tmp = true;
    } else {
      tmp = this.l15().h13_1.c15_1;
    }
    if (tmp) {
      tmp$ret$0 = deserializer.ik(this);
      break $l$block;
    }
    var discriminator = classDiscriminator(deserializer.gk(), this.l15());
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.m15();
    var serialName = deserializer.gk().kl();
    if (!(value instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).h9();
      var tmp_1 = getKClassFromExpression(value).h9();
      var tmp$ret$1 = this.r10();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$1, toString(value));
    }
    var jsonTree = value;
    var tmp0_safe_receiver = jsonTree.fc(discriminator);
    var tmp1_safe_receiver = tmp0_safe_receiver == null ? null : get_jsonPrimitive(tmp0_safe_receiver);
    var type = tmp1_safe_receiver == null ? null : get_contentOrNull(tmp1_safe_receiver);
    var tmp_2;
    try {
      tmp_2 = findPolymorphicSerializer(deserializer, this, type);
    } catch ($p) {
      var tmp_3;
      if ($p instanceof SerializationException) {
        var it = $p;
        throw JsonDecodingException_0(-1, ensureNotNull(it.message), jsonTree.toString());
      } else {
        throw $p;
      }
    }
    var tmp_4 = tmp_2;
    var actualSerializer = isInterface(tmp_4, DeserializationStrategy) ? tmp_4 : THROW_CCE();
    tmp$ret$0 = readPolymorphicJson(this.l15(), discriminator, jsonTree, actualSerializer);
  }
  return tmp$ret$0;
};
protoOf(AbstractJsonTreeDecoder).q10 = function (parentName, childName) {
  return childName;
};
protoOf(AbstractJsonTreeDecoder).dn = function (descriptor) {
  var currentObject = this.q1b();
  var tmp0_subject = descriptor.ll();
  var tmp;
  var tmp_0;
  if (equals(tmp0_subject, LIST_getInstance())) {
    tmp_0 = true;
  } else {
    tmp_0 = tmp0_subject instanceof PolymorphicKind;
  }
  if (tmp_0) {
    var tmp_1 = this.l15();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = descriptor.kl();
    if (!(currentObject instanceof JsonArray)) {
      var tmp_2 = getKClass(JsonArray).h9();
      var tmp_3 = getKClassFromExpression(currentObject).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.r10();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_2 + ', but had ' + tmp_3 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$0, toString(currentObject));
    }
    tmp = new JsonTreeListDecoder(tmp_1, currentObject);
  } else {
    if (equals(tmp0_subject, MAP_getInstance())) {
      // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
      var this_0 = this.l15();
      var keyDescriptor = carrierDescriptor(descriptor.sl(0), this_0.sn());
      var keyKind = keyDescriptor.ll();
      var tmp_4;
      var tmp_5;
      if (keyKind instanceof PrimitiveKind) {
        tmp_5 = true;
      } else {
        tmp_5 = equals(keyKind, ENUM_getInstance());
      }
      if (tmp_5) {
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.beginStructure.<anonymous>' call
        var tmp_6 = this.l15();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
        // Inline function 'kotlinx.serialization.json.internal.cast' call
        var serialName_0 = descriptor.kl();
        if (!(currentObject instanceof JsonObject)) {
          var tmp_7 = getKClass(JsonObject).h9();
          var tmp_8 = getKClassFromExpression(currentObject).h9();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
          var tmp$ret$3 = this.r10();
          throw JsonDecodingException_0(-1, 'Expected ' + tmp_7 + ', but had ' + tmp_8 + ' as the serialized body of ' + serialName_0 + ' at element: ' + tmp$ret$3, toString(currentObject));
        }
        tmp_4 = new JsonTreeMapDecoder(tmp_6, currentObject);
      } else {
        if (this_0.h13_1.x14_1) {
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.beginStructure.<anonymous>' call
          var tmp_9 = this.l15();
          // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
          // Inline function 'kotlinx.serialization.json.internal.cast' call
          var serialName_1 = descriptor.kl();
          if (!(currentObject instanceof JsonArray)) {
            var tmp_10 = getKClass(JsonArray).h9();
            var tmp_11 = getKClassFromExpression(currentObject).h9();
            // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
            var tmp$ret$7 = this.r10();
            throw JsonDecodingException_0(-1, 'Expected ' + tmp_10 + ', but had ' + tmp_11 + ' as the serialized body of ' + serialName_1 + ' at element: ' + tmp$ret$7, toString(currentObject));
          }
          tmp_4 = new JsonTreeListDecoder(tmp_9, currentObject);
        } else {
          throw InvalidKeyKindException(keyDescriptor);
        }
      }
      tmp = tmp_4;
    } else {
      var tmp_12 = this.l15();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
      // Inline function 'kotlinx.serialization.json.internal.cast' call
      var serialName_2 = descriptor.kl();
      if (!(currentObject instanceof JsonObject)) {
        var tmp_13 = getKClass(JsonObject).h9();
        var tmp_14 = getKClassFromExpression(currentObject).h9();
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
        var tmp$ret$12 = this.r10();
        throw JsonDecodingException_0(-1, 'Expected ' + tmp_13 + ', but had ' + tmp_14 + ' as the serialized body of ' + serialName_2 + ' at element: ' + tmp$ret$12, toString(currentObject));
      }
      tmp = new JsonTreeDecoder(tmp_12, currentObject, this.n1b_1);
    }
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).en = function (descriptor) {
};
protoOf(AbstractJsonTreeDecoder).pm = function () {
  var tmp = this.q1b();
  return !(tmp instanceof JsonNull);
};
protoOf(AbstractJsonTreeDecoder).s1b = function (tag) {
  return !(this.r1b(tag) === JsonNull_getInstance());
};
protoOf(AbstractJsonTreeDecoder).t10 = function (tag) {
  return this.s1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).t1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'boolean' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      var tmp0_elvis_lhs = get_booleanOrNull(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'boolean', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'boolean', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).u10 = function (tag) {
  return this.t1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).u1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'byte' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedByte.<anonymous>' call
      var result = get_int(literal);
      var tmp0_elvis_lhs = (-128 <= result ? result <= 127 : false) ? toByte(result) : null;
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'byte', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'byte', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).v10 = function (tag) {
  return this.u1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).v1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'short' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedShort.<anonymous>' call
      var result = get_int(literal);
      var tmp0_elvis_lhs = (-32768 <= result ? result <= 32767 : false) ? toShort(result) : null;
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'short', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'short', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).w10 = function (tag) {
  return this.v1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).w1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'int' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedInt.<anonymous>' call
      var tmp0_elvis_lhs = get_int(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'int', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'int', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).x10 = function (tag) {
  return this.w1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).x1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'long' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedLong.<anonymous>' call
      var tmp0_elvis_lhs = get_long(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'long', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'long', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).y10 = function (tag) {
  return this.x1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).y1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'float' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedFloat.<anonymous>' call
      var tmp0_elvis_lhs = get_float(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'float', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'float', tag);
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$4;
  var specialFp = this.l15().h13_1.e15_1;
  if (specialFp || isFinite(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.q1b()));
};
protoOf(AbstractJsonTreeDecoder).z10 = function (tag) {
  return this.y1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).z1b = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'double' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedDouble.<anonymous>' call
      var tmp0_elvis_lhs = get_double(literal);
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'double', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'double', tag);
      } else {
        throw $p;
      }
    }
  }
  var result = tmp$ret$4;
  var specialFp = this.l15().h13_1.e15_1;
  if (specialFp || isFinite_0(result))
    return result;
  throw InvalidFloatingPointDecoded(result, tag, toString(this.q1b()));
};
protoOf(AbstractJsonTreeDecoder).a11 = function (tag) {
  return this.z1b((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).a1c = function (tag) {
  var tmp$ret$4;
  $l$block: {
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var value = this.r1b(tag);
    if (!(value instanceof JsonPrimitive)) {
      var tmp = getKClass(JsonPrimitive).h9();
      var tmp_0 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'char' + ' at element: ' + tmp$ret$0, toString(value));
    }
    var literal = value;
    try {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.decodeTaggedChar.<anonymous>' call
      var tmp0_elvis_lhs = new Char(single(literal.t15()));
      var tmp_1;
      if (tmp0_elvis_lhs == null) {
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        tmp_1 = tmp0_elvis_lhs;
      }
      tmp$ret$4 = tmp_1.k1_1;
      break $l$block;
    } catch ($p) {
      if ($p instanceof IllegalArgumentException) {
        var e = $p;
        unparsedPrimitive(this, literal, 'char', tag);
      } else {
        throw $p;
      }
    }
  }
  return tmp$ret$4;
};
protoOf(AbstractJsonTreeDecoder).b11 = function (tag) {
  return this.a1c((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).b1c = function (tag) {
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
  // Inline function 'kotlinx.serialization.json.internal.cast' call
  var value = this.r1b(tag);
  if (!(value instanceof JsonPrimitive)) {
    var tmp = getKClass(JsonPrimitive).h9();
    var tmp_0 = getKClassFromExpression(value).h9();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
    var tmp$ret$0 = this.p1b(tag);
    throw JsonDecodingException_0(-1, 'Expected ' + tmp + ', but had ' + tmp_0 + ' as the serialized body of ' + 'string' + ' at element: ' + tmp$ret$0, toString(value));
  }
  var value_0 = value;
  if (!(value_0 instanceof JsonLiteral))
    throw JsonDecodingException_0(-1, "Expected string value for a non-null key '" + tag + "', got null literal instead at element: " + this.p1b(tag), toString(this.q1b()));
  if (!value_0.w15_1 && !this.l15().h13_1.w14_1) {
    throw JsonDecodingException_0(-1, "String literal for key '" + tag + "' should be quoted at element: " + this.p1b(tag) + ".\nUse 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.", toString(this.q1b()));
  }
  return value_0.y15_1;
};
protoOf(AbstractJsonTreeDecoder).c11 = function (tag) {
  return this.b1c((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE());
};
protoOf(AbstractJsonTreeDecoder).c1c = function (tag, inlineDescriptor) {
  var tmp;
  if (get_isUnsignedNumber(inlineDescriptor)) {
    var tmp_0 = this.l15();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.getPrimitiveValue' call
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    var value = this.r1b(tag);
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = inlineDescriptor.kl();
    if (!(value instanceof JsonPrimitive)) {
      var tmp_1 = getKClass(JsonPrimitive).h9();
      var tmp_2 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.p1b(tag);
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_1 + ', but had ' + tmp_2 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$0, toString(value));
    }
    var lexer = StringJsonLexer_0(tmp_0, value.t15());
    tmp = new JsonDecoderForUnsignedTypes(lexer, this.l15());
  } else {
    tmp = protoOf(NamedValueDecoder).d11.call(this, tag, inlineDescriptor);
  }
  return tmp;
};
protoOf(AbstractJsonTreeDecoder).d11 = function (tag, inlineDescriptor) {
  return this.c1c((!(tag == null) ? typeof tag === 'string' : false) ? tag : THROW_CCE(), inlineDescriptor);
};
protoOf(AbstractJsonTreeDecoder).an = function (descriptor) {
  return !(this.p10() == null) ? protoOf(NamedValueDecoder).an.call(this, descriptor) : (new JsonPrimitiveDecoder(this.l15(), this.z1(), this.n1b_1)).an(descriptor);
};
function coerceInputValue_0($this, descriptor, index, tag) {
  var tmp$ret$1;
  $l$block_2: {
    // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue' call
    var this_0 = $this.l15();
    var isOptional = descriptor.tl(index);
    var elementDescriptor = descriptor.sl(index);
    var tmp;
    if (isOptional && !elementDescriptor.gl()) {
      // Inline function 'kotlinx.serialization.json.internal.JsonTreeDecoder.coerceInputValue.<anonymous>' call
      var tmp_0 = $this.r1b(tag);
      tmp = tmp_0 instanceof JsonNull;
    } else {
      tmp = false;
    }
    if (tmp) {
      tmp$ret$1 = true;
      break $l$block_2;
    }
    if (equals(elementDescriptor.ll(), ENUM_getInstance())) {
      var tmp_1;
      if (elementDescriptor.gl()) {
        // Inline function 'kotlinx.serialization.json.internal.JsonTreeDecoder.coerceInputValue.<anonymous>' call
        var tmp_2 = $this.r1b(tag);
        tmp_1 = tmp_2 instanceof JsonNull;
      } else {
        tmp_1 = false;
      }
      if (tmp_1) {
        tmp$ret$1 = false;
        break $l$block_2;
      }
      // Inline function 'kotlinx.serialization.json.internal.JsonTreeDecoder.coerceInputValue.<anonymous>' call
      var tmp_3 = $this.r1b(tag);
      var tmp0_safe_receiver = tmp_3 instanceof JsonPrimitive ? tmp_3 : null;
      var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : get_contentOrNull(tmp0_safe_receiver);
      var tmp_4;
      if (tmp0_elvis_lhs == null) {
        tmp$ret$1 = false;
        break $l$block_2;
      } else {
        tmp_4 = tmp0_elvis_lhs;
      }
      var enumValue = tmp_4;
      var enumIndex = getJsonNameIndex(elementDescriptor, this_0, enumValue);
      var coerceToNull = !this_0.h13_1.z14_1 && elementDescriptor.gl();
      if (enumIndex === -3 && (isOptional || coerceToNull)) {
        // Inline function 'kotlinx.serialization.json.internal.tryCoerceValue.<anonymous>' call
        tmp$ret$1 = true;
        break $l$block_2;
      }
    }
    tmp$ret$1 = false;
  }
  return tmp$ret$1;
}
function absenceIsNull($this, descriptor, index) {
  $this.m1c_1 = (!$this.l15().h13_1.z14_1 && !descriptor.tl(index) && descriptor.sl(index).gl());
  return $this.m1c_1;
}
function JsonTreeDecoder(json, value, polymorphicDiscriminator, polyDescriptor) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  polyDescriptor = polyDescriptor === VOID ? null : polyDescriptor;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.j1c_1 = value;
  this.k1c_1 = polyDescriptor;
  this.l1c_1 = 0;
  this.m1c_1 = false;
}
protoOf(JsonTreeDecoder).z1 = function () {
  return this.j1c_1;
};
protoOf(JsonTreeDecoder).un = function (descriptor) {
  while (this.l1c_1 < descriptor.nl()) {
    var _unary__edvuaz = this.l1c_1;
    this.l1c_1 = _unary__edvuaz + 1 | 0;
    var name = this.k10(descriptor, _unary__edvuaz);
    var index = this.l1c_1 - 1 | 0;
    this.m1c_1 = false;
    var tmp;
    var tmp_0;
    // Inline function 'kotlin.collections.contains' call
    // Inline function 'kotlin.collections.containsKey' call
    var this_0 = this.z1();
    if ((isInterface(this_0, KtMap) ? this_0 : THROW_CCE()).a2(name)) {
      tmp_0 = true;
    } else {
      tmp_0 = absenceIsNull(this, descriptor, index);
    }
    if (tmp_0) {
      tmp = !this.o1b_1.b15_1 || !coerceInputValue_0(this, descriptor, index, name);
    } else {
      tmp = false;
    }
    if (tmp) {
      return index;
    }
  }
  return -1;
};
protoOf(JsonTreeDecoder).pm = function () {
  return !this.m1c_1 && protoOf(AbstractJsonTreeDecoder).pm.call(this);
};
protoOf(JsonTreeDecoder).l10 = function (descriptor, index) {
  var strategy = namingStrategy(descriptor, this.l15());
  var baseName = descriptor.pl(index);
  if (strategy == null) {
    if (!this.o1b_1.f15_1)
      return baseName;
    if (this.z1().d2().u(baseName))
      return baseName;
  }
  var deserializationNamesMap_0 = deserializationNamesMap(this.l15(), descriptor);
  // Inline function 'kotlin.collections.find' call
  var this_0 = this.z1().d2();
  var tmp$ret$1;
  $l$block: {
    // Inline function 'kotlin.collections.firstOrNull' call
    var tmp0_iterator = this_0.j();
    while (tmp0_iterator.k()) {
      var element = tmp0_iterator.l();
      // Inline function 'kotlinx.serialization.json.internal.JsonTreeDecoder.elementName.<anonymous>' call
      if (deserializationNamesMap_0.c2(element) === index) {
        tmp$ret$1 = element;
        break $l$block;
      }
    }
    tmp$ret$1 = null;
  }
  var tmp0_safe_receiver = tmp$ret$1;
  if (tmp0_safe_receiver == null)
    null;
  else {
    // Inline function 'kotlin.let' call
    // Inline function 'kotlin.contracts.contract' call
    return tmp0_safe_receiver;
  }
  var fallbackName = strategy == null ? null : strategy.f18(descriptor, index, baseName);
  return fallbackName == null ? baseName : fallbackName;
};
protoOf(JsonTreeDecoder).r1b = function (tag) {
  return getValue(this.z1(), tag);
};
protoOf(JsonTreeDecoder).dn = function (descriptor) {
  if (descriptor === this.k1c_1) {
    var tmp = this.l15();
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast' call
    var value = this.q1b();
    // Inline function 'kotlinx.serialization.json.internal.cast' call
    var serialName = this.k1c_1.kl();
    if (!(value instanceof JsonObject)) {
      var tmp_0 = getKClass(JsonObject).h9();
      var tmp_1 = getKClassFromExpression(value).h9();
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonTreeDecoder.cast.<anonymous>' call
      var tmp$ret$0 = this.r10();
      throw JsonDecodingException_0(-1, 'Expected ' + tmp_0 + ', but had ' + tmp_1 + ' as the serialized body of ' + serialName + ' at element: ' + tmp$ret$0, toString(value));
    }
    return new JsonTreeDecoder(tmp, value, this.n1b_1, this.k1c_1);
  }
  return protoOf(AbstractJsonTreeDecoder).dn.call(this, descriptor);
};
protoOf(JsonTreeDecoder).en = function (descriptor) {
  var tmp;
  if (this.o1b_1.v14_1) {
    tmp = true;
  } else {
    var tmp_0 = descriptor.ll();
    tmp = tmp_0 instanceof PolymorphicKind;
  }
  if (tmp)
    return Unit_instance;
  var strategy = namingStrategy(descriptor, this.l15());
  var tmp_1;
  if (strategy == null && !this.o1b_1.f15_1) {
    tmp_1 = jsonCachedSerialNames(descriptor);
  } else if (!(strategy == null)) {
    tmp_1 = deserializationNamesMap(this.l15(), descriptor).d2();
  } else {
    var tmp_2 = jsonCachedSerialNames(descriptor);
    // Inline function 'kotlin.collections.orEmpty' call
    var tmp0_safe_receiver = get_schemaCache(this.l15()).q1a(descriptor, get_JsonDeserializationNamesKey());
    var tmp0_elvis_lhs = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.d2();
    var tmp$ret$0 = tmp0_elvis_lhs == null ? emptySet() : tmp0_elvis_lhs;
    tmp_1 = plus_0(tmp_2, tmp$ret$0);
  }
  var names = tmp_1;
  var _iterator__ex2g4s = this.z1().d2().j();
  while (_iterator__ex2g4s.k()) {
    var key = _iterator__ex2g4s.l();
    if (!names.u(key) && !(key === this.n1b_1)) {
      throw UnknownKeyException(key, this.z1().toString());
    }
  }
};
function JsonTreeListDecoder(json, value) {
  AbstractJsonTreeDecoder.call(this, json, value);
  this.t1c_1 = value;
  this.u1c_1 = this.t1c_1.m();
  this.v1c_1 = -1;
}
protoOf(JsonTreeListDecoder).z1 = function () {
  return this.t1c_1;
};
protoOf(JsonTreeListDecoder).l10 = function (descriptor, index) {
  return index.toString();
};
protoOf(JsonTreeListDecoder).r1b = function (tag) {
  return this.t1c_1.o(toInt(tag));
};
protoOf(JsonTreeListDecoder).un = function (descriptor) {
  while (this.v1c_1 < (this.u1c_1 - 1 | 0)) {
    this.v1c_1 = this.v1c_1 + 1 | 0;
    return this.v1c_1;
  }
  return -1;
};
function JsonPrimitiveDecoder(json, value, polymorphicDiscriminator) {
  polymorphicDiscriminator = polymorphicDiscriminator === VOID ? null : polymorphicDiscriminator;
  AbstractJsonTreeDecoder.call(this, json, value, polymorphicDiscriminator);
  this.c1d_1 = value;
  this.e11('primitive');
}
protoOf(JsonPrimitiveDecoder).z1 = function () {
  return this.c1d_1;
};
protoOf(JsonPrimitiveDecoder).un = function (descriptor) {
  return 0;
};
protoOf(JsonPrimitiveDecoder).r1b = function (tag) {
  // Inline function 'kotlin.require' call
  // Inline function 'kotlin.contracts.contract' call
  if (!(tag === 'primitive')) {
    // Inline function 'kotlinx.serialization.json.internal.JsonPrimitiveDecoder.currentElement.<anonymous>' call
    var message = "This input can only handle primitives with 'primitive' tag";
    throw IllegalArgumentException_init_$Create$(toString(message));
  }
  return this.c1d_1;
};
function JsonTreeMapDecoder(json, value) {
  JsonTreeDecoder.call(this, json, value);
  this.n1d_1 = value;
  this.o1d_1 = toList(this.n1d_1.d2());
  this.p1d_1 = imul(this.o1d_1.m(), 2);
  this.q1d_1 = -1;
}
protoOf(JsonTreeMapDecoder).z1 = function () {
  return this.n1d_1;
};
protoOf(JsonTreeMapDecoder).l10 = function (descriptor, index) {
  var i = index / 2 | 0;
  return this.o1d_1.o(i);
};
protoOf(JsonTreeMapDecoder).un = function (descriptor) {
  while (this.q1d_1 < (this.p1d_1 - 1 | 0)) {
    this.q1d_1 = this.q1d_1 + 1 | 0;
    return this.q1d_1;
  }
  return -1;
};
protoOf(JsonTreeMapDecoder).r1b = function (tag) {
  return (this.q1d_1 % 2 | 0) === 0 ? JsonPrimitive_0(tag) : getValue(this.n1d_1, tag);
};
protoOf(JsonTreeMapDecoder).en = function (descriptor) {
};
function readPolymorphicJson(_this__u8e3s4, discriminator, element, deserializer) {
  return (new JsonTreeDecoder(_this__u8e3s4, element, discriminator, deserializer.gk())).cn(deserializer);
}
var WriteMode_OBJ_instance;
var WriteMode_LIST_instance;
var WriteMode_MAP_instance;
var WriteMode_POLY_OBJ_instance;
function values() {
  return [WriteMode_OBJ_getInstance(), WriteMode_LIST_getInstance(), WriteMode_MAP_getInstance(), WriteMode_POLY_OBJ_getInstance()];
}
function get_entries() {
  if ($ENTRIES == null)
    $ENTRIES = enumEntries(values());
  return $ENTRIES;
}
var WriteMode_entriesInitialized;
function WriteMode_initEntries() {
  if (WriteMode_entriesInitialized)
    return Unit_instance;
  WriteMode_entriesInitialized = true;
  WriteMode_OBJ_instance = new WriteMode('OBJ', 0, _Char___init__impl__6a9atx(123), _Char___init__impl__6a9atx(125));
  WriteMode_LIST_instance = new WriteMode('LIST', 1, _Char___init__impl__6a9atx(91), _Char___init__impl__6a9atx(93));
  WriteMode_MAP_instance = new WriteMode('MAP', 2, _Char___init__impl__6a9atx(123), _Char___init__impl__6a9atx(125));
  WriteMode_POLY_OBJ_instance = new WriteMode('POLY_OBJ', 3, _Char___init__impl__6a9atx(91), _Char___init__impl__6a9atx(93));
}
var $ENTRIES;
function WriteMode(name, ordinal, begin, end) {
  Enum.call(this, name, ordinal);
  this.d1b_1 = begin;
  this.e1b_1 = end;
}
function switchMode(_this__u8e3s4, desc) {
  var tmp0_subject = desc.ll();
  var tmp;
  if (tmp0_subject instanceof PolymorphicKind) {
    tmp = WriteMode_POLY_OBJ_getInstance();
  } else {
    if (equals(tmp0_subject, LIST_getInstance())) {
      tmp = WriteMode_LIST_getInstance();
    } else {
      if (equals(tmp0_subject, MAP_getInstance())) {
        // Inline function 'kotlinx.serialization.json.internal.selectMapMode' call
        var keyDescriptor = carrierDescriptor(desc.sl(0), _this__u8e3s4.sn());
        var keyKind = keyDescriptor.ll();
        var tmp_0;
        var tmp_1;
        if (keyKind instanceof PrimitiveKind) {
          tmp_1 = true;
        } else {
          tmp_1 = equals(keyKind, ENUM_getInstance());
        }
        if (tmp_1) {
          // Inline function 'kotlinx.serialization.json.internal.switchMode.<anonymous>' call
          tmp_0 = WriteMode_MAP_getInstance();
        } else {
          if (_this__u8e3s4.h13_1.x14_1) {
            // Inline function 'kotlinx.serialization.json.internal.switchMode.<anonymous>' call
            tmp_0 = WriteMode_LIST_getInstance();
          } else {
            throw InvalidKeyKindException(keyDescriptor);
          }
        }
        tmp = tmp_0;
      } else {
        tmp = WriteMode_OBJ_getInstance();
      }
    }
  }
  return tmp;
}
function carrierDescriptor(_this__u8e3s4, module_0) {
  var tmp;
  if (equals(_this__u8e3s4.ll(), CONTEXTUAL_getInstance())) {
    var tmp0_safe_receiver = getContextualDescriptor(module_0, _this__u8e3s4);
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : carrierDescriptor(tmp0_safe_receiver, module_0);
    tmp = tmp1_elvis_lhs == null ? _this__u8e3s4 : tmp1_elvis_lhs;
  } else if (_this__u8e3s4.ml()) {
    tmp = carrierDescriptor(_this__u8e3s4.sl(0), module_0);
  } else {
    tmp = _this__u8e3s4;
  }
  return tmp;
}
function WriteMode_OBJ_getInstance() {
  WriteMode_initEntries();
  return WriteMode_OBJ_instance;
}
function WriteMode_LIST_getInstance() {
  WriteMode_initEntries();
  return WriteMode_LIST_instance;
}
function WriteMode_MAP_getInstance() {
  WriteMode_initEntries();
  return WriteMode_MAP_instance;
}
function WriteMode_POLY_OBJ_getInstance() {
  WriteMode_initEntries();
  return WriteMode_POLY_OBJ_instance;
}
function appendEscape($this, lastPosition, current) {
  $this.r1d(lastPosition, current);
  return appendEsc($this, current + 1 | 0);
}
function decodedString($this, lastPosition, currentPosition) {
  $this.r1d(lastPosition, currentPosition);
  var result = $this.z13_1.toString();
  $this.z13_1.ya(0);
  return result;
}
function takePeeked($this) {
  // Inline function 'kotlin.also' call
  var this_0 = ensureNotNull($this.y13_1);
  // Inline function 'kotlin.contracts.contract' call
  // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.takePeeked.<anonymous>' call
  $this.y13_1 = null;
  return this_0;
}
function wasUnquotedString($this) {
  return !(charSequenceGet($this.s1d(), $this.w13_1 - 1 | 0) === _Char___init__impl__6a9atx(34));
}
function appendEsc($this, startPosition) {
  var currentPosition = startPosition;
  currentPosition = $this.t1d(currentPosition);
  if (currentPosition === -1) {
    $this.b18('Expected escape sequence to continue, got EOF');
  }
  var tmp = $this.s1d();
  var _unary__edvuaz = currentPosition;
  currentPosition = _unary__edvuaz + 1 | 0;
  var currentChar = charSequenceGet(tmp, _unary__edvuaz);
  if (currentChar === _Char___init__impl__6a9atx(117)) {
    return appendHex($this, $this.s1d(), currentPosition);
  }
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(currentChar);
  var c = escapeToChar(tmp$ret$0);
  if (c === _Char___init__impl__6a9atx(0)) {
    $this.b18("Invalid escaped char '" + toString_1(currentChar) + "'");
  }
  $this.z13_1.b8(c);
  return currentPosition;
}
function appendHex($this, source, startPos) {
  if ((startPos + 4 | 0) >= charSequenceLength(source)) {
    $this.w13_1 = startPos;
    $this.u1d();
    if (($this.w13_1 + 4 | 0) >= charSequenceLength(source)) {
      $this.b18('Unexpected EOF during unicode escape');
    }
    return appendHex($this, source, $this.w13_1);
  }
  $this.z13_1.b8(numberToChar((((fromHexChar($this, source, startPos) << 12) + (fromHexChar($this, source, startPos + 1 | 0) << 8) | 0) + (fromHexChar($this, source, startPos + 2 | 0) << 4) | 0) + fromHexChar($this, source, startPos + 3 | 0) | 0));
  return startPos + 4 | 0;
}
function fromHexChar($this, source, currentPosition) {
  var character = charSequenceGet(source, currentPosition);
  var tmp;
  if (_Char___init__impl__6a9atx(48) <= character ? character <= _Char___init__impl__6a9atx(57) : false) {
    // Inline function 'kotlin.code' call
    var tmp_0 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_0 = _Char___init__impl__6a9atx(48);
    tmp = tmp_0 - Char__toInt_impl_vasixd(this_0) | 0;
  } else if (_Char___init__impl__6a9atx(97) <= character ? character <= _Char___init__impl__6a9atx(102) : false) {
    // Inline function 'kotlin.code' call
    var tmp_1 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_1 = _Char___init__impl__6a9atx(97);
    tmp = (tmp_1 - Char__toInt_impl_vasixd(this_1) | 0) + 10 | 0;
  } else if (_Char___init__impl__6a9atx(65) <= character ? character <= _Char___init__impl__6a9atx(70) : false) {
    // Inline function 'kotlin.code' call
    var tmp_2 = Char__toInt_impl_vasixd(character);
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(65);
    tmp = (tmp_2 - Char__toInt_impl_vasixd(this_2) | 0) + 10 | 0;
  } else {
    $this.b18("Invalid toHexChar char '" + toString_1(character) + "' in unicode escape");
  }
  return tmp;
}
function consumeBoolean2($this, start) {
  var current = $this.t1d(start);
  if (current >= charSequenceLength($this.s1d()) || current === -1) {
    $this.b18('EOF');
  }
  // Inline function 'kotlin.code' call
  var tmp = $this.s1d();
  var _unary__edvuaz = current;
  current = _unary__edvuaz + 1 | 0;
  var this_0 = charSequenceGet(tmp, _unary__edvuaz);
  var tmp0_subject = Char__toInt_impl_vasixd(this_0) | 32;
  var tmp_0;
  // Inline function 'kotlin.code' call
  var this_1 = _Char___init__impl__6a9atx(116);
  if (tmp0_subject === Char__toInt_impl_vasixd(this_1)) {
    consumeBooleanLiteral($this, 'rue', current);
    tmp_0 = true;
  } else {
    // Inline function 'kotlin.code' call
    var this_2 = _Char___init__impl__6a9atx(102);
    if (tmp0_subject === Char__toInt_impl_vasixd(this_2)) {
      consumeBooleanLiteral($this, 'alse', current);
      tmp_0 = false;
    } else {
      $this.b18("Expected valid boolean literal prefix, but had '" + $this.g19() + "'");
    }
  }
  return tmp_0;
}
function consumeBooleanLiteral($this, literalSuffix, current) {
  if ((charSequenceLength($this.s1d()) - current | 0) < literalSuffix.length) {
    $this.b18('Unexpected end of boolean literal');
  }
  var inductionVariable = 0;
  var last = charSequenceLength(literalSuffix) - 1 | 0;
  if (inductionVariable <= last)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      var expected = charSequenceGet(literalSuffix, i);
      var actual = charSequenceGet($this.s1d(), current + i | 0);
      // Inline function 'kotlin.code' call
      var tmp = Char__toInt_impl_vasixd(expected);
      // Inline function 'kotlin.code' call
      if (!(tmp === (Char__toInt_impl_vasixd(actual) | 32))) {
        $this.b18("Expected valid boolean literal prefix, but had '" + $this.g19() + "'");
      }
    }
     while (inductionVariable <= last);
  $this.w13_1 = current + literalSuffix.length | 0;
}
function consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive) {
  var tmp;
  switch (isExponentPositive) {
    case false:
      // Inline function 'kotlin.math.pow' call

      var x = -exponentAccumulator.i3();
      tmp = Math.pow(10.0, x);
      break;
    case true:
      // Inline function 'kotlin.math.pow' call

      var x_0 = exponentAccumulator.i3();
      tmp = Math.pow(10.0, x_0);
      break;
    default:
      noWhenBranchMatchedException();
      break;
  }
  return tmp;
}
function AbstractJsonLexer() {
  this.w13_1 = 0;
  this.x13_1 = new JsonPath();
  this.y13_1 = null;
  this.z13_1 = StringBuilder_init_$Create$();
}
protoOf(AbstractJsonLexer).u1d = function () {
};
protoOf(AbstractJsonLexer).t1a = function () {
  var current = this.v1d();
  var source = this.s1d();
  if (current >= charSequenceLength(source) || current === -1)
    return false;
  if (charSequenceGet(source, current) === _Char___init__impl__6a9atx(44)) {
    this.w13_1 = this.w13_1 + 1 | 0;
    return true;
  }
  return false;
};
protoOf(AbstractJsonLexer).w1d = function (c) {
  return c === _Char___init__impl__6a9atx(125) || c === _Char___init__impl__6a9atx(93) || (c === _Char___init__impl__6a9atx(58) || c === _Char___init__impl__6a9atx(44)) ? false : true;
};
protoOf(AbstractJsonLexer).a14 = function () {
  var nextToken = this.i19();
  if (!(nextToken === 10)) {
    this.b18('Expected EOF after parsing, but had ' + toString_1(charSequenceGet(this.s1d(), this.w13_1 - 1 | 0)) + ' instead');
  }
};
protoOf(AbstractJsonLexer).c19 = function (expected) {
  var token = this.i19();
  if (!(token === expected)) {
    this.x1d(expected);
  }
  return token;
};
protoOf(AbstractJsonLexer).y1d = function (expected) {
  if (this.w13_1 > 0 && expected === _Char___init__impl__6a9atx(34)) {
    var tmp$ret$1;
    $l$block: {
      // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.withPositionRollback' call
      var snapshot = this.w13_1;
      try {
        // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.unexpectedToken.<anonymous>' call
        this.w13_1 = this.w13_1 - 1 | 0;
        tmp$ret$1 = this.g19();
        break $l$block;
      }finally {
        this.w13_1 = snapshot;
      }
    }
    var inputLiteral = tmp$ret$1;
    if (inputLiteral === 'null') {
      this.a18("Expected string literal but 'null' literal was found", this.w13_1 - 1 | 0, "Use 'coerceInputValues = true' in 'Json {}' builder to coerce nulls if property has a default value.");
    }
  }
  this.x1d(charToTokenClass(expected));
};
protoOf(AbstractJsonLexer).z1d = function (expectedToken, wasConsumed) {
  var expected = tokenDescription(expectedToken);
  var position = wasConsumed ? this.w13_1 - 1 | 0 : this.w13_1;
  var s = this.w13_1 === charSequenceLength(this.s1d()) || position < 0 ? 'EOF' : toString_1(charSequenceGet(this.s1d(), position));
  this.b18('Expected ' + expected + ", but had '" + s + "' instead", position);
};
protoOf(AbstractJsonLexer).x1d = function (expectedToken, wasConsumed, $super) {
  wasConsumed = wasConsumed === VOID ? true : wasConsumed;
  return $super === VOID ? this.z1d(expectedToken, wasConsumed) : $super.z1d.call(this, expectedToken, wasConsumed);
};
protoOf(AbstractJsonLexer).d19 = function () {
  var source = this.s1d();
  var cpos = this.w13_1;
  $l$loop_0: while (true) {
    cpos = this.t1d(cpos);
    if (cpos === -1)
      break $l$loop_0;
    var ch = charSequenceGet(source, cpos);
    if (ch === _Char___init__impl__6a9atx(32) || ch === _Char___init__impl__6a9atx(10) || ch === _Char___init__impl__6a9atx(13) || ch === _Char___init__impl__6a9atx(9)) {
      cpos = cpos + 1 | 0;
      continue $l$loop_0;
    }
    this.w13_1 = cpos;
    return charToTokenClass(ch);
  }
  this.w13_1 = cpos;
  return 10;
};
protoOf(AbstractJsonLexer).u1a = function (doConsume) {
  var current = this.v1d();
  current = this.t1d(current);
  var len = charSequenceLength(this.s1d()) - current | 0;
  if (len < 4 || current === -1)
    return false;
  var inductionVariable = 0;
  if (inductionVariable <= 3)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (!(charSequenceGet('null', i) === charSequenceGet(this.s1d(), current + i | 0)))
        return false;
    }
     while (inductionVariable <= 3);
  if (len > 4 && charToTokenClass(charSequenceGet(this.s1d(), current + 4 | 0)) === 0)
    return false;
  if (doConsume) {
    this.w13_1 = current + 4 | 0;
  }
  return true;
};
protoOf(AbstractJsonLexer).f1b = function (doConsume, $super) {
  doConsume = doConsume === VOID ? true : doConsume;
  return $super === VOID ? this.u1a(doConsume) : $super.u1a.call(this, doConsume);
};
protoOf(AbstractJsonLexer).v1a = function (isLenient) {
  var token = this.d19();
  var tmp;
  if (isLenient) {
    if (!(token === 1) && !(token === 0))
      return null;
    tmp = this.g19();
  } else {
    if (!(token === 1))
      return null;
    tmp = this.f19();
  }
  var string = tmp;
  this.y13_1 = string;
  return string;
};
protoOf(AbstractJsonLexer).a1e = function () {
  this.y13_1 = null;
};
protoOf(AbstractJsonLexer).b1e = function (startPos, endPos) {
  // Inline function 'kotlin.text.substring' call
  var this_0 = this.s1d();
  return toString(charSequenceSubSequence(this_0, startPos, endPos));
};
protoOf(AbstractJsonLexer).f19 = function () {
  if (!(this.y13_1 == null)) {
    return takePeeked(this);
  }
  return this.y1a();
};
protoOf(AbstractJsonLexer).consumeString2 = function (source, startPosition, current) {
  var currentPosition = current;
  var lastPosition = startPosition;
  var char = charSequenceGet(source, currentPosition);
  var usedAppend = false;
  while (!(char === _Char___init__impl__6a9atx(34))) {
    if (char === _Char___init__impl__6a9atx(92)) {
      usedAppend = true;
      currentPosition = this.t1d(appendEscape(this, lastPosition, currentPosition));
      if (currentPosition === -1) {
        this.b18('Unexpected EOF', currentPosition);
      }
      lastPosition = currentPosition;
    } else {
      currentPosition = currentPosition + 1 | 0;
      if (currentPosition >= charSequenceLength(source)) {
        usedAppend = true;
        this.r1d(lastPosition, currentPosition);
        currentPosition = this.t1d(currentPosition);
        if (currentPosition === -1) {
          this.b18('Unexpected EOF', currentPosition);
        }
        lastPosition = currentPosition;
      }
    }
    char = charSequenceGet(source, currentPosition);
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.b1e(lastPosition, currentPosition);
  } else {
    tmp = decodedString(this, lastPosition, currentPosition);
  }
  var string = tmp;
  this.w13_1 = currentPosition + 1 | 0;
  return string;
};
protoOf(AbstractJsonLexer).z1a = function () {
  var result = this.g19();
  if (result === 'null' && wasUnquotedString(this)) {
    this.b18("Unexpected 'null' value instead of string literal");
  }
  return result;
};
protoOf(AbstractJsonLexer).g19 = function () {
  if (!(this.y13_1 == null)) {
    return takePeeked(this);
  }
  var current = this.v1d();
  if (current >= charSequenceLength(this.s1d()) || current === -1) {
    this.b18('EOF', current);
  }
  var token = charToTokenClass(charSequenceGet(this.s1d(), current));
  if (token === 1) {
    return this.f19();
  }
  if (!(token === 0)) {
    this.b18('Expected beginning of the string, but got ' + toString_1(charSequenceGet(this.s1d(), current)));
  }
  var usedAppend = false;
  while (charToTokenClass(charSequenceGet(this.s1d(), current)) === 0) {
    current = current + 1 | 0;
    if (current >= charSequenceLength(this.s1d())) {
      usedAppend = true;
      this.r1d(this.w13_1, current);
      var eof = this.t1d(current);
      if (eof === -1) {
        this.w13_1 = current;
        return decodedString(this, 0, 0);
      } else {
        current = eof;
      }
    }
  }
  var tmp;
  if (!usedAppend) {
    tmp = this.b1e(this.w13_1, current);
  } else {
    tmp = decodedString(this, this.w13_1, current);
  }
  var result = tmp;
  this.w13_1 = current;
  return result;
};
protoOf(AbstractJsonLexer).r1d = function (fromIndex, toIndex) {
  this.z13_1.sa(this.s1d(), fromIndex, toIndex);
};
protoOf(AbstractJsonLexer).x1a = function (allowLenientStrings) {
  // Inline function 'kotlin.collections.mutableListOf' call
  var tokenStack = ArrayList_init_$Create$();
  var lastToken = this.d19();
  if (!(lastToken === 8) && !(lastToken === 6)) {
    this.g19();
    return Unit_instance;
  }
  $l$loop: while (true) {
    lastToken = this.d19();
    if (lastToken === 1) {
      if (allowLenientStrings)
        this.g19();
      else
        this.y1a();
      continue $l$loop;
    }
    var tmp0_subject = lastToken;
    if (tmp0_subject === 8 || tmp0_subject === 6) {
      tokenStack.h(lastToken);
    } else if (tmp0_subject === 9) {
      if (!(last(tokenStack) === 8))
        throw JsonDecodingException_0(this.w13_1, 'found ] instead of } at path: ' + this.x13_1.toString(), this.s1d());
      removeLast(tokenStack);
    } else if (tmp0_subject === 7) {
      if (!(last(tokenStack) === 6))
        throw JsonDecodingException_0(this.w13_1, 'found } instead of ] at path: ' + this.x13_1.toString(), this.s1d());
      removeLast(tokenStack);
    } else if (tmp0_subject === 10) {
      this.b18('Unexpected end of input due to malformed JSON during ignoring unknown keys');
    }
    this.i19();
    if (tokenStack.m() === 0)
      return Unit_instance;
  }
};
protoOf(AbstractJsonLexer).toString = function () {
  return "JsonReader(source='" + toString(this.s1d()) + "', currentPosition=" + this.w13_1 + ')';
};
protoOf(AbstractJsonLexer).w1a = function (key) {
  var processed = this.b1e(0, this.w13_1);
  var lastIndexOf_0 = lastIndexOf(processed, key);
  this.a18("Encountered an unknown key '" + key + "'", lastIndexOf_0, "Use 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.");
};
protoOf(AbstractJsonLexer).a18 = function (message, position, hint) {
  var tmp;
  // Inline function 'kotlin.text.isEmpty' call
  if (charSequenceLength(hint) === 0) {
    tmp = '';
  } else {
    tmp = '\n' + hint;
  }
  var hintMessage = tmp;
  throw JsonDecodingException_0(position, message + ' at path: ' + this.x13_1.o18() + hintMessage, this.s1d());
};
protoOf(AbstractJsonLexer).b18 = function (message, position, hint, $super) {
  position = position === VOID ? this.w13_1 : position;
  hint = hint === VOID ? '' : hint;
  return $super === VOID ? this.a18(message, position, hint) : $super.a18.call(this, message, position, hint);
};
protoOf(AbstractJsonLexer).z15 = function () {
  var current = this.v1d();
  current = this.t1d(current);
  if (current >= charSequenceLength(this.s1d()) || current === -1) {
    this.b18('EOF');
  }
  var tmp;
  if (charSequenceGet(this.s1d(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    if (current === charSequenceLength(this.s1d())) {
      this.b18('EOF');
    }
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var accumulator = new Long(0, 0);
  var exponentAccumulator = new Long(0, 0);
  var isNegative = false;
  var isExponentPositive = false;
  var hasExponent = false;
  var start = current;
  $l$loop_4: while (!(current === charSequenceLength(this.s1d()))) {
    var ch = charSequenceGet(this.s1d(), current);
    if ((ch === _Char___init__impl__6a9atx(101) || ch === _Char___init__impl__6a9atx(69)) && !hasExponent) {
      if (current === start) {
        this.b18('Unexpected symbol ' + toString_1(ch) + ' in numeric literal');
      }
      isExponentPositive = true;
      hasExponent = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45) && hasExponent) {
      if (current === start) {
        this.b18("Unexpected symbol '-' in numeric literal");
      }
      isExponentPositive = false;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(43) && hasExponent) {
      if (current === start) {
        this.b18("Unexpected symbol '+' in numeric literal");
      }
      isExponentPositive = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    if (ch === _Char___init__impl__6a9atx(45)) {
      if (!(current === start)) {
        this.b18("Unexpected symbol '-' in numeric literal");
      }
      isNegative = true;
      current = current + 1 | 0;
      continue $l$loop_4;
    }
    var token = charToTokenClass(ch);
    if (!(token === 0))
      break $l$loop_4;
    current = current + 1 | 0;
    var digit = Char__minus_impl_a2frrh(ch, _Char___init__impl__6a9atx(48));
    if (!(0 <= digit ? digit <= 9 : false)) {
      this.b18("Unexpected symbol '" + toString_1(ch) + "' in numeric literal");
    }
    if (hasExponent) {
      // Inline function 'kotlin.Long.plus' call
      // Inline function 'kotlin.Long.times' call
      exponentAccumulator = exponentAccumulator.v2(toLong(10)).g(toLong(digit));
      continue $l$loop_4;
    }
    // Inline function 'kotlin.Long.minus' call
    // Inline function 'kotlin.Long.times' call
    accumulator = accumulator.v2(toLong(10)).u2(toLong(digit));
    if (accumulator.e1(new Long(0, 0)) > 0) {
      this.b18('Numeric value overflow');
    }
  }
  var hasChars = !(current === start);
  if (start === current || (isNegative && start === (current - 1 | 0))) {
    this.b18('Expected numeric literal');
  }
  if (hasQuotation) {
    if (!hasChars) {
      this.b18('EOF');
    }
    if (!(charSequenceGet(this.s1d(), current) === _Char___init__impl__6a9atx(34))) {
      this.b18('Expected closing quotation mark');
    }
    current = current + 1 | 0;
  }
  this.w13_1 = current;
  if (hasExponent) {
    var doubleAccumulator = accumulator.i3() * consumeNumericLiteral$calculateExponent(exponentAccumulator, isExponentPositive);
    if (doubleAccumulator > (new Long(-1, 2147483647)).i3() || doubleAccumulator < (new Long(0, -2147483648)).i3()) {
      this.b18('Numeric value overflow');
    }
    // Inline function 'kotlin.math.floor' call
    if (!(Math.floor(doubleAccumulator) === doubleAccumulator)) {
      this.b18("Can't convert " + doubleAccumulator + ' to Long');
    }
    accumulator = numberToLong(doubleAccumulator);
  }
  var tmp_0;
  if (isNegative) {
    tmp_0 = accumulator;
  } else if (!accumulator.equals(new Long(0, -2147483648))) {
    tmp_0 = accumulator.y2();
  } else {
    this.b18('Numeric value overflow');
  }
  return tmp_0;
};
protoOf(AbstractJsonLexer).g1b = function () {
  var current = this.v1d();
  if (current === charSequenceLength(this.s1d())) {
    this.b18('EOF');
  }
  var tmp;
  if (charSequenceGet(this.s1d(), current) === _Char___init__impl__6a9atx(34)) {
    current = current + 1 | 0;
    tmp = true;
  } else {
    tmp = false;
  }
  var hasQuotation = tmp;
  var result = consumeBoolean2(this, current);
  if (hasQuotation) {
    if (this.w13_1 === charSequenceLength(this.s1d())) {
      this.b18('EOF');
    }
    if (!(charSequenceGet(this.s1d(), this.w13_1) === _Char___init__impl__6a9atx(34))) {
      this.b18('Expected closing quotation mark');
    }
    this.w13_1 = this.w13_1 + 1 | 0;
  }
  return result;
};
function charToTokenClass(c) {
  var tmp;
  // Inline function 'kotlin.code' call
  if (Char__toInt_impl_vasixd(c) < 126) {
    var tmp_0 = CharMappings_getInstance().d1e_1;
    // Inline function 'kotlin.code' call
    tmp = tmp_0[Char__toInt_impl_vasixd(c)];
  } else {
    tmp = 0;
  }
  return tmp;
}
function tokenDescription(token) {
  return token === 1 ? "quotation mark '\"'" : token === 2 ? "string escape sequence '\\'" : token === 4 ? "comma ','" : token === 5 ? "colon ':'" : token === 6 ? "start of the object '{'" : token === 7 ? "end of the object '}'" : token === 8 ? "start of the array '['" : token === 9 ? "end of the array ']'" : token === 10 ? 'end of the input' : token === 127 ? 'invalid token' : 'valid token';
}
function escapeToChar(c) {
  return c < 117 ? CharMappings_getInstance().c1e_1[c] : _Char___init__impl__6a9atx(0);
}
function initEscape($this) {
  var inductionVariable = 0;
  if (inductionVariable <= 31)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      initC2ESC($this, i, _Char___init__impl__6a9atx(117));
    }
     while (inductionVariable <= 31);
  initC2ESC($this, 8, _Char___init__impl__6a9atx(98));
  initC2ESC($this, 9, _Char___init__impl__6a9atx(116));
  initC2ESC($this, 10, _Char___init__impl__6a9atx(110));
  initC2ESC($this, 12, _Char___init__impl__6a9atx(102));
  initC2ESC($this, 13, _Char___init__impl__6a9atx(114));
  initC2ESC_0($this, _Char___init__impl__6a9atx(47), _Char___init__impl__6a9atx(47));
  initC2ESC_0($this, _Char___init__impl__6a9atx(34), _Char___init__impl__6a9atx(34));
  initC2ESC_0($this, _Char___init__impl__6a9atx(92), _Char___init__impl__6a9atx(92));
}
function initCharToToken($this) {
  var inductionVariable = 0;
  if (inductionVariable <= 32)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      initC2TC($this, i, 127);
    }
     while (inductionVariable <= 32);
  initC2TC($this, 9, 3);
  initC2TC($this, 10, 3);
  initC2TC($this, 13, 3);
  initC2TC($this, 32, 3);
  initC2TC_0($this, _Char___init__impl__6a9atx(44), 4);
  initC2TC_0($this, _Char___init__impl__6a9atx(58), 5);
  initC2TC_0($this, _Char___init__impl__6a9atx(123), 6);
  initC2TC_0($this, _Char___init__impl__6a9atx(125), 7);
  initC2TC_0($this, _Char___init__impl__6a9atx(91), 8);
  initC2TC_0($this, _Char___init__impl__6a9atx(93), 9);
  initC2TC_0($this, _Char___init__impl__6a9atx(34), 1);
  initC2TC_0($this, _Char___init__impl__6a9atx(92), 2);
}
function initC2ESC($this, c, esc) {
  if (!(esc === _Char___init__impl__6a9atx(117))) {
    // Inline function 'kotlin.code' call
    var tmp$ret$0 = Char__toInt_impl_vasixd(esc);
    $this.c1e_1[tmp$ret$0] = numberToChar(c);
  }
}
function initC2ESC_0($this, c, esc) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2ESC($this, tmp$ret$0, esc);
}
function initC2TC($this, c, cl) {
  $this.d1e_1[c] = cl;
}
function initC2TC_0($this, c, cl) {
  // Inline function 'kotlin.code' call
  var tmp$ret$0 = Char__toInt_impl_vasixd(c);
  return initC2TC($this, tmp$ret$0, cl);
}
function CharMappings() {
  CharMappings_instance = this;
  this.c1e_1 = charArray(117);
  this.d1e_1 = new Int8Array(126);
  initEscape(this);
  initCharToToken(this);
}
var CharMappings_instance;
function CharMappings_getInstance() {
  if (CharMappings_instance == null)
    new CharMappings();
  return CharMappings_instance;
}
function StringJsonLexerWithComments(source) {
  StringJsonLexer.call(this, source);
}
protoOf(StringJsonLexerWithComments).i19 = function () {
  var source = this.s1d();
  var cpos = this.v1d();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.w13_1 = cpos + 1 | 0;
  return charToTokenClass(charSequenceGet(source, cpos));
};
protoOf(StringJsonLexerWithComments).e19 = function () {
  var current = this.v1d();
  if (current >= this.s1d().length || current === -1)
    return false;
  return this.w1d(charSequenceGet(this.s1d(), current));
};
protoOf(StringJsonLexerWithComments).s1a = function (expected) {
  var source = this.s1d();
  var current = this.v1d();
  if (current >= source.length || current === -1) {
    this.w13_1 = -1;
    this.y1d(expected);
  }
  var c = charSequenceGet(source, current);
  this.w13_1 = current + 1 | 0;
  if (c === expected)
    return Unit_instance;
  else {
    this.y1d(expected);
  }
};
protoOf(StringJsonLexerWithComments).d19 = function () {
  var source = this.s1d();
  var cpos = this.v1d();
  if (cpos >= source.length || cpos === -1)
    return 10;
  this.w13_1 = cpos;
  return charToTokenClass(charSequenceGet(source, cpos));
};
protoOf(StringJsonLexerWithComments).v1d = function () {
  var current = this.w13_1;
  if (current === -1)
    return current;
  var source = this.s1d();
  $l$loop_1: while (current < source.length) {
    var c = charSequenceGet(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop_1;
    }
    if (c === _Char___init__impl__6a9atx(47) && (current + 1 | 0) < source.length) {
      var tmp0_subject = charSequenceGet(source, current + 1 | 0);
      if (tmp0_subject === _Char___init__impl__6a9atx(47)) {
        current = indexOf_0(source, _Char___init__impl__6a9atx(10), current + 2 | 0);
        if (current === -1) {
          current = source.length;
        } else {
          current = current + 1 | 0;
        }
        continue $l$loop_1;
      } else if (tmp0_subject === _Char___init__impl__6a9atx(42)) {
        current = indexOf(source, '*/', current + 2 | 0);
        if (current === -1) {
          this.w13_1 = source.length;
          this.b18('Expected end of the block comment: "*/", but had EOF instead');
        } else {
          current = current + 2 | 0;
        }
        continue $l$loop_1;
      }
    }
    break $l$loop_1;
  }
  this.w13_1 = current;
  return current;
};
function StringJsonLexer(source) {
  AbstractJsonLexer.call(this);
  this.n1e_1 = source;
}
protoOf(StringJsonLexer).s1d = function () {
  return this.n1e_1;
};
protoOf(StringJsonLexer).t1d = function (position) {
  return position < this.s1d().length ? position : -1;
};
protoOf(StringJsonLexer).i19 = function () {
  var source = this.s1d();
  var cpos = this.w13_1;
  $l$loop: while (!(cpos === -1) && cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charSequenceGet(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.w13_1 = cpos;
    return charToTokenClass(c);
  }
  this.w13_1 = source.length;
  return 10;
};
protoOf(StringJsonLexer).e19 = function () {
  var current = this.w13_1;
  if (current === -1)
    return false;
  var source = this.s1d();
  $l$loop: while (current < source.length) {
    var c = charSequenceGet(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
      continue $l$loop;
    }
    this.w13_1 = current;
    return this.w1d(c);
  }
  this.w13_1 = current;
  return false;
};
protoOf(StringJsonLexer).v1d = function () {
  var current = this.w13_1;
  if (current === -1)
    return current;
  var source = this.s1d();
  $l$loop: while (current < source.length) {
    var c = charSequenceGet(source, current);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9)) {
      current = current + 1 | 0;
    } else {
      break $l$loop;
    }
  }
  this.w13_1 = current;
  return current;
};
protoOf(StringJsonLexer).s1a = function (expected) {
  if (this.w13_1 === -1) {
    this.y1d(expected);
  }
  var source = this.s1d();
  var cpos = this.w13_1;
  $l$loop: while (cpos < source.length) {
    var _unary__edvuaz = cpos;
    cpos = _unary__edvuaz + 1 | 0;
    var c = charSequenceGet(source, _unary__edvuaz);
    // Inline function 'kotlinx.serialization.json.internal.AbstractJsonLexer.isWs' call
    if (c === _Char___init__impl__6a9atx(32) || c === _Char___init__impl__6a9atx(10) || c === _Char___init__impl__6a9atx(13) || c === _Char___init__impl__6a9atx(9))
      continue $l$loop;
    this.w13_1 = cpos;
    if (c === expected)
      return Unit_instance;
    this.y1d(expected);
  }
  this.w13_1 = -1;
  this.y1d(expected);
};
protoOf(StringJsonLexer).y1a = function () {
  this.s1a(_Char___init__impl__6a9atx(34));
  var current = this.w13_1;
  var closingQuote = indexOf_0(this.s1d(), _Char___init__impl__6a9atx(34), current);
  if (closingQuote === -1) {
    this.g19();
    this.z1d(1, false);
  }
  var inductionVariable = current;
  if (inductionVariable < closingQuote)
    do {
      var i = inductionVariable;
      inductionVariable = inductionVariable + 1 | 0;
      if (charSequenceGet(this.s1d(), i) === _Char___init__impl__6a9atx(92)) {
        return this.consumeString2(this.s1d(), this.w13_1, i);
      }
    }
     while (inductionVariable < closingQuote);
  this.w13_1 = closingQuote + 1 | 0;
  // Inline function 'kotlin.text.substring' call
  // Inline function 'kotlin.js.asDynamic' call
  return this.s1d().substring(current, closingQuote);
};
protoOf(StringJsonLexer).a1b = function (keyToMatch, isLenient) {
  var positionSnapshot = this.w13_1;
  try {
    if (!(this.i19() === 6))
      return null;
    var firstKey = this.v1a(isLenient);
    if (!(firstKey === keyToMatch))
      return null;
    this.a1e();
    if (!(this.i19() === 5))
      return null;
    return this.v1a(isLenient);
  }finally {
    this.w13_1 = positionSnapshot;
    this.a1e();
  }
};
function StringJsonLexer_0(json, source) {
  return !json.h13_1.j15_1 ? new StringJsonLexer(source) : new StringJsonLexerWithComments(source);
}
function get_schemaCache(_this__u8e3s4) {
  return _this__u8e3s4.j13_1;
}
function JsonToStringWriter() {
  this.m13_1 = StringBuilder_init_$Create$_0(128);
}
protoOf(JsonToStringWriter).f17 = function (value) {
  this.m13_1.wa(value);
};
protoOf(JsonToStringWriter).z16 = function (char) {
  this.m13_1.b8(char);
};
protoOf(JsonToStringWriter).b17 = function (text) {
  this.m13_1.a8(text);
};
protoOf(JsonToStringWriter).l17 = function (text) {
  printQuoted(this.m13_1, text);
};
protoOf(JsonToStringWriter).n13 = function () {
  this.m13_1.za();
};
protoOf(JsonToStringWriter).toString = function () {
  return this.m13_1.toString();
};
function createMapForCache(initialCapacity) {
  return HashMap_init_$Create$(initialCapacity);
}
//region block: post-declaration
protoOf(defer$1).gl = get_isNullable;
protoOf(defer$1).ml = get_isInline;
protoOf(defer$1).ol = get_annotations;
protoOf(PolymorphismValidator).a13 = contextual;
//endregion
//region block: init
Companion_instance = new Companion();
Companion_instance_0 = new Companion_0();
Companion_instance_1 = new Companion_1();
Companion_instance_2 = new Companion_2();
Tombstone_instance = new Tombstone();
//endregion
//region block: exports
export {
  Json_0 as Jsonsmkyu9xjl7fv,
};
//endregion

//# sourceMappingURL=kotlinx-serialization-kotlinx-serialization-json.mjs.map
