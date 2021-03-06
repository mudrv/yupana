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

package org.yupana.api.query.syntax

import org.yupana.api.query.{ AggregateExpr, Expression }
import org.yupana.api.types.{ Aggregation, DataType }

trait AggregationSyntax {
  def sum[T](e: Expression.Aux[T])(implicit n: Numeric[T], dt: DataType.Aux[T]) = AggregateExpr(Aggregation.sum[T], e)
  def min[T](e: Expression.Aux[T])(implicit ord: Ordering[T], dt: DataType.Aux[T]) =
    AggregateExpr(Aggregation.min[T], e)
  def max[T](e: Expression.Aux[T])(implicit ord: Ordering[T], dt: DataType.Aux[T]) =
    AggregateExpr(Aggregation.max[T], e)
  def count[T](e: Expression.Aux[T]) = AggregateExpr(Aggregation.count[T], e)
  def distinctCount[T](e: Expression.Aux[T]) = AggregateExpr(Aggregation.distinctCount[T], e)
}

object AggregationSyntax extends AggregationSyntax
