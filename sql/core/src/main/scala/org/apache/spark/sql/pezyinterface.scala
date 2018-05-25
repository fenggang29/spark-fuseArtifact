package org.apache.spark.sql



/**
  * Created by zy on 2017/8/3.
  */

trait pezyinterface {

  def analysisSql(sqltext: String , ss: SparkSession) : Unit

}
