package pimpathon.argonaut

import argonaut._
import argonaut.Json._
import monocle.Traversal
import org.junit.Test

import pimpathon.any._
import pimpathon.option._
import pimpathon.util._
import pimpathon.argonaut.json._
import pimpathon.pimpTry._

import scalaz.{-\/, \/-, \/}


class JsonTest extends JsonUtil {
  @Test def filterNulls(): Unit = test(_.filterNulls,
    """null"""                        → """null""",
    """{ "a": null, "b": 3 }"""       → """{ "b": 3 }""",
    """[ "a", null, "b" ]"""          → """[ "a", "b" ]""",
    """{ "o": [ "a", null, "b" ] }""" → """{ "o": [ "a", "b" ] }""",
    """[ { "a": null, "b": 3 } ]"""   → """[ { "b": 3 } ]"""
  )

  @Test def descendant_values(): Unit = {
    jobj.descendant("age").getAll === List(age)
    jobj.descendant("age").modify(_ ⇒ redacted) === ("age" → redacted) ->: jobj

    jobj.descendant("{name, age}").getAll === List(name, age)
    jobj.descendant("{name, age}").modify(_ ⇒ redacted) === ("name" → redacted) ->: ("age" → redacted) ->: jobj
  }

  @Test def descendant_elements(): Unit = {
    jArray(fields).descendant("[0, 2]").getAll === List(lying, address)

    jArray(fields).descendant("[0, 2]").modify(_ ⇒ redacted) === jArrayElements(
      redacted, name, redacted, age, width, preferences
    )
  }

  @Test def descendant_all(): Unit = {
    jobj.descendant("*").getAll === List(name, age, lying, address, preferences, width)

    jobj.descendant("*").modify(_ ⇒ jString("redacted")) === jObjectFields(
      "name" → redacted, "age" → redacted, "lying" → redacted, "address" → redacted, "preferences" → redacted,
      "width" → redacted
    )
  }

  private def test(f: Json ⇒ Json, data: (String, String)*): Unit = data.foreach {
    case (input, expected) ⇒ f(parse(input)) === parse(expected)
  }

  private def parse(content: String): Json = Parse.parseOption(content).getOrThrow("Invalid json in test\n" + content)
}

class CodecJsonTest extends JsonUtil {
  @Test def beforeDecode(): Unit = codec.beforeDecode(reverse).decodeJson(json)  === codec.decodeJson(reverse(json))
  @Test def afterDecode(): Unit  = codec.afterDecode(_.reverse).decodeJson(json) === reverse(codec.decodeJson(json))
  @Test def beforeEncode(): Unit = codec.beforeEncode(_.reverse).encode(list)    === codec.encode(list.reverse)
  @Test def afterEncode(): Unit  = codec.afterEncode(reverse).encode(list)       === reverse(codec.encode(list))
  @Test def andThen(): Unit      = codec.andThen(reverse).encode(list)           === reverse(codec.encode(list))
  @Test def compose(): Unit      = codec.compose(reverse).decodeJson(json)       === codec.decodeJson(reverse(json))

  @Test def xmapKeys(): Unit = mapCodec.xmapKeys[String](_.reverse)(_.reverse).calc(reversed ⇒ {
    reversed.encode(Map("foo" → "bar"))         === mapCodec.encode(Map("oof" → "bar"))
    reversed.decodeJson(jsonMap("foo" → "bar")) === mapCodec.decodeJson(jsonMap("oof" → "bar"))
  })

  @Test def xmapValues(): Unit = mapCodec.xmapValues[String](_.reverse)(_.reverse).calc(reversed ⇒ {
    reversed.encode(Map("foo" → "bar"))         === mapCodec.encode(Map("foo" → "rab"))
    reversed.decodeJson(jsonMap("foo" → "bar")) === mapCodec.decodeJson(jsonMap("foo" → "rab"))
  })

  @Test def xmapDisjunction(): Unit = stringCodec.xmapDisjunction[Int](attempt(_.toInt))(_.toString).calc(intCodec ⇒ {
    intCodec.encode(3)                     === Json.jString("3")
    intCodec.decodeJson(Json.jString("3")) === DecodeResult.ok(3)
    intCodec.decodeJson(Json.jString("a")) === DecodeResult.fail("a", CursorHistory(Nil))
  })

  // Searching for a better name before making this a pimp (and one producing Either[A, B])
  private def attempt[A, B](f: A => B)(a: A): A \/ B = a.attempt(f).fold(_ => -\/(a), \/-(_))
}

class EncodeJsonTest extends JsonUtil {
  @Test def afterEncode(): Unit = encoder.afterEncode(reverse).encode(list)      === reverse(encoder.encode(list))
  @Test def andThen(): Unit     = encoder.andThen(reverse).encode(list)          === reverse(encoder.encode(list))
  @Test def downcast(): Unit    = Base.encoder.downcast[Derived].encode(derived) === derivedEncoded

  @Test def contramapKeys(): Unit =
    mapEncoder.contramapKeys[String](_.reverse).encode(Map("foo" → "bar")) === mapEncoder.encode(Map("oof" → "bar"))

  @Test def contramapValues(): Unit =
    mapEncoder.contramapValues[String](_.reverse).encode(Map("foo" → "bar")) === mapEncoder.encode(Map("foo" → "rab"))
}

class DecodeJsonTest extends JsonUtil {
  @Test def beforeDecode(): Unit = decoder.beforeDecode(reverse).decodeJson(json) === decoder.decodeJson(reverse(json))
  @Test def compose(): Unit      = decoder.compose(reverse).decodeJson(json)      === decoder.decodeJson(reverse(json))
  @Test def upcast(): Unit       = Derived.codec.upcast[Base].decodeJson(derivedEncoded) === DecodeResult.ok(derived)

