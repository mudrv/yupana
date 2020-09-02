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

package org.yupana.core.sql

import org.yupana.api.query.{ ConstantExpr, Expression, NullExpr, TypeConvertExpr }
import org.yupana.api.types.{ DataType, TypeConverter }

trait ExprPair {
  type T
  val a: Expression.Aux[T]
  val b: Expression.Aux[T]

  def dataType: DataType.Aux[T] = a.dataType
}

object ExprPair {
  def apply[T0](x: Expression.Aux[T0], y: Expression.Aux[T0]): ExprPair = new ExprPair {
    override type T = T0
    override val a: Expression.Aux[T0] = x
    override val b: Expression.Aux[T0] = y
  }

  def constCast[U, T](const: ConstantExpr[U], dataType: DataType.Aux[T]): Either[String, T] = {
    if (const.dataType == dataType) {
      Right(const.v.asInstanceOf[T])
    } else {
      TypeConverter(const.dataType, dataType.aux)
        .map(conv => conv.convert(const.v))
        .orElse(
          TypeConverter
            .partial(const.dataType, dataType.aux)
            .flatMap(conv => conv.convert(const.v))
        )
        .toRight(
          s"Cannot convert value '${const.v}' of type ${const.dataType.meta.sqlTypeName} to ${dataType.meta.sqlTypeName}"
        )
    }
  }

  def alignTypes(ca: Expression, cb: Expression): Either[String, ExprPair] = {
    if (ca.dataType == cb.dataType) {
      Right(ExprPair[ca.Out](ca.aux, cb.asInstanceOf[Expression.Aux[ca.Out]]))
    } else {
      (ca, cb) match {
        case (_: ConstantExpr[_], _: ConstantExpr[_]) => convertRegular(ca, cb)

        case (NullExpr(_), _) => Right(ExprPair(NullExpr(cb.dataType), cb.aux))
        case (_, NullExpr(_)) => Right(ExprPair(ca.aux, NullExpr(ca.dataType)))

        case (c: ConstantExpr[_], _) =>
          constCast(c, cb.dataType).right.map(cc => ExprPair(ConstantExpr(cc)(cb.dataType), cb.aux))

        case (_, c: ConstantExpr[_]) =>
          constCast(c, ca.dataType).right.map(cc => ExprPair(ca.aux, ConstantExpr(cc)(ca.dataType)))

        case (_, _) => convertRegular(ca, cb)
      }
    }
  }

  private def convertRegular(ca: Expression, cb: Expression): Either[String, ExprPair] = {
    TypeConverter(ca.dataType, cb.dataType)
      .map(aToB => ExprPair[cb.Out](TypeConvertExpr(aToB, ca.aux), cb.aux))
      .orElse(
        TypeConverter(cb.dataType, ca.dataType)
          .map(bToA => ExprPair[ca.Out](ca.aux, TypeConvertExpr(bToA, cb.aux)))
      )
      .toRight(s"Incompatible types ${ca.dataType.meta.sqlTypeName}($ca) and ${cb.dataType.meta.sqlTypeName}($cb)")
  }
}
