package org.yupana.core.sql.parser

import org.joda.time.{DateTimeZone, LocalDateTime, Period}

sealed trait Value {
  def asString: String
}

case object Placeholder extends Value {
  override def asString: String = throw new IllegalStateException("asString called on Placeholder")
}

case class NumericValue(value: BigDecimal) extends Value {
  override def asString: String = value.toString
}

case class StringValue(value: String) extends Value {
  override def asString: String = value
}

case class TimestampValue(value: LocalDateTime) extends Value {
  override def asString: String = value.toString
}

object TimestampValue {
  def apply(millis: Long): TimestampValue = new TimestampValue(new LocalDateTime(millis, DateTimeZone.UTC))
}

case class PeriodValue(value: Period) extends Value {
  override def asString: String = value.toString
}