  @Test def mapKeys(): Unit =
    mapDecoder.mapKeys(_.reverse).decodeJson(jsonMap("foo" → "bar")) === mapDecoder.decodeJson(jsonMap("oof" → "bar"))

  @Test def mapValues(): Unit =
    mapDecoder.mapValues(_.reverse).decodeJson(jsonMap("foo" → "bar")) === mapDecoder.decodeJson(jsonMap("foo" → "rab"))
}

class TraversalFrills extends JsonUtil {
  @Test def bool(): Unit = {
    calling(id.bool.getAll)           .partitions(fields).into(lying → List(true), others → nil)
    calling(id.bool.modify(_ ⇒ false)).partitions(fields).into(lying → jBool(false), others → unchanged)
  }

  @Test def string(): Unit = {
    calling(id.string.getAll)         .partitions(fields).into(name → List("Eric"),     others → nil)
    calling(id.string.modify(_ + "!")).partitions(fields).into(name → jString("Eric!"), others → unchanged)
  }

  @Test def array(): Unit = {
    calling(id.array.getAll)           .partitions(fields).into(address → List(acaciaRoad),           others → nil)
    calling(id.array.modify(_.reverse)).partitions(fields).into(address → jArray(acaciaRoad.reverse), others → unchanged)
  }

  @Test def obj(): Unit = {
    calling(id.obj.getAll).partitions(fields).into(preferences → List(bananasAndMould), others → nil)

    calling(id.obj.modify(_ - "mould")).partitions(fields)
      .into(preferences → jObjectFields("bananas" → jBool(true)), others → unchanged)
  }

  @Test def double(): Unit = {
    calling(id.double.getAll).partitions(fields).into(age → List(3.0), width → List(33.5), others → nil)

    calling(id.double.modify(_ * 2.0)).partitions(fields)
      .into(age → jNumberOrNull(6.0), width → jNumberOrNull(67.0), others → unchanged)
  }

  @Test def int(): Unit = {
    calling(id.int.getAll)       .partitions(fields).into(age → List(3),    others → nil)
    calling(id.int.modify(_ * 2)).partitions(fields).into(age → jNumber(6), others → unchanged)
  }

  @Test def descendant_values(): Unit = {
    id.descendant("age").getAll(jobj) === List(age)
    id.descendant("age").modify(_ ⇒ redacted)(jobj) === ("age" → redacted) ->: jobj

    id.descendant("{name, age}").getAll(jobj) === List(name, age)
    id.descendant("{name, age}").modify(_ ⇒ redacted)(jobj) === ("name" → redacted) ->: ("age" → redacted) ->: jobj
  }

  @Test def descendant_elements(): Unit = {
    id.descendant("[0, 2]").getAll(jArray(fields)) === List(lying, address)

    id.descendant("[0, 2]").modify(_ ⇒ redacted)(jArray(fields)) === jArrayElements(
      redacted, name, redacted, age, width, preferences
    )
  }

  @Test def descendant_all(): Unit = {
    id.descendant("*").getAll(jobj) === List(name, age, lying, address, preferences, width)

    id.descendant("*").modify(_ ⇒ jString("redacted"))(jobj) === jObjectFields(
      "name" → redacted, "age" → redacted, "lying" → redacted, "address" → redacted, "preferences" → redacted,
      "width" → redacted
    )
  }

  private val id: Traversal[Json, Json] = Traversal.id[Json]
}

trait JsonUtil {
  def reverse(json: Json): Json = json.withArray(_.reverse)
  def reverse[A](decodeResult: DecodeResult[List[A]]): DecodeResult[List[A]] = decodeResult.map(_.reverse)

  val codec: CodecJson[List[String]]           = CodecJson.derived[List[String]]
  val mapCodec: CodecJson[Map[String, String]] = CodecJson.derived[Map[String, String]]
  val stringCodec: CodecJson[String]           = CodecJson.derived[String]
  val (encoder, decoder)       = (codec.Encoder, codec.Decoder)
  val (mapEncoder, mapDecoder) = (mapCodec.Encoder, mapCodec.Decoder)

  val list = List("food", "foo", "bard", "bar")
  val json = Json.jArray(list.map(Json.jString))
  def jsonMap(kvs: (String, String)*) = Json.jObjectAssocList(kvs.map { case (k, v) ⇒ (k, Json.jString(v)) }.toList)

  trait Base
  object Base { val encoder = EncodeJson[Base]({ case d: Derived ⇒ Derived.codec.encode(d) }) }

  case class Derived(i: Int) extends Base
  object Derived { implicit val codec: CodecJson[Derived] = CodecJson.casecodec1(Derived.apply, Derived.unapply)("i") }

  val derived = Derived(123)
  val derivedEncoded = Derived.codec.encode(derived)

  val acaciaRoad = List(jString("29 Acacia Road"), jString("Nuttytown"))
  val bananasAndMould = JsonObject.empty + ("bananas", jBool(true)) + ("mould", jBool(false))

  val fields@List(lying, name, address, age, width, preferences) = List(
    jBool(true), jString("Eric"), jArray(acaciaRoad), jNumber(3), jNumberOrNull(33.5), jObject(bananasAndMould)
  )

  val jobj: Json = jObjectFields(
    "name" → name, "age" → age, "lying" → lying, "address" → address, "preferences" → preferences, "width" → width
  )

  val redacted = jString("redacted")
}