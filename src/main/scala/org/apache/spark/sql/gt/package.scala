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

package org.apache.spark.sql

import org.apache.spark.annotation.Experimental
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute

/**
 * Module providing support for using GeoTrellis native types in Spark SQL.
 * `import org.apache.spark.sql.gt._`., and then call `gtRegister(SQLContext)`.
 *
 * @author sfitch
 * @since 3/30/17
 */
package object gt extends implicits {
  @Experimental
  def gtRegister(sqlContext: SQLContext): Unit = {
    gt.types.Registrator.register()
    gt.functions.Registrator.register(sqlContext)
  }

  private[gt] implicit class NamedColumn(col: Column) {
    def columnName = col.expr match {
      case ua: UnresolvedAttribute ⇒ ua.name
      case o ⇒ o.prettyName
    }
  }
}
