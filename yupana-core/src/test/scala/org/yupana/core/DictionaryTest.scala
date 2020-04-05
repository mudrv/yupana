package org.yupana.core

import java.util.Properties

import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.yupana.api.schema.Dimension
import org.yupana.core.cache.CacheFactory
import org.yupana.core.dao.DictionaryDao

class DictionaryTest
    extends FlatSpec
    with Matchers
    with MockFactory
    with OptionValues
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  override def beforeAll(): Unit = {
    val properties = new Properties()
    properties.load(getClass.getClassLoader.getResourceAsStream("app.properties"))
    CacheFactory.init(properties, "test")
  }

  override def beforeEach(): Unit = {
    CacheFactory.flushCaches()
  }

  val testDim = Dimension("test")

  "Dictionary" should "use DAO in value method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getValueById _).expects(testDim, 1).returning(Some("value")).once()
    dictionary.value(1) shouldEqual Some("value")
  }

  it should "use cache in value method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getValueById _).expects(testDim, 1).returning(Some("value")).once()
    dictionary.value(1) shouldEqual Some("value")
    dictionary.value(1) shouldEqual Some("value")
  }

  it should "absent cache in value method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getValueById _).expects(testDim, 1).returning(None).once()
    dictionary.value(1) shouldBe empty
    dictionary.value(1) shouldBe empty
  }

  it should "use DAO in values method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getValuesByIds _)
      .expects(testDim, Set(1L, 2L, 3L))
      .returning(Map(1L -> "value 1", 3L -> "value 3"))
      .once()
    dictionary.values(Set(1, 2, 3)) shouldEqual Map(1 -> "value 1", 3 -> "value 3")
  }

  it should "return empty map for empty ids set in values method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)
    dictionary.values(Set.empty) shouldBe Map.empty
  }

  it should "use caches in values method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getValuesByIds _)
      .expects(testDim, Set(1L, 2L, 3L))
      .returning(Map(1L -> "value 1", 3L -> "value 3"))
      .once()
    (dictionaryDaoMock.getValuesByIds _).expects(testDim, Set(4L)).returning(Map(4L -> "value 4")).once()
    dictionary.values(Set(1L, 2L, 3L)) shouldEqual Map(1L -> "value 1", 3L -> "value 3")
    dictionary.values(Set(2L, 3L, 4L)) shouldEqual Map(4L -> "value 4", 3L -> "value 3")
  }

  it should "use DAO in findIdByValue method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(Some(1)).once()
    dictionary.findIdByValue("value") shouldEqual Some(1)
  }

  it should "use cache in findIdByValue method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(Some(1)).once()
    dictionary.findIdByValue("value") shouldEqual Some(1)
    dictionary.findIdByValue("value") shouldEqual Some(1)
  }

  it should "treat null values as empty strings in find methods" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)
    (dictionaryDaoMock.getIdByValue _).expects(testDim, "").returning(None).once()
    dictionary.findIdByValue(null) shouldEqual None
  }

  it should "use DAO in findIdsByValues method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdsByValues _)
      .expects(testDim, Set("value 1", "value 2", "value 3"))
      .returning(Map("value 1" -> 1, "value 3" -> 3))
      .once()

    dictionary.findIdsByValues(Set("value 1", "value 2", "value 3")) shouldEqual Map("value 1" -> 1, "value 3" -> 3)
  }

  it should "use caches in findIdsByValues method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdsByValues _)
      .expects(testDim, Set("value 1", "value 2", "value 3"))
      .returning(Map("value 1" -> 1, "value 3" -> 3))
      .once()

    (dictionaryDaoMock.getIdsByValues _)
      .expects(testDim, Set("value 2", "value 4"))
      .returning(Map("value 4" -> 4))
      .once()

    dictionary.findIdsByValues(Set("value 1", "value 2", "value 3")) shouldEqual Map("value 1" -> 1, "value 3" -> 3)
    dictionary.findIdsByValues(Set("value 2", "value 3", "value 4")) shouldEqual Map("value 4" -> 4, "value 3" -> 3)
    dictionary.findIdsByValues(Set("value 3", "value 4")) shouldEqual Map("value 4" -> 4, "value 3" -> 3)
  }

  it should "return empty map for empty set of values" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)
    dictionary.findIdsByValues(Set.empty) shouldEqual Map.empty
  }

  it should "read existing values in id method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(Some(1)).once()
    dictionary.id("value") shouldEqual 1
    dictionary.id("value") shouldEqual 1
  }

  it should "create new ids for unknown values in id method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(None).once()
    (dictionaryDaoMock.createSeqId _).expects(testDim).returning(42)
    (dictionaryDaoMock.checkAndPut _).expects(testDim, 42, "value").returning(true)
    dictionary.id("value") shouldEqual 42
    dictionary.id("value") shouldEqual 42
  }

  it should "handle concurrent put in id call" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(None).once()
    (dictionaryDaoMock.createSeqId _).expects(testDim).returning(42)
    (dictionaryDaoMock.checkAndPut _).expects(testDim, 42, "value").returning(false)
    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(Some(43)).once()
    dictionary.id("value") shouldEqual 43
    dictionary.id("value") shouldEqual 43
  }

  it should "create new ids in batch" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdsByValues _)
      .expects(testDim, Set("value 1", "value 2"))
      .returning(Map("value 1" -> 1))
      .once()

    (dictionaryDaoMock.createSeqId _).expects(testDim).returning(33)
    (dictionaryDaoMock.checkAndPut _).expects(testDim, 33, "value 2").returning(true)
    dictionary.getOrCreateIdsForValues(Set("value 1", "value 2")) shouldEqual Map("value 1" -> 1, "value 2" -> 33)
  }

  it should "throw exception if something went wrong while trying to create new id for unknown value in id method" in {
    val dictionaryDaoMock = mock[DictionaryDao]
    val dictionary = new Dictionary(testDim, dictionaryDaoMock)

    (dictionaryDaoMock.getIdByValue _).expects(testDim, "value").returning(None).twice()
    (dictionaryDaoMock.createSeqId _).expects(testDim).returning(42)
    (dictionaryDaoMock.checkAndPut _).expects(testDim, 42, "value").returning(false)
    val thrown = the[IllegalStateException] thrownBy dictionary.id("value")
    thrown.getMessage should equal(s"Can't put value value to dictionary ${testDim.name}")
  }

}
