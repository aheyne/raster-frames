/*
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright 2017 Astraea, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package examples

import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.Tile
import astraea.spark.rasterframes._

/**
 * Example tour of some general features in RasterFrames
 *
 * @author sfitch 
 * @since 10/24/17
 */
object Tour extends App {
  implicit val spark = SparkSession.builder()
    .master("local[*]")
    .appName(getClass.getName)
    .getOrCreate()

  import spark.implicits._

  rfInit(spark.sqlContext)

  // Read in a geo-referenced image
  val scene = SinglebandGeoTiff("src/test/resources/L8-B8-Robinson-IL.tiff")

  // Convert it to a raster frame, discretizing it into the given tile size.
  val rf = scene.projectedRaster.toRF(64, 64)

  // See how many tiles we have after discretization
  println("Tile count: " + rf.count())

  // Take a peek at what we're working with
  rf.show(8, false)

  // Confirm we have equally sized tiles
  rf.select(tileDimensions($"tile")).distinct().show()

  // Compute per-tile statistics
  rf.select(tileStats($"tile")).show(8, false)

  // Count the number of no-data cells
  rf.select(aggNoDataCells($"tile")).show(false)

  // Compute some aggregate stats over all cells
  rf.select(aggStats($"tile")).show(false)

  // Create a Spark UDT to perform contrast adjustment via GeoTrellis
  val contrast = udf((t: Tile) ⇒ t.sigmoidal(0.2, 10))

  // Let's contrast adjust the tile column
  val withAdjusted = rf.withColumn("adjusted", contrast(rf("tile"))).asRF

  // Show the stats for the adjusted version
  withAdjusted.select(aggStats($"adjusted")).show(false)

  // Reassemble into a raster and save to a file
  val raster = withAdjusted.toRaster($"adjusted", 774, 500)
  GeoTiff(raster).write("contrast-adjusted.tiff")

  // Perform some arbitrary local ops between columns and render
  val withOp = withAdjusted.withColumn("op", localSubtract($"tile", $"adjusted")).asRF
  val raster2 = withOp.toRaster($"op", 774, 500)
  GeoTiff(raster2).write("with-op.tiff")

  spark.stop()
}
