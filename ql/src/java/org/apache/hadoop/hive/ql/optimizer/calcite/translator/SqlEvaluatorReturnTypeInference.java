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
package org.apache.hadoop.hive.ql.optimizer.calcite.translator;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlOperatorBinding;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.hadoop.hive.ql.exec.FunctionInfo;
import org.apache.hadoop.hive.ql.exec.FunctionRegistry;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;

public class SqlEvaluatorReturnTypeInference implements SqlReturnTypeInference {
  private final String funcName;

  public SqlEvaluatorReturnTypeInference(String funcName) {
    this.funcName = funcName;
  }

  @Override
  public @Nullable RelDataType inferReturnType(SqlOperatorBinding opBinding) {
    TypeInfo[] opTypes =
        opBinding.collectOperandTypes().stream().map(TypeConverter::convert).toArray(TypeInfo[]::new);
    ObjectInspector[] oiBinding = Arrays.stream(opTypes)
        .map(TypeInfoUtils::getStandardWritableObjectInspectorFromTypeInfo)
        .toArray(ObjectInspector[]::new);
    ObjectInspector roi = null;
    try {
      FunctionInfo fi = FunctionRegistry.getFunctionInfo(funcName);
      if (fi == null) {
        throw new IllegalStateException("Could not get function info for " + funcName);
      }
      if (fi.isGenericUDF()) {
        roi = fi.getGenericUDF().initialize(oiBinding);
      }
      if (fi.isGenericUDAF()) {
        roi = fi.getGenericUDAFResolver().getEvaluator(opTypes).init(GenericUDAFEvaluator.Mode.COMPLETE, oiBinding);
      }
    } catch (Exception e) {
      throw new IllegalStateException("Could not initialize " + funcName, e);
    }
    if (roi != null) {
      return TypeConverter.convert(TypeInfoUtils.getTypeInfoFromObjectInspector(roi), opBinding.getTypeFactory());
    } else {
      throw new IllegalStateException("Could not get evaluator for " + funcName);
    }
  }
}
