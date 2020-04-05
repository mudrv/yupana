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

package org.yupana.core

import org.yupana.core.model.InternalRow
import org.yupana.api.query._
import org.yupana.core.operations.Operations

object ExpressionCalculator {

  implicit private val operations: Operations = Operations

  def evaluateExpression(
      expr: Expression,
      queryContext: QueryContext,
      internalRow: InternalRow,
      tryEval: Boolean = true
  ): Option[expr.Out] = {
    val res = if (queryContext != null && queryContext.exprsIndex.contains(expr)) {
      internalRow.get[expr.Out](queryContext, expr)
    } else {
      None
    }
    if (res.isEmpty && tryEval) {
      eval(expr, queryContext, internalRow)
    } else {
      res
    }
  }

  private def eval(expr: Expression, queryContext: QueryContext, internalRow: InternalRow): Option[expr.Out] = {

    val res = expr match {
      case ConstantExpr(x) => Some(x).asInstanceOf[Option[expr.Out]]

      case TimeExpr         => None
      case DimensionExpr(_) => None
      case MetricExpr(_)    => None
      case LinkExpr(_, _)   => None

      case ConditionExpr(condition, positive, negative) =>
        val x = evaluateExpression(condition, queryContext, internalRow)
          .getOrElse(false)
        if (x) {
          evaluateExpression(positive, queryContext, internalRow)
        } else {
          evaluateExpression(negative, queryContext, internalRow)
        }

      case UnaryOperationExpr(f, e) =>
        f(evaluateExpression(e, queryContext, internalRow))

      case BinaryOperationExpr(f, a, b) =>
        for {
          ae <- evaluateExpression(a, queryContext, internalRow)
          be <- evaluateExpression(b, queryContext, internalRow)
        } yield f(ae, be)

      case TypeConvertExpr(tc, e) =>
        evaluateExpression(e, queryContext, internalRow).map(tc.direct)

      case AggregateExpr(_, e) =>
        evaluateExpression(e, queryContext, internalRow)

      case WindowFunctionExpr(_, e) =>
        evaluateExpression(e, queryContext, internalRow)

      case InExpr(e, vs) =>
        for {
          eValue <- evaluateExpression(e, queryContext, internalRow)
        } yield vs contains eValue

      case NotInExpr(e, vs) =>
        for {
          eValue <- evaluateExpression(e, queryContext, internalRow)
        } yield !vs.contains(eValue)

      case AndExpr(cs) =>
        val executed = cs.map(c => evaluateExpression(c, queryContext, internalRow))
        executed.reduce((a, b) => a.flatMap(x => b.map(y => x && y)))

      case OrExpr(cs) =>
        val executed = cs.map(c => evaluateExpression(c, queryContext, internalRow))
        executed.reduce((a, b) => a.flatMap(x => b.map(y => x || y)))

      case TupleExpr(e1, e2) =>
        for {
          a <- evaluateExpression(e1, queryContext, internalRow)
          b <- evaluateExpression(e2, queryContext, internalRow)
        } yield (a, b)

      case ae @ ArrayExpr(es) =>
        val values: Array[ae.elementDataType.T] =
          Array.ofDim[ae.elementDataType.T](es.length)(ae.elementDataType.classTag)
        var success = true
        var i = 0

        while (i < es.length && success) {
          evaluateExpression(es(i), queryContext, internalRow) match {
            case Some(v) => values(i) = v.asInstanceOf[ae.elementDataType.T]
            case None    => success = false
          }

          i += 1
        }

        if (success) Some(values) else None

      case x => throw new IllegalArgumentException(s"Unsupported expression $x")
    }

    // I cannot find a better solution to ensure compiler that concrete expr type Out is the same with expr.Out
    res.asInstanceOf[Option[expr.Out]]
  }
}
