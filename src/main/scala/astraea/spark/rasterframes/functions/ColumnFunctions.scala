/*
 * Copyright 2017 Astraea, Inc.
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

package astraea.spark.rasterframes.functions

import geotrellis.raster.Tile
import geotrellis.raster.histogram.Histogram
import geotrellis.raster.mapalgebra.local.LocalTileBinaryOp
import geotrellis.raster.mapalgebra.{local ⇒ alg}
import geotrellis.raster.summary.Statistics
import org.apache.spark.annotation.Experimental
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.analysis.MultiAlias
import org.apache.spark.sql.catalyst.expressions.{CreateArray, Expression, Inline}
import org.apache.spark.sql.functions.{lit, udf ⇒ SparkUDF}
import org.apache.spark.sql.gt.Implicits._
import org.apache.spark.sql.gt._
import org.apache.spark.sql.types._

import scala.reflect.runtime.universe._

/**
 * UDFs for working with tiles in Spark DataFrames.
 *
 * @author sfitch
 * @since 4/3/17
 */
trait ColumnFunctions {
  private implicit val stringEnc: Encoder[String] = Encoders.STRING
  private implicit val doubleEnc: Encoder[Double] = Encoders.scalaDouble
  private implicit val statsEnc: Encoder[Statistics[Int]] = Encoders.product[Statistics[Int]]

  // format: off
  /** Create a row for each cell in tile. */
  @Experimental
  def explodeTiles(cols: Column*): Column = explodeTileSample(1.0, cols: _*)

  /** Create a row for each cell in tile with random sampling. */
  @Experimental
  def explodeTileSample(sampleFraction: Double, cols: Column*): Column = {
    val exploder = ExplodeTileExpression(sampleFraction, cols.map(_.expr))
    // Hack to grab the first two non-cell columns
    val metaNames = exploder.elementSchema.fieldNames.take(2)
    val colNames = cols.map(_.columnName)
    new Column(exploder).as(metaNames ++ colNames)
  }

  /** Query the number of (cols, rows) in a tile. */
  @Experimental
  def tileDimensions(col: Column): Column = withAlias("tileDimensions", col)(
    SparkUDF[(Int, Int), Tile](UDFs.tileDimensions).apply(col)
  ).cast(StructType(Seq(StructField("cols", IntegerType), StructField("rows", IntegerType))))

  /**  Compute the full column aggregate floating point histogram. */
  @Experimental
  def aggHistogram(col: Column): TypedColumn[Any, Histogram[Double]] =
  withAlias("histogram", col)(
    UDFs.aggHistogram(col)
  ).as[Histogram[Double]]

  /** Compute the full column aggregate floating point statistics. */
  @Experimental
  def aggStats(col: Column): TypedColumn[Any, Statistics[Double]] =
  withAlias("stats", col)(
    UDFs.aggStats(col)
  ).as[Statistics[Double]]

  /** Compute tileHistogram of floating point tile values. */
  @Experimental
  def tileHistogramDouble(col: Column): TypedColumn[Any, Histogram[Double]] =
  withAlias("tileHistogramDouble", col)(
    SparkUDF[Histogram[Double], Tile](UDFs.tileHistogramDouble).apply(col)
  ).as[Histogram[Double]]

  /** Compute statistics of tile values. */
  @Experimental
  def tileStatsDouble(col: Column): TypedColumn[Any, Statistics[Double]] =
  withAlias("tileStatsDouble", col)(
    SparkUDF[Statistics[Double], Tile](UDFs.tileStatsDouble).apply(col)
  ).as[Statistics[Double]]

  /** Compute the tile-wise mean */
  @Experimental
  def tileMeanDouble(col: Column): TypedColumn[Any, Double] =
  withAlias("tileMeanDouble", col)(
    SparkUDF[Double, Tile](UDFs.tileMeanDouble).apply(col)
  ).as[Double]

  /** Compute the tile-wise mean */
  @Experimental
  def tileMean(col: Column): TypedColumn[Any, Double] =
  withAlias("tileMean", col)(
    SparkUDF[Double, Tile](UDFs.tileMean).apply(col)
  ).as[Double]

