package org.apache.spark.sql.hive

import org.apache.hadoop.hive.ql.plan.TableDesc
import org.apache.hadoop.hive.serde2.objectinspector.{ObjectInspector, StructObjectInspector}
import org.apache.spark.TaskContext
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.types.DataType

/**
  * Created by zy on 2017/8/17.
  */

trait
pezyhivewriteif {

  def pezyhivewrite(row: InternalRow,wrappers:Array[(Any)=>Any],
    dataTypes: Seq[DataType],fieldOIs:Array[ObjectInspector], outputData:Array[Any],
    standardOI: StructObjectInspector, tableDesc: TableDesc) : Unit

  def pezyclose() : Unit

}
