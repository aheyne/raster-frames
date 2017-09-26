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

package org.apache.spark.sql.gt.types

import geotrellis.vector.Geometry
import geotrellis.vector.io.wkb.WKB
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.types._

import scala.reflect.ClassTag

/**
 * Base class for UDTs backed by "well-known binary" (WKB) encoding.
 * @author sfitch
 * @since 4/18/17
 */
trait WKBBackedUDT[T >: Null <: Geometry] { self: UserDefinedType[T] ⇒

  implicit val targetClassTag: ClassTag[T]

  override val simpleString = typeName

  override def sqlType: DataType = StructType(Array(StructField(typeName + "_wkb", BinaryType)))

  override def userClass: Class[T] = targetClassTag.runtimeClass.asInstanceOf[Class[T]]

  override def serialize(obj: T): Any = {
    Option(obj)
      .map(WKB.write(_))
      .map(InternalRow.apply(_))
      .orNull
  }

  override def deserialize(datum: Any): T = {
    Option(datum)
      .collect { case row: InternalRow ⇒ row }
      .flatMap(row ⇒ Option(row.getBinary(0)))
      .map(WKB.read)
      .orNull
      .asInstanceOf[T]
  }
}