  /** Compute tileHistogram of tile values. */
  @Experimental
  def tileHistogram(col: Column): TypedColumn[Any, Histogram[Int]] =
  withAlias("tileHistogram", col)(
    SparkUDF[Histogram[Int], Tile](UDFs.tileHistogram).apply(col)
  ).as[Histogram[Int]]

  /** Compute statistics of tile values. */
  @Experimental
  def tileStats(col: Column): TypedColumn[Any, Statistics[Int]] =
  withAlias("tileStats", col)(
    SparkUDF[Statistics[Int], Tile](UDFs.tileStats).apply(col)
  ).as[Statistics[Int]]

  /** Compute cell-local aggregate descriptive statistics for a column of tiles. */
  @Experimental
  def localAggStats(col: Column): Column =
  withAlias("localAggStats", col)(
    UDFs.localAggStats(col)
  )

  /** Compute the cellwise/local max operation between tiles in a column. */
  @Experimental
  def localAggMax(col: Column): TypedColumn[Any, Tile] =
  withAlias("localAggMax", col)(
    UDFs.localAggMax(col)
  ).as[Tile]

  /** Compute the cellwise/local min operation between tiles in a column. */
  @Experimental
  def localAggMin(col: Column): TypedColumn[Any, Tile] =
  withAlias("localAggMin", col)(
    UDFs.localAggMin(col)
  ).as[Tile]

  /** Compute the cellwise/local mean operation between tiles in a column. */
  @Experimental
  def localAggMean(col: Column): TypedColumn[Any, Tile] =
  withAlias("localAggMean", col)(
    UDFs.localAggMean(col)
  ).as[Tile]

  /** Compute the cellwise/local count of non-NoData cells for all tiles in a column. */
  @Experimental
  def localAggCount(col: Column): TypedColumn[Any, Tile] =
  withAlias("localCount", col)(
    UDFs.localAggCount(col)
  ).as[Tile]

  /** Cellwise addition between two tiles. */
  @Experimental
  def localAdd(left: Column, right: Column): TypedColumn[Any, Tile] =
  localAlgebra(alg.Add, left, right)

  /** Cellwise subtraction between two tiles. */
  @Experimental
  def localSubtract(left: Column, right: Column): TypedColumn[Any, Tile] =
  localAlgebra(alg.Subtract, left, right)

  /** Perform an arbitrary GeoTrellis `LocalTileBinaryOp` between two tile columns. */
  @Experimental
  def localAlgebra(op: LocalTileBinaryOp, left: Column, right: Column):
  TypedColumn[Any, Tile] =
    withAlias(opName(op), left, right)(
      SparkUDF[Tile, Tile, Tile](op.apply).apply(left, right)
    ).as[Tile]

  /** Render tile as ASCII string for debugging purposes. */
  @Experimental
  def renderAscii(col: Column): TypedColumn[Any, String] =
  withAlias("renderAscii", col)(
    SparkUDF[String, Tile](UDFs.renderAscii).apply(col)
  ).as[String]

  // --------------------------------------------------------------------------------------------
  // -- Private APIs below --
  // --------------------------------------------------------------------------------------------
  /** Tags output column with a nicer name. */
  private[rasterframes] def withAlias(name: String, inputs: Column*)(output: Column) = {
    val paramNames = inputs.map(_.columnName).mkString(",")
    output.as(s"$name($paramNames)")
  }

  private[rasterframes] def opName(op: LocalTileBinaryOp) =
    op.getClass.getSimpleName.replace("$", "").toLowerCase

//  /** Lookup the registered Catalyst UDT for the given Scala type. */
//  private[rasterframes] def udtOf[T >: Null: TypeTag]: UserDefinedType[T] =
//    UDTRegistration.getUDTFor(typeTag[T].tpe.toString).map(_.newInstance().asInstanceOf[UserDefinedType[T]])
//      .getOrElse(throw new IllegalArgumentException(typeTag[T].tpe + " doesn't have a corresponding UDT"))

  /** Creates a Catalyst expression for flattening the fields in a struct into columns. */
  private[rasterframes] def projectStructExpression(dataType: StructType, input: Expression) =
    MultiAlias(Inline(CreateArray(Seq(input))), dataType.fields.map(_.name))
}