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

package astraea.spark.rasterframes

import geotrellis.proj4.LatLng
import geotrellis.vector.{Extent, Point}

/**
 * Test rig associated with spatial key related extension methods
 *
 * @author sfitch 
 * @since 12/15/17
 */
class SpatialKeySpec  extends TestEnvironment with TestData {
  // This is to avoid an IntelliJ error
  protected def withFixture(test: Any) = ???
  import spark.implicits._

  describe("Spatial key conversions") {
    val raster = sampleGeoTiff.projectedRaster
    // Create a raster frame with a single row
    val rf = raster.toRF(raster.tile.cols, raster.tile.rows)

    it("should add an extent column") {
      val expected = raster.extent
      val result = rf.withExtent().select($"extent".as[Extent]).first
      assert(expected === result)
    }

    it("should add a center value") {
      val expected = raster.extent.center
      val result = rf.withCenter().select($"center".as[(Double, Double)]).first
      assert(expected === Point(result._1, result._2))
    }

    it("should add a center lat/lng value") {
      val expected = raster.extent.center.reproject(raster.crs, LatLng)
      val result = rf.withCenterLatLng().select($"center".as[(Double, Double)]).first
      assert(expected === Point(result._1, result._2))
    }
  }

}
