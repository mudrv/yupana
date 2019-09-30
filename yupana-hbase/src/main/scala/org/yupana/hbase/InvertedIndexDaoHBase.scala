/*
 * Copyright 2019 Rusexpertiza LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yupana.hbase

import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.hbase.client.{ Get, Put, ResultScanner, Scan }
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.hbase.{ CellUtil, HColumnDescriptor, HTableDescriptor }
import org.yupana.api.utils.{ DimOrdering, SortedSetIterator }
import org.yupana.core.dao.InvertedIndexDao

import scala.collection.JavaConverters._

object InvertedIndexDaoHBase {
  val FAMILY: Array[Byte] = Bytes.toBytes("f")
  val VALUE: Array[Byte] = Array.emptyByteArray
  val BATCH_SIZE = 500000

  def stringSerializer(str: String): Array[Byte] = Bytes.toBytes(str)
  def stringDeserializer(bytes: Array[Byte]): String = Bytes.toString(bytes)
  def intSerializer(v: Int): Array[Byte] = Bytes.toBytes(v)
  def intDeserializer(bytes: Array[Byte]): Int = Bytes.toInt(bytes)
  def longSerializer(v: Long): Array[Byte] = Bytes.toBytes(v)
  def longDeserializer(bytes: Array[Byte]): Long = Bytes.toLong(bytes)

  def checkTableExistsElseCreate(hBaseConnection: ExternalLinkHBaseConnection, tableName: String) {
    val desc = new HTableDescriptor(hBaseConnection.getTableName(tableName))
      .addFamily(new HColumnDescriptor(InvertedIndexDaoHBase.FAMILY))
    hBaseConnection.checkTablesExistsElseCreate(desc)
  }

  def createPutOperation[K, V](
      key: K,
      values: Set[V],
      keySerializer: K => Array[Byte],
      valueSerializer: V => Array[Byte]
  ): Option[Put] = {
    if (values.isEmpty) {
      None
    } else {
      val wordBytes = keySerializer(key)
      val put =
        values.foldLeft(new Put(wordBytes))((put, value) => put.addColumn(FAMILY, valueSerializer(value), VALUE))
      Some(put)
    }
  }
}

class InvertedIndexDaoHBase[K, V: DimOrdering](
    connection: ExternalLinkHBaseConnection,
    tableName: String,
    keySerializer: K => Array[Byte],
    valueSerializer: V => Array[Byte],
    valueDeserializer: Array[Byte] => V
) extends InvertedIndexDao[K, V]
    with StrictLogging {

  import InvertedIndexDaoHBase._

  checkTableExistsElseCreate(connection, tableName)

  override def put(key: K, values: Set[V]): Unit = {
    if (values.nonEmpty) {
      val table = connection.getTable(tableName)
      val put = createPutOperation(key, values, keySerializer, valueSerializer)
      put.foreach(table.put)
    }
  }

  override def batchPut(batch: Map[K, Set[V]]): Unit = {
    val table = connection.getTable(tableName)
    val puts = batch.flatMap {
      case (key, values) =>
        createPutOperation(key, values, keySerializer, valueSerializer)
    }
    table.put(puts.toSeq.asJava)
  }

  private def toIterator(result: ResultScanner): SortedSetIterator[V] = {
    val it = result.iterator().asScala.flatMap { result =>
      result.rawCells().map { cell =>
        valueDeserializer(CellUtil.cloneQualifier(cell))
      }
    }
    SortedSetIterator(it)
  }

  def values(key: K): SortedSetIterator[V] = {
    allValues(Set(key))
  }

  private def scanValues(key: K): SortedSetIterator[V] = {
    logger.trace(s"scan values for key $key")
    val skey = keySerializer(key)
    val table = connection.getTable(tableName)
    val scan = new Scan(skey, Bytes.padTail(skey, 1)).addFamily(FAMILY).setBatch(BATCH_SIZE)
    val scanner = table.getScanner(scan)
    toIterator(scanner)
  }

  def allValues(keys: Set[K]): SortedSetIterator[V] = {
    val table = connection.getTable(tableName)

    val keySeq = keys.toSeq

    val gets = keySeq.map { key =>
      val skey = keySerializer(key)
      new Get(skey).setMaxResultsPerColumnFamily(BATCH_SIZE)
    }

    val results = table.get(gets.asJava).zip(keySeq)
    val (completed, partial) = results.partition { case (res, key) => res.rawCells().length < BATCH_SIZE }

    val iterators = partial.map { case (_, key) => scanValues(key) }

    val fetched = completed.map {
      case (result, _) =>
        SortedSetIterator(result.rawCells().map(c => valueDeserializer(CellUtil.cloneQualifier(c))).toIterator)
    }

    SortedSetIterator.unionAll(iterators ++ fetched)
  }
}
