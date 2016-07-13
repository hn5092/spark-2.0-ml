import java.io.File

import org.apache.spark.SparkConf
import org.apache.spark.sql.{Row, SparkSession}
import org.spark_project.guava.io.{ByteStreams, Files}

/**
  * Created by xym on 2016/7/3.
  */
object Hive {
  /*
   * Licensed to the Apache Software Foundation (ASF) under one or more
   * contributor license agreements.  See the NOTICE file distributed with
   * this work for additional information regarding copyright ownership.
   * The ASF licenses this file to You under the Apache License, Version 2.0
   * (the "License"); you may not use this file except in compliance with
   * the License.  You may obtain a copy of the License at
   *
   *    http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
   */

  // scalastyle:off println
    case class Record(key: Int, value: String)

    // Copy kv1.txt file from classpath to temporary directory
    val kv1Stream = Hive.getClass.getResourceAsStream("/kv1.txt")
    val kv1File = File.createTempFile("kv1", "txt")
    kv1File.deleteOnExit()
    ByteStreams.copy(kv1Stream, Files.newOutputStreamSupplier(kv1File))

    def main(args: Array[String]) {
      val sparkConf = new SparkConf().setAppName("HiveFromSpark")

      // A hive context adds support for finding tables in the MetaStore and writing queries
      // using HiveQL. Users who do not have an existing Hive deployment can still create a
      // HiveContext. When not configured by the hive-site.xml, the context automatically
      // creates metastore_db and warehouse in the current directory.
      val spark = SparkSession.builder
        .config(sparkConf).master("local")
        .enableHiveSupport()
        .getOrCreate()
      val sc = spark.sparkContext

      import spark.implicits._
      import spark.sql
      sql("CREATE TABLE IF NOT EXISTS src2 (key INT, value STRING)")
      sql(s"LOAD DATA LOCAL INPATH '${kv1File.getAbsolutePath}' INTO TABLE src2")

      // Queries are expressed in HiveQL
      println("Result of 'SELECT *': ")
      sql("SELECT * FROM src2").collect().foreach(println)

      // Aggregation queries are also supported.
      val count = sql("SELECT COUNT(*) FROM src2").collect().head.getLong(0)
      println(s"COUNT(*): $count")

      // The results of SQL queries are themselves RDDs and support all normal RDD functions.  The
      // items in the RDD are of type Row, which allows you to access each column by ordinal.
      val rddFromSql = sql("SELECT key, value FROM src WHERE key < 10 ORDER BY key")

      println("Result of RDD.map:")
      val rddAsStrings = rddFromSql.rdd.map {
        case Row(key: Int, value: String) => s"Key: $key, Value: $value"
      }

      // You can also use RDDs to create temporary views within a HiveContext.
      val rdd = sc.parallelize((1 to 100).map(i => Record(i, s"val_$i")))
      rdd.toDF().createOrReplaceTempView("records")

      // Queries can then join RDD data with data stored in Hive.
      println("Result of SELECT *:")
      sql("SELECT * FROM records r JOIN src s ON r.key = s.key").collect().foreach(println)

      spark.stop()
    }
  // scalastyle:on println

}