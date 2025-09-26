/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.optimizer.calcite;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSyntax;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlAverageAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlCountAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlMinMaxAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlSumAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlSumEmptyIsZeroAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlVarianceAggFunction;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveBetween;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveConcat;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveDateAddSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveDateSubSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveExtractDate;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveFloorDate;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveFromUnixTimeSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveIn;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveToDateSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveToUnixTimestampSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveTruncSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveUnixTimestampSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.translator.SqlFunctionConverter;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class HiveSqlOperatorTable implements SqlOperatorTable {
  private final List<SqlOperator> operators =
      ImmutableList.of(HiveIn.INSTANCE, HiveBetween.INSTANCE, HiveExtractDate.YEAR, HiveExtractDate.QUARTER,
          HiveExtractDate.MONTH, HiveExtractDate.WEEK, HiveExtractDate.DAY, HiveExtractDate.HOUR,
          HiveExtractDate.MINUTE, HiveExtractDate.SECOND, HiveFloorDate.YEAR, HiveFloorDate.QUARTER,
          HiveFloorDate.MONTH, HiveFloorDate.WEEK, HiveFloorDate.DAY, HiveFloorDate.HOUR, HiveFloorDate.MINUTE,
          HiveFloorDate.SECOND, HiveConcat.INSTANCE, HiveTruncSqlOperator.INSTANCE, HiveToDateSqlOperator.INSTANCE,
          HiveToUnixTimestampSqlOperator.INSTANCE, HiveUnixTimestampSqlOperator.INSTANCE,
          HiveFromUnixTimeSqlOperator.INSTANCE, HiveDateAddSqlOperator.INSTANCE, HiveDateSubSqlOperator.INSTANCE);

  @Override
  public void lookupOperatorOverloads(SqlIdentifier opName, @Nullable SqlFunctionCategory category, SqlSyntax syntax,
      List<SqlOperator> operatorList, SqlNameMatcher nameMatcher) {
    for (SqlOperator op : operators) {
      if (op.getName().equals(opName.getSimple())) {
        operatorList.add(op);
      }
    }
  }

  return switch (aggName) {
    case "$SUM0" -> new HiveSqlSumEmptyIsZeroAggFunction(
        distinct, returnTypeInference, operandTypeInference, operandTypeChecker
    );
    case "sum" -> new HiveSqlSumAggFunction(distinct, returnTypeInference, operandTypeInference, operandTypeChecker);
    case "count" ->
        new HiveSqlCountAggFunction(distinct, returnTypeInference, operandTypeInference, operandTypeChecker);
    case "min" -> new HiveSqlMinMaxAggFunction(returnTypeInference, operandTypeInference, operandTypeChecker, true);
    case "max" -> new HiveSqlMinMaxAggFunction(returnTypeInference, operandTypeInference, operandTypeChecker, false);
    case "avg" ->
        new HiveSqlAverageAggFunction(distinct, returnTypeInference, operandTypeInference, operandTypeChecker);
    case "std", "stddev", "stddev_pop" ->
        new HiveSqlVarianceAggFunction("stddev_pop", SqlKind.STDDEV_POP, returnTypeInference, operandTypeInference,
            operandTypeChecker);
    case "stddev_samp" -> new HiveSqlVarianceAggFunction("stddev_samp", SqlKind.STDDEV_SAMP, returnTypeInference,
        operandTypeInference, operandTypeChecker);
    case "variance", "var_pop" ->
        new HiveSqlVarianceAggFunction("var_pop", SqlKind.VAR_POP, returnTypeInference, operandTypeInference,
            operandTypeChecker);
    case "var_samp" ->
        new HiveSqlVarianceAggFunction("var_samp", SqlKind.VAR_SAMP, returnTypeInference, operandTypeInference,
            operandTypeChecker);
    default -> {
      SqlOperator operator = getOperatorFromDefault(aggName);
      if (operator != null) {
        yield (SqlAggFunction) operator;
      }
      yield new SqlFunctionConverter.CalciteUDAF(distinct, aggName, returnTypeInference, operandTypeInference, operandTypeChecker);
    }
  };

  @Override
  public List<SqlOperator> getOperatorList() {
    return operators;
  }
}
