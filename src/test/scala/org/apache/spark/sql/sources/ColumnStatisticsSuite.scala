/*
 * Copyright 2016 Lightcopy
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

package org.apache.spark.sql.sources

import org.apache.spark.sql.types._

import com.github.lightcopy.testutil.UnitTestSuite
import com.github.lightcopy.testutil.implicits._

class ColumnStatisticsSuite extends UnitTestSuite {
  test("ColumnStatistics - getStatisticsForType") {
    ColumnStatistics.getStatisticsForType(IntegerType) should be (IntColumnStatistics())
    ColumnStatistics.getStatisticsForType(LongType) should be (LongColumnStatistics())
    ColumnStatistics.getStatisticsForType(StringType) should be (StringColumnStatistics())
  }

  test("ColumnStatistics - getStatisticsForType, fail for invalid numeric type") {
    var err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(ShortType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))

    err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(ByteType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))
  }

  test("ColumnStatistics - getStatisticsForType, fail for date type") {
    var err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(DateType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))

    err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(TimestampType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))
  }

  test("ColumnStatistics - getStatisticsForType, fail for other type") {
    var err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(BooleanType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))

    err = intercept[UnsupportedOperationException] {
      ColumnStatistics.getStatisticsForType(BinaryType)
    }
    assert(err.getMessage.contains("Column statistics do not exist for type"))
  }

  // == IntColumnStatistics ==
  test("IntColumnStatistics - initialize nulls") {
    val stats = IntColumnStatistics()
    stats.getNumNulls should be (0)
    stats.hasNull should be (false)
  }

  test("IntColumnStatistics - increment nulls") {
    val stats = IntColumnStatistics()
    stats.incrementNumNulls()
    stats.getNumNulls should be (1)
    stats.hasNull should be (true)

    stats.incrementNumNulls()
    stats.getNumNulls should be (2)
    stats.hasNull should be (true)
  }

  test("IntColumnStatistics - getMin/getMax when not set") {
    val stats = IntColumnStatistics()
    assert(stats.getMin === null)
    assert(stats.getMax === null)
  }

  test("IntColumnStatistics - update statistics when not set") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(123)
    assert(stats.getMin === 123)
    assert(stats.getMax === 123)
  }

  test("IntColumnStatistics - update statistics when set") {
    val stats = IntColumnStatistics()
    for (x <- Seq(123, 0, -1, 124)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === -1)
    assert(stats.getMax === 124)
  }

  test("IntColumnStatistics - toString") {
    val stats = IntColumnStatistics()
    stats.toString should be ("IntColumnStatistics[min=null, max=null, nulls=0]")

    stats.updateMinMax(123)
    stats.updateMinMax(124)
    stats.incrementNumNulls()
    stats.toString should be ("IntColumnStatistics[min=123, max=124, nulls=1]")
  }

  test("IntColumnStatistics - update statistics with type min/max") {
    val stats = IntColumnStatistics()
    for (x <- Seq(123, Int.MinValue, Int.MaxValue)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === Int.MinValue)
    assert(stats.getMax === Int.MaxValue)
  }

  test("IntColumnStatistics - update statistics with wrong types") {
    val stats = IntColumnStatistics()
    for (x <- Seq("abc", null, 1L, Array(1, 2, 3), 123)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === 123)
    assert(stats.getMax === 123)
  }

  test("IntColumnStatistics - contains") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(7)
    stats.updateMinMax(128)
    // different contains checks
    stats.contains(Int.MinValue) should be (false)
    stats.contains(-1) should be (false)
    stats.contains(7) should be (true)
    stats.contains(96) should be (true)
    stats.contains(128) should be (true)
    stats.contains(Int.MaxValue) should be (false)
  }

  test("IntColumnStatistics - contains nulls") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(7)

    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("IntColumnStatistics - contains nulls when not initialized") {
    val stats = IntColumnStatistics()
    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("IntColumnStatistics - contains other types") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(7)

    stats.contains("str") should be (false)
    stats.contains(Array(1, 2, 3)) should be (false)
    stats.contains(true) should be (false)
    stats.contains(false) should be (false)
  }

  // integer statistics - isLessThanMin

  test("IntColumnStatistics - isLessThanMin") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isLessThanMin(9) should be (true)
    stats.isLessThanMin(10) should be (false)
    stats.isLessThanMin(20) should be (false)
  }

  test("IntColumnStatistics - isLessThanMin, null") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)

    stats.isLessThanMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isLessThanMin(null) should be (false)
  }

  test("IntColumnStatistics - isLessThanMin, other types") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isLessThanMin("abc") should be (false)
    stats.isLessThanMin(Array(1, 2, 3)) should be (false)
  }

  // integer statistics - isEqualToMin

  test("IntColumnStatistics - isEqualToMin") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isEqualToMin(10) should be (true)
    stats.isEqualToMin(20) should be (false)
  }

  test("IntColumnStatistics - isEqualToMin, null") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)

    stats.isEqualToMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMin(null) should be (false)
  }

  test("IntColumnStatistics - isEqualToMin, other types") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isEqualToMin("abc") should be (false)
    stats.isEqualToMin(Array(1, 2, 3)) should be (false)
  }

  // integer statistics - isGreaterThanMax

  test("IntColumnStatistics - isGreaterThanMax") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isGreaterThanMax(10) should be (false)
    stats.isGreaterThanMax(20) should be (false)
    stats.isGreaterThanMax(21) should be (true)
  }

  test("IntColumnStatistics - isGreaterThanMax, null") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)

    stats.isGreaterThanMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isGreaterThanMax(null) should be (false)
  }

  test("IntColumnStatistics - isGreaterThanMax, other types") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isGreaterThanMax("abc") should be (false)
    stats.isGreaterThanMax(Array(1, 2, 3)) should be (false)
  }

  // integer statistics - isEqualToMax

  test("IntColumnStatistics - isEqualToMax") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isEqualToMax(10) should be (false)
    stats.isEqualToMax(20) should be (true)
  }

  test("IntColumnStatistics - isEqualToMax, null") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)

    stats.isEqualToMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMax(null) should be (false)
  }

  test("IntColumnStatistics - isEqualToMax, other types") {
    val stats = IntColumnStatistics()
    stats.updateMinMax(10)
    stats.updateMinMax(20)
    stats.incrementNumNulls()

    stats.isEqualToMax("abc") should be (false)
    stats.isEqualToMax(Array(1, 2, 3)) should be (false)
  }

  //////////////////////////////////////////////////////////////
  // == LongColumnStatistics ==
  //////////////////////////////////////////////////////////////

  test("LongColumnStatistics - initialize nulls") {
    val stats = LongColumnStatistics()
    stats.getNumNulls should be (0)
    stats.hasNull should be (false)
  }

  test("LongColumnStatistics - increment nulls") {
    val stats = LongColumnStatistics()
    stats.incrementNumNulls()
    stats.getNumNulls should be (1)
    stats.hasNull should be (true)

    stats.incrementNumNulls()
    stats.getNumNulls should be (2)
    stats.hasNull should be (true)
  }

  test("LongColumnStatistics - getMin/getMax when not set") {
    val stats = LongColumnStatistics()
    assert(stats.getMin === null)
    assert(stats.getMax === null)
  }

  test("LongColumnStatistics - update statistics when not set") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(321L)
    assert(stats.getMin === 321L)
    assert(stats.getMax === 321L)
  }

  test("LongColumnStatistics - update statistics when set") {
    val stats = LongColumnStatistics()
    for (x <- Seq(321L, 0L, -1L, 322L)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === -1L)
    assert(stats.getMax === 322L)
  }

  test("LongColumnStatistics - update statistics with type min/max") {
    val stats = LongColumnStatistics()
    for (x <- Seq(321L, Long.MinValue, Long.MaxValue)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === Long.MinValue)
    assert(stats.getMax === Long.MaxValue)
  }

  test("LongColumnStatistics - update statistics with wrong types") {
    val stats = LongColumnStatistics()
    for (x <- Seq("abc", null, 1, Array(1, 2, 3), 321L)) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === 321L)
    assert(stats.getMax === 321L)
  }

  test("LongColumnStatistics - toString") {
    val stats = LongColumnStatistics()
    stats.toString should be ("LongColumnStatistics[min=null, max=null, nulls=0]")

    stats.updateMinMax(1L)
    stats.updateMinMax(2L)
    stats.incrementNumNulls()
    stats.toString should be ("LongColumnStatistics[min=1, max=2, nulls=1]")
  }

  test("LongColumnStatistics - contains") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(Int.MinValue.toLong)
    stats.updateMinMax(Int.MaxValue.toLong)
    // different contains checks
    stats.contains(Long.MinValue) should be (false)
    stats.contains(Int.MinValue.toLong) should be (true)
    stats.contains(-1L) should be (true)
    stats.contains(7L) should be (true)
    stats.contains(96L) should be (true)
    stats.contains(128L) should be (true)
    stats.contains(Int.MaxValue.toLong) should be (true)
    stats.contains(Long.MaxValue) should be (false)
  }

  test("LongColumnStatistics - contains nulls") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(1L)
    stats.updateMinMax(2L)

    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("LongColumnStatistics - contains nulls when not initialized") {
    val stats = LongColumnStatistics()
    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("LongColumnStatistics - contains other types") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(1L)
    stats.updateMinMax(2L)
    stats.incrementNumNulls()

    stats.contains("str") should be (false)
    stats.contains(Array(1, 2, 3)) should be (false)
    stats.contains(true) should be (false)
    stats.contains(false) should be (false)
    stats.contains(Int.MinValue) should be (false)
    stats.contains(Int.MaxValue) should be (false)
  }

  // long statistics - isLessThanMin

  test("LongColumnStatistics - isLessThanMin") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isLessThanMin(9L) should be (true)
    stats.isLessThanMin(10L) should be (false)
    stats.isLessThanMin(20L) should be (false)
  }

  test("LongColumnStatistics - isLessThanMin, null") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)

    stats.isLessThanMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isLessThanMin(null) should be (false)
  }

  test("LongColumnStatistics - isLessThanMin, other types") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isLessThanMin("abc") should be (false)
    stats.isLessThanMin(Array(1, 2, 3)) should be (false)
  }

  // long statistics - isEqualToMin

  test("LongColumnStatistics - isEqualToMin") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isEqualToMin(10L) should be (true)
    stats.isEqualToMin(20L) should be (false)
  }

  test("LongColumnStatistics - isEqualToMin, null") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)

    stats.isEqualToMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMin(null) should be (false)
  }

  test("LongColumnStatistics - isEqualToMin, other types") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isEqualToMin("abc") should be (false)
    stats.isEqualToMin(Array(1, 2, 3)) should be (false)
  }

  // long statistics - isGreaterThanMax

  test("LongColumnStatistics - isGreaterThanMax") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isGreaterThanMax(10L) should be (false)
    stats.isGreaterThanMax(20L) should be (false)
    stats.isGreaterThanMax(21L) should be (true)
  }

  test("LongColumnStatistics - isGreaterThanMax, null") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)

    stats.isGreaterThanMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isGreaterThanMax(null) should be (false)
  }

  test("LongColumnStatistics - isGreaterThanMax, other types") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isGreaterThanMax("abc") should be (false)
    stats.isGreaterThanMax(Array(1, 2, 3)) should be (false)
  }

  // long statistics - isEqualToMax

  test("LongColumnStatistics - isEqualToMax") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isEqualToMax(10L) should be (false)
    stats.isEqualToMax(20L) should be (true)
  }

  test("LongColumnStatistics - isEqualToMax, null") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)

    stats.isEqualToMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMax(null) should be (false)
  }

  test("LongColumnStatistics - isEqualToMax, other types") {
    val stats = LongColumnStatistics()
    stats.updateMinMax(10L)
    stats.updateMinMax(20L)
    stats.incrementNumNulls()

    stats.isEqualToMax("abc") should be (false)
    stats.isEqualToMax(Array(1, 2, 3)) should be (false)
  }

  //////////////////////////////////////////////////////////////
  // == StringColumnStatistics ==
  //////////////////////////////////////////////////////////////

  test("StringColumnStatistics - initialize nulls") {
    val stats = StringColumnStatistics()
    stats.getNumNulls should be (0)
    stats.hasNull should be (false)
  }

  test("StringColumnStatistics - increment nulls") {
    val stats = StringColumnStatistics()
    stats.incrementNumNulls()
    stats.getNumNulls should be (1)
    stats.hasNull should be (true)

    stats.incrementNumNulls()
    stats.getNumNulls should be (2)
    stats.hasNull should be (true)
  }

  test("StringColumnStatistics - getMin/getMax when not set") {
    val stats = StringColumnStatistics()
    assert(stats.getMin === null)
    assert(stats.getMax === null)
  }

  test("StringColumnStatistics - update statistics when not set") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("abc")
    assert(stats.getMin === "abc")
    assert(stats.getMax === "abc")
  }

  test("StringColumnStatistics - update statistics when set") {
    val stats = StringColumnStatistics()
    for (x <- Seq("ccc", "aaa", "bbb", "zzz")) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === "aaa")
    assert(stats.getMax === "zzz")
  }

  test("StringColumnStatistics - update statistics with empty values") {
    val stats = StringColumnStatistics()
    for (x <- Seq("a", "b", "", "", "c", "d")) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === "")
    assert(stats.getMax === "d")
  }

  test("StringColumnStatistics - set empty values as min/max for statistics") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("")
    assert(stats.getMin === "")
    assert(stats.getMax === "")
  }

  test("StringColumnStatistics - update statistics with wrong types") {
    val stats = StringColumnStatistics()
    for (x <- Seq(1, null, 1L, Array(1, 2, 3), 123, "abc")) {
      stats.updateMinMax(x)
    }
    assert(stats.getMin === "abc")
    assert(stats.getMax === "abc")
  }

  test("StringColumnStatistics - toString") {
    val stats = StringColumnStatistics()
    stats.toString should be ("StringColumnStatistics[min=null, max=null, nulls=0]")

    stats.updateMinMax("a")
    stats.updateMinMax("b")
    stats.incrementNumNulls()
    stats.toString should be ("StringColumnStatistics[min=a, max=b, nulls=1]")
  }

  test("StringColumnStatistics - contains") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("a")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    // different contains checks
    stats.contains("1") should be (false)
    stats.contains("a") should be (true)
    stats.contains("aaa") should be (true)
    stats.contains("b") should be (true)
    stats.contains("bbb") should be (true)
    stats.contains("c") should be (true)
    stats.contains("ccc") should be (true)
    stats.contains("d") should be (true)
    stats.contains("ddd") should be (false)
    stats.contains("z") should be (false)
  }

  test("StringColumnStatistics - contains nulls") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("a")
    stats.updateMinMax("b")

    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("StringColumnStatistics - contains nulls when not initialized") {
    val stats = StringColumnStatistics()
    stats.contains(null) should be (false)
    stats.incrementNumNulls()
    stats.contains(null) should be (true)
  }

  test("StringColumnStatistics - contains other types") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("a")
    stats.updateMinMax("b")
    stats.incrementNumNulls()

    stats.contains(Long.MinValue) should be (false)
    stats.contains(Int.MinValue) should be (false)
    stats.contains(Int.MaxValue) should be (false)
    stats.contains(Long.MaxValue) should be (false)
    stats.contains(Array(1, 2, 3)) should be (false)
    stats.contains(true) should be (false)
    stats.contains(false) should be (false)
  }

  // string statistics - isLessThanMin

  test("StringColumnStatistics - isLessThanMin") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isLessThanMin("a") should be (true)
    stats.isLessThanMin("aaaaaa") should be (true)
    stats.isLessThanMin("b") should be (false)
    stats.isLessThanMin("bbbbbb") should be (false)
    stats.isLessThanMin("d") should be (false)
  }

  test("StringColumnStatistics - isLessThanMin, null") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")

    stats.isLessThanMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isLessThanMin(null) should be (false)
  }

  test("StringColumnStatistics - isLessThanMin, other types") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isLessThanMin(1L) should be (false)
    stats.isLessThanMin(Array(1, 2, 3)) should be (false)
  }

  // string statistics - isEqualToMin

  test("StringColumnStatistics - isEqualToMin") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isEqualToMin("b") should be (true)
    stats.isEqualToMin("bb") should be (false)
    stats.isEqualToMin("d") should be (false)
  }

  test("StringColumnStatistics - isEqualToMin, null") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")

    stats.isEqualToMin(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMin(null) should be (false)
  }

  test("StringColumnStatistics - isEqualToMin, other types") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isEqualToMin(1L) should be (false)
    stats.isEqualToMin(Array(1, 2, 3)) should be (false)
  }

  // string statistics - isGreaterThanMax

  test("StringColumnStatistics - isGreaterThanMax") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isGreaterThanMax("a") should be (false)
    stats.isGreaterThanMax("d") should be (false)
    stats.isGreaterThanMax("dd") should be (true)
  }

  test("StringColumnStatistics - isGreaterThanMax, null") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")

    stats.isGreaterThanMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isGreaterThanMax(null) should be (false)
  }

  test("StringColumnStatistics - isGreaterThanMax, other types") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isGreaterThanMax(1L) should be (false)
    stats.isGreaterThanMax(Array(1, 2, 3)) should be (false)
  }

  // string statistics - isEqualToMax

  test("StringColumnStatistics - isEqualToMax") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isEqualToMax("b") should be (false)
    stats.isEqualToMax("d") should be (true)
    stats.isEqualToMax("dd") should be (false)
  }

  test("StringColumnStatistics - isEqualToMax, null") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")

    stats.isEqualToMax(null) should be (false)
    stats.incrementNumNulls()
    stats.isEqualToMax(null) should be (false)
  }

  test("StringColumnStatistics - isEqualToMax, other types") {
    val stats = StringColumnStatistics()
    stats.updateMinMax("b")
    stats.updateMinMax("d")
    stats.incrementNumNulls()

    stats.isEqualToMax(1L) should be (false)
    stats.isEqualToMax(Array(1, 2, 3)) should be (false)
  }
}
