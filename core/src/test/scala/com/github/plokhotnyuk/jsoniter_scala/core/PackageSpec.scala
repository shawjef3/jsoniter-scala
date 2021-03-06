package com.github.plokhotnyuk.jsoniter_scala.core

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.charset.StandardCharsets.UTF_8

import com.github.plokhotnyuk.jsoniter_scala.core.UserAPI._
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.prop.PropertyChecks

class PackageSpec extends WordSpec with Matchers with PropertyChecks {
  "read" should {
    "parse JSON from the provided input stream" in {
      readFromStream(getClass.getResourceAsStream("user_api_response.json"))(codec) shouldBe user
    }
    "parse JSON from the byte array" in {
      readFromArray(compactJson)(codec) shouldBe user
    }
    "parse JSON from the byte array within specified positions" in {
      readFromSubArray(httpMessage, 66, httpMessage.length)(codec) shouldBe user
    }
    "throw an exception if cannot parse input with message containing input offset & hex dump of affected part" in {
      assert(intercept[JsonParseException](readFromArray(httpMessage)(codec)).getMessage ==
        """expected '{', offset: 0x00000000, buf:
          |           +-------------------------------------------------+
          |           |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
          |+----------+-------------------------------------------------+------------------+
          || 00000000 | 48 54 54 50 2f 31 2e 30 20 32 30 30 20 4f 4b 0a | HTTP/1.0 200 OK. |
          || 00000010 | 43 6f 6e 74 65 6e 74 2d 54 79 70 65 3a 20 61 70 | Content-Type: ap |
          || 00000020 | 70 6c 69 63 61 74 69 6f 6e 2f 6a 73 6f 6e 0a 43 | plication/json.C |
          |+----------+-------------------------------------------------+------------------+""".stripMargin)
    }
    "throw an exception in case of the provided params are invalid" in {
      intercept[NullPointerException](readFromArray(compactJson)(null))
      intercept[NullPointerException](readFromStream(new ByteArrayInputStream(compactJson))(null))
      intercept[NullPointerException](readFromSubArray(httpMessage, 66, httpMessage.length)(null))
      intercept[NullPointerException](readFromArray(null.asInstanceOf[Array[Byte]])(codec))
      intercept[NullPointerException](readFromSubArray(null.asInstanceOf[Array[Byte]], 0, 50)(codec))
      intercept[NullPointerException](readFromStream(null.asInstanceOf[InputStream])(codec))
      intercept[NullPointerException](readFromStream(new ByteArrayInputStream(compactJson), null)(codec))
      intercept[NullPointerException](readFromSubArray(httpMessage, 66, httpMessage.length, null)(codec))
      assert(intercept[ArrayIndexOutOfBoundsException](readFromSubArray(httpMessage, 50, 200)(codec))
        .getMessage.contains("`to` should be positive and not greater than `buf` length"))
      assert(intercept[ArrayIndexOutOfBoundsException](readFromSubArray(httpMessage, 50, 10)(codec))
        .getMessage.contains("`from` should be positive and not greater than `to`"))
    }
  }
  "scanValueStream" should {
    def inputStream: InputStream = getClass.getResourceAsStream("user_api_value_stream.json")

    "scan JSON values from the provided input stream" in {
      var users: Seq[User] = Seq.empty
      scanJsonValuesFromStream(inputStream) { (u: User) =>
        users = users :+ u
        true
      }(codec)
      users shouldBe Seq(user, user1, user2)
    }
    "scanning of JSON values can be interrupted by returning `false` from the consumer" in {
      var users: Seq[User] = Seq.empty
      scanJsonValuesFromStream(inputStream) { (u: User) =>
        users = users :+ u
        false
      }(codec)
      users shouldBe Seq(user)
    }
    "throw an exception in case of the provided params are invalid" in {
      val skip = (_: User) => true
      val npe = null.asInstanceOf[User => Boolean]
      intercept[NullPointerException](scanJsonValuesFromStream(null.asInstanceOf[InputStream])(skip)(codec))
      intercept[NullPointerException](scanJsonValuesFromStream(inputStream, null)(skip)(codec))
      intercept[NullPointerException](scanJsonValuesFromStream(inputStream)(npe)(codec))
    }
  }
  "scanArray" should {
    def inputStream: InputStream = getClass.getResourceAsStream("user_api_array.json")

    "scan values of JSON array from the provided input stream" in {
      var users: Seq[User] = Seq.empty
      scanJsonArrayFromStream(inputStream) { (u: User) =>
        users = users :+ u
        true
      }(codec)
      users shouldBe Seq(user, user1, user2)
    }
    "scanning of JSON array values can be interrupted by returning `false` from the consumer" in {
      var users: Seq[User] = Seq.empty
      scanJsonArrayFromStream(inputStream) { (u: User) =>
        users = users :+ u
        false
      }(codec)
      users shouldBe Seq(user)
    }
    "throw an exception in case of the provided params are invalid" in {
      val skip = (_: User) => true
      val npe = null.asInstanceOf[User => Boolean]
      intercept[NullPointerException](scanJsonArrayFromStream(null.asInstanceOf[InputStream])(skip)(codec))
      intercept[NullPointerException](scanJsonArrayFromStream(inputStream, null)(skip)(codec))
      intercept[NullPointerException](scanJsonArrayFromStream(inputStream)(npe)(codec))
    }
  }
  "write" should {
    val buf = new Array[Byte](150)

    "serialize an object to the provided output stream" in {
      val out1 = new ByteArrayOutputStream()
      writeToStream(user, out1)(codec)
      out1.toString("UTF-8") shouldBe toString(compactJson)
      val out2 = new ByteArrayOutputStream()
      writeToStream(user, out2, WriterConfig(indentionStep = 2))(codec)
      out2.toString("UTF-8") shouldBe toString(prettyJson)
    }
    "serialize an object to a new instance of byte array" in {
      toString(writeToArray(user)(codec)) shouldBe toString(compactJson)
      toString(writeToArray(user, WriterConfig(indentionStep = 2))(codec)) shouldBe toString(prettyJson)
    }
    "serialize an object to the provided byte array from specified position" in {
      val from1 = 10
      val to1 = writeToPreallocatedArray(user, buf, from1)(codec)
      new String(buf, from1, to1 - from1, UTF_8) shouldBe toString(compactJson)
      val from2 = 0
      val to2 = writeToPreallocatedArray(user, buf, from2, WriterConfig(indentionStep = 2))(codec)
      new String(buf, from2, to2 - from2, UTF_8) shouldBe toString(prettyJson)
    }
    "throw array index out of bounds exception in case of the provided byte array is overflown during serialization" in {
      assert(intercept[ArrayIndexOutOfBoundsException](writeToPreallocatedArray(user, buf, 100)(codec))
        .getMessage.contains("`buf` length exceeded"))
    }
    "throw i/o exception in case of the provided params are invalid" in {
      intercept[NullPointerException](writeToArray(user)(null))
      intercept[NullPointerException](writeToStream(user, new ByteArrayOutputStream())(null))
      intercept[NullPointerException](writeToPreallocatedArray(user, buf, 0)(null))
      intercept[NullPointerException](writeToStream(user, null.asInstanceOf[OutputStream])(codec))
      intercept[NullPointerException](writeToPreallocatedArray(user, null, 50)(codec))
      intercept[NullPointerException](writeToArray(user, null.asInstanceOf[WriterConfig])(codec))
      intercept[NullPointerException](writeToStream(user, new ByteArrayOutputStream(), null)(codec))
      intercept[NullPointerException](writeToPreallocatedArray(user, buf, 0, null)(codec))
      assert(intercept[ArrayIndexOutOfBoundsException](writeToPreallocatedArray(user, new Array[Byte](10), 50)(codec))
        .getMessage.contains("`from` should be positive and not greater than `buf` length"))
    }
  }

  def toString(json: Array[Byte]): String = new String(json, 0, json.length, UTF_8)
}
