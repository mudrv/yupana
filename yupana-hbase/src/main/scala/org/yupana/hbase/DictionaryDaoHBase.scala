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
import org.apache.hadoop.hbase._
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter
import org.apache.hadoop.hbase.util.Bytes
import org.yupana.api.schema.Dimension
import org.yupana.core.dao.DictionaryDao
import org.yupana.core.utils.metric.MetricQueryCollector

import scala.collection.JavaConverters._

object DictionaryDaoHBase {

  val tableNamePrefix: String = "ts_dict_"
  val directFamily: Array[Byte] = Bytes.toBytes("direct") // store id-> value
  val reverseFamily: Array[Byte] = Bytes.toBytes("reverse") // store value -> id
  val column: Array[Byte] = Bytes.toBytes("c")
  val seqIdRowKey: Array[Byte] = Bytes.toBytes(0)

  val BATCH_SIZE = 50000

  def getTableName(namespace: String, name: String): TableName =
    TableName.valueOf(namespace, tableNamePrefix + name)

  def getReversePairFromResult(result: Result): Option[(Long, String)] = {
    if (!result.isEmpty) {
      Option(result.getValue(reverseFamily, column))
        .map(Bytes.toLong)
        .map((_, Bytes.toString(result.getRow)))
    } else {
      None
    }
  }

  def getReverseScan: Scan = new Scan().addFamily(DictionaryDaoHBase.reverseFamily)
}

class DictionaryDaoHBase(connection: Connection, namespace: String) extends DictionaryDao with StrictLogging {
  import DictionaryDaoHBase._

  var existsTables = Set.empty[String]

  HBaseUtils.checkNamespaceExistsElseCreate(connection, namespace)

  override def getValueById(dimension: Dimension, id: Long): Option[String] = {
    checkTablesExistsElseCreate(dimension)
    val table = getTable(dimension.name)
    val get = new Get(Bytes.toBytes(id)).addFamily(directFamily)
    val result = table.get(get)
    if (!result.isEmpty) {
      Some(Bytes.toString(result.getValue(directFamily, column)))
    } else {
      None
    }
  }

  override def getValuesByIds(
      dimension: Dimension,
      ids: Set[Long]
  ): Map[Long, String] = {
    logger.trace(s"Get dictionary values by ids for ${dimension.name}. Size of ids: ${ids.size}")
    checkTablesExistsElseCreate(dimension)
    val table = getTable(dimension.name)
    val r = ids.toSeq
      .grouped(BATCH_SIZE)
      .flatMap { idsSeq =>
        val ranges = idsSeq.map { id =>
          val key = Bytes.toBytes(id)
          new MultiRowRangeFilter.RowRange(key, true, key, true)
        }

        val filter = new MultiRowRangeFilter(new java.util.ArrayList(ranges.asJava))
        val start = filter.getRowRanges.asScala.head.getStartRow
        val end = Bytes.padTail(filter.getRowRanges.asScala.last.getStopRow, 1)

        val scan = new Scan(start, end)
          .addFamily(directFamily)
          .setFilter(filter)

        logger.trace(s"--- Send request to HBase")
        val scanner = table.getScanner(scan)

        scanner.iterator().asScala.map { result =>
          val id = Bytes.toLong(result.getRow)
          val value = Bytes.toString(result.getValue(directFamily, column))
          id -> value
        }
      }
      .toMap
    logger.trace(s"--- Dictionary values extracted")
    r
  }

  override def getIdByValue(dimension: Dimension, value: String): Option[Long] = {
    checkTablesExistsElseCreate(dimension)
    if (value != null) {
      val trimmed = value.trim
      if (trimmed.nonEmpty) {
        val table = getTable(dimension.name)
        val get = new Get(Bytes.toBytes(trimmed)).addFamily(reverseFamily)
        val result = table.get(get)
        if (!result.isEmpty) {
          Some(Bytes.toLong(result.getValue(reverseFamily, column)))
        } else {
          None
        }
      } else {
        None
      }
    } else {
      None
    }
  }

  override def getIdsByValues(dimension: Dimension, values: Set[String]): Map[String, Long] = {
    if (values.isEmpty) {
      Map.empty
    } else {
      val nonEmptyValues = values.filter(_ != null).map(_.trim).filter(_.nonEmpty).toSeq
      logger.trace(s"Get dictionary ids by values for ${dimension.name}. Size of values: ${nonEmptyValues.size}")
      checkTablesExistsElseCreate(dimension)
      val table = getTable(dimension.name)

      val r = nonEmptyValues
        .grouped(BATCH_SIZE)
        .flatMap { vs =>
          val ranges = vs.map { value =>
            new MultiRowRangeFilter.RowRange(Bytes.toBytes(value), true, Bytes.toBytes(value), true)
          }

          val rangeFilter = new MultiRowRangeFilter(new java.util.ArrayList(ranges.asJava))
          val start = rangeFilter.getRowRanges.asScala.head.getStartRow
          val end = Bytes.padTail(rangeFilter.getRowRanges.asScala.last.getStopRow, 1)
          val scan = new Scan(start, end)
            .addFamily(reverseFamily)
            .setFilter(rangeFilter)

          logger.trace(s"--- Send request to HBase")
          val scanner = table.getScanner(scan)
          scanner.iterator().asScala.map { result =>
            val id = Bytes.toLong(result.getValue(reverseFamily, column))
            val value = Bytes.toString(result.getRow)
            value -> id
          }
        }
        .toMap

      logger.trace(s"--- Dictionary values extracted")
      r
    }
  }

  override def checkAndPut(dimension: Dimension, id: Long, value: String): Boolean = {
    checkTablesExistsElseCreate(dimension)
    val idBytes = Bytes.toBytes(id)
    val valueBytes = Bytes.toBytes(value)

    val table = getTable(dimension.name)
    val rput = new Put(valueBytes).addColumn(reverseFamily, column, idBytes)
    if (table.checkAndPut(valueBytes, reverseFamily, column, null, rput)) {
      val dput = new Put(idBytes).addColumn(directFamily, column, valueBytes)
      table.put(dput)
      true
    } else {
      false
    }
  }

  override def createSeqId(dimension: Dimension): Int = {
    checkTablesExistsElseCreate(dimension)
    getTable(dimension.name).incrementColumnValue(seqIdRowKey, directFamily, column, 1).toInt
  }

  private def getTable(name: String) = {
    connection.getTable(getTableName(namespace, name))
  }

  def checkTablesExistsElseCreate(dimension: Dimension): Unit = {
    if (!existsTables.contains(dimension.name)) {
      try {
        val tableName = getTableName(namespace, dimension.name)
        if (!connection.getAdmin.tableExists(tableName)) {
          val desc = new HTableDescriptor(tableName)
            .addFamily(new HColumnDescriptor(directFamily))
            .addFamily(new HColumnDescriptor(reverseFamily))
          connection.getAdmin.createTable(desc)
        }
      } catch {
        case _: TableExistsException =>
      }
      existsTables += dimension.name
    }
  }
}
