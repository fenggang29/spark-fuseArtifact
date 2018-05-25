package org.apache.spark.sql.execution.datasources.jdbc

import org.apache.spark.Partition
import org.apache.spark.internal.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql._
import org.apache.spark.sql.sources._

import scala.collection.mutable.ArrayBuffer

/**
  * Created by 冯刚 on 2018/4/28.
  */
private[sql] object JDBCPLUSRelation  extends Logging{

  def columnPartition(partitioning: JDBCPartitioningInfo): Array[Partition] = {
    if (partitioning == null || partitioning.numPartitions <= 1 ||
      partitioning.lowerBound == partitioning.upperBound) {
      return Array[Partition](JDBCPartition(null, 0))
    }

    val lowerBound = partitioning.lowerBound
    val upperBound = partitioning.upperBound
    require (lowerBound <= upperBound,
      "Operation not allowed: the lower bound of partitioning column is larger than the upper " +
        s"bound. Lower bound: $lowerBound; Upper bound: $upperBound")

    val numPartitions =
      if ((upperBound - lowerBound) >= partitioning.numPartitions) {
        partitioning.numPartitions
      } else {
        logWarning("The number of partitions is reduced because the specified number of " +
          "partitions is less than the difference between upper bound and lower bound. " +
          s"Updated number of partitions: ${upperBound - lowerBound}; Input number of " +
          s"partitions: ${partitioning.numPartitions}; Lower bound: $lowerBound; " +
          s"Upper bound: $upperBound.")
        upperBound - lowerBound
      }
    // Overflow and silliness can happen if you subtract then divide.
    // Here we get a little roundoff, but that's (hopefully) OK.
    val stride: Long = upperBound / numPartitions - lowerBound / numPartitions
    val column = partitioning.column
    var i: Int = 0
    var currentValue: Long = lowerBound
    var ans = new ArrayBuffer[Partition]()
    while (i < numPartitions) {
      val lBound = if (i != 0) s"$column >= $currentValue" else null
      currentValue += stride
      val uBound = if (i != numPartitions - 1) s"$column < $currentValue" else null
      val whereClause =
        if (uBound == null) {
          lBound
        } else if (lBound == null) {
          s"$uBound or $column is null"
        } else {
          s"$lBound AND $uBound"
        }
      ans += JDBCPartition(whereClause, i)
      i = i + 1
    }
    ans.toArray
  }
}
private[sql] case class JDBCPLUSRelation(
    parts: Array[Partition], jdbcOptions: JDBCOptions)(@transient val sparkSession: SparkSession)
  extends BaseRelation
    with AggregatedFilteredScan
    with InsertableRelation {

  override def sqlContext: SQLContext = sparkSession.sqlContext

  override val needConversion: Boolean = false

  override val schema: StructType = JDBCRDD.resolveTable(jdbcOptions)

  // Check if JDBCRDD.compileFilter can accept input filters
  override def unhandledFilters(filters: Array[Filter]): Array[Filter] = {
    filters.filter(JDBCRDD.compileFilter(_, JdbcDialects.get(jdbcOptions.url)).isEmpty)
  }



  override def buildScan(groupingColumns: Array[String],
                         aggregateFunctions: Array[AggregateFunc],
                         filters: Array[Filter]): RDD[Row] = {
    // Rely on a type erasure hack to pass RDD[InternalRow] back as RDD[Row]
    println("======我在JDBCPLUSRelation中的buildScan方法中1======")
    JDBCRDD.scanTable(
      sparkSession.sparkContext,
      schema,
      groupingColumns,
      filters,
      parts,
      jdbcOptions).asInstanceOf[RDD[Row]]
  }

  override def insert(data: DataFrame, overwrite: Boolean): Unit = {
    val url = jdbcOptions.url
    val table = jdbcOptions.table
    val properties = jdbcOptions.asProperties
    data.write
      .mode(if (overwrite) SaveMode.Overwrite else SaveMode.Append)
      .jdbc(url, table, properties)
  }

  override def toString: String = {
    val partitioningInfo = if (parts.nonEmpty) s" [numPartitions=${parts.length}]" else ""
    // credentials should not be included in the plan output, table information is sufficient.
    s"JDBCPLUSRelation(${jdbcOptions.table})" + partitioningInfo
  }
}