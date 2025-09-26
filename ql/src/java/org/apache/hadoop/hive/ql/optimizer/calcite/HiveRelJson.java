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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.externalize.RelJson;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexDynamicParam;
import org.apache.calcite.rex.RexFieldCollation;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexOver;
import org.apache.calcite.rex.RexWindowBound;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlCollation;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlOperandTypeChecker;
import org.apache.calcite.sql.type.SqlOperandTypeInference;
import org.apache.calcite.sql.type.SqlReturnTypeInference;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.util.ConversionUtil;
import org.apache.calcite.util.JsonBuilder;
import org.apache.calcite.util.Util;
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
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveTableScan;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveToDateSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveToUnixTimestampSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveTruncSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveUnixTimestampSqlOperator;
import org.apache.hadoop.hive.ql.optimizer.calcite.translator.SqlFunctionConverter;
import org.apache.hadoop.hive.ql.parse.type.RexNodeExprFactory;

/**
 * Hive extension of RelJson, used for serialization and deserialization.
 */
public class HiveRelJson extends RelJson {
  private final JsonBuilder jsonBuilder;
  private static final String OPERANDS = "operands";
  private static final String DISTINCT = "distinct";
  private static final String LITERAL = "literal";
  private static Method toRex = null;
  private static Method addRexFieldCollationList = null;
  private static Method toRexWindowBound = null;
  private static Method toJsonSqlOperator = null;

  public HiveRelJson(JsonBuilder jsonBuilder) {
    super(jsonBuilder);
    this.jsonBuilder = jsonBuilder;
    try {
      toRex = RelJson.class.getDeclaredMethod("toRex", RelInput.class, Object.class);
      toRex.setAccessible(true);
      addRexFieldCollationList = RelJson.class
          .getDeclaredMethod("addRexFieldCollationList", List.class, RelInput.class, List.class);
      addRexFieldCollationList.setAccessible(true);
      toRexWindowBound = RelJson.class.getDeclaredMethod("toRexWindowBound", RelInput.class, Map.class);
      toRexWindowBound.setAccessible(true);
      toJsonSqlOperator = RelJson.class.getDeclaredMethod("toJson", SqlOperator.class);
      toJsonSqlOperator.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Object toJson(Object value) {
    return switch (value) {
      case null -> null;
      case HiveTableScan.HiveTableScanTrait trait -> trait.name();
      case RexNode rexNode -> toJson(rexNode);
      case SqlOperator operator -> {
        try {
          yield toJsonSqlOperator.invoke(this, operator);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
      default -> super.toJson(value);
    };
  }

  private Object toJson(RexNode node) {
    return switch (node) {
      case RexCall rexCall -> toJsonRexCall(rexCall);
      case RexDynamicParam rexDynamicParam -> toJson(rexDynamicParam);
      case null, default -> super.toJson(node);
    };
  }
  
  private Object toJson(RexDynamicParam rexDynamicParam) {
    Map<String, Object> map = jsonBuilder.map();
    map.put("dynamic_param", true);
    map.put("index", rexDynamicParam.getIndex());
    return map;
  }
  
  private Map<String, Object> toJsonRexCall(RexCall node) {
    Map<String, Object> map = null;
    if (node != null) {
      map = jsonBuilder.map();
      map.put("op", toJson(node.getOperator()));
      map.put("type", toJson(node.getType()));
      final List<Object> list = jsonBuilder.list();
      for (RexNode operand : node.getOperands()) {
        list.add(toJson(operand));
      }
      map.put(OPERANDS, list);
      if (Objects.requireNonNull(node.getKind()) == SqlKind.CAST) {
        map.put("type", toJson(node.getType()));
      }
      if (node.getOperator() instanceof SqlFunction op && (op.getFunctionType().isUserDefined())) {
        map.put("class", op.getClass().getName());
        map.put("type", toJson(node.getType()));
        map.put("deterministic", op.isDeterministic());
        map.put("dynamic", op.isDynamicFunction());
      }
      if (node instanceof RexOver over) {
        map.put(DISTINCT, over.isDistinct());
        map.put("type", toJson(node.getType()));
        map.put("window", toJson(over.getWindow()));
        map.put("ignoreNulls", toJson(over.ignoreNulls()));
      }
    }
    return map;
  }

  public SqlAggFunction toAggregation(RelInput input, String aggName, Map<String, Object> map) {
    final Boolean distinct = (Boolean) map.get(DISTINCT);
    @SuppressWarnings("unchecked") final List<Object> operands = (List<Object>) map.get(OPERANDS);
    final RelDataType type = toType(input.getCluster().getTypeFactory(), map.get("type"));

    final SqlReturnTypeInference returnTypeInference = ReturnTypes.explicit(type);
    final ImmutableList.Builder<RelDataType> argTypesBuilder = new ImmutableList.Builder<>();
    for (Object e : operands) {
      if (e instanceof Integer) {
        argTypesBuilder.add(input.getInput().getRowType().getFieldList().get((Integer) e).getType());
      } else {
        argTypesBuilder.add(toRex(input, e).getType());
      }
    }
    final List<RelDataType> argTypes = argTypesBuilder.build();
    final SqlOperandTypeInference operandTypeInference = InferTypes.explicit(argTypes);
    final ImmutableList.Builder<SqlTypeFamily> typeFamilyBuilder = new ImmutableList.Builder<>();
    for (RelDataType at : argTypes) {
      typeFamilyBuilder.add(Util.first(at.getSqlTypeName().getFamily(), SqlTypeFamily.ANY));
    }
    final SqlOperandTypeChecker operandTypeChecker = OperandTypes.family(typeFamilyBuilder.build());

  }



  public RexNode toRex(RelInput relInput, Object o) {
    if (o == null) {
      return null;
    }
    
    try {
      return switch (o) {
        case Map map -> toRexMap(relInput, map, relInput.getCluster());
        default -> (RexNode) toRex.invoke(this, relInput, o);
      };
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private RexNode toRexMap(RelInput relInput, Object o, RelOptCluster cluster)
      throws InvocationTargetException, IllegalAccessException {
    RexBuilder rexBuilder = relInput.getCluster().getRexBuilder();
    Map map = (Map) o;
    final Map opMap = (Map) map.get("op");
    final RelDataTypeFactory typeFactory = cluster.getTypeFactory();
    if (opMap != null) {
      return toRexOperator(relInput, opMap, map, typeFactory);
    }
    if (map.containsKey(LITERAL)) {
      return makeLiteral(relInput, map, typeFactory);
    }
    // To create RexNode expr, we need to call toOp() via toRexOperator().
    final String field = (String) map.get("field");
    if (field != null) {
      final Object jsonExpr = map.get("expr");
      final RexNode expr = toRex(relInput, jsonExpr);
      return rexBuilder.makeFieldAccess(expr, field, true);
    }
    // Currently Calcite does not de/serialize RexDynamicParam, so we add support for it here.
    if (map.containsKey("dynamic_param")) {
      int index = (int) map.get("index");
      return rexBuilder.makeDynamicParam(rexBuilder.getTypeFactory().createSqlType(SqlTypeName.NULL), index);
    }
    return (RexNode) toRex.invoke(this, relInput, o);
  }

  private RexNode makeLiteral(RelInput relInput, Map map, RelDataTypeFactory typeFactory) throws InvocationTargetException, IllegalAccessException {
    Object literal = map.get(LITERAL);
    RexBuilder rexBuilder = relInput.getCluster().getRexBuilder();
    final RelDataType type = toType(typeFactory, map.get("type"));
    if (literal == null) {
      return rexBuilder.makeNullLiteral(type);
    }
    if (type == null) {
      // In previous versions, type was not specified for all literals.
      // To keep backwards compatibility, if type is not specified
      // we just interpret the literal
      return (RexNode) toRex.invoke(this, relInput, literal);
    }
    return makeLiteral(rexBuilder, literal, type);
  }

  private RexNode makeLiteral(RexBuilder rexBuilder, Object literal, RelDataType type) {
    switch (type.getSqlTypeName()) {
      case CHAR:
      case VARCHAR:
        literal = RexNodeExprFactory.makeHiveUnicodeString((String) literal);
        return rexBuilder.makeLiteral(literal, type, true);
      case DOUBLE:
      case FLOAT:
        if (literal instanceof Integer i) {
          return rexBuilder.makeExactLiteral(new BigDecimal(i), type);
        } else if (literal instanceof BigDecimal bd) {
          return rexBuilder.makeExactLiteral(bd, type);
        }
      default:
        return rexBuilder.makeLiteral(literal, type, true);
    }
  }

  private RexNode toRexOperator(RelInput relInput, Map opMap, Map map, RelDataTypeFactory typeFactory) 
      throws InvocationTargetException, IllegalAccessException {
    RexBuilder rexBuilder = relInput.getCluster().getRexBuilder();
    final String opName = (String) opMap.get("name");
    final List operands = (List) map.get(OPERANDS);
    final List<RexNode> rexOperands = toRexList(relInput, operands);
    final Object jsonType = map.get("type");
    final Map window = (Map) map.get("window");
    if (window != null) {
      return toRexWindow(relInput, opName, map, typeFactory, jsonType, window, rexOperands);
    } else {
      final SqlOperator operator = toOp(relInput, opName, map);
      final RelDataType type;
      if (jsonType != null) {
        type = toType(typeFactory, jsonType);
      } else {
        type = rexBuilder.deriveReturnType(operator, rexOperands);
      }
      return rexBuilder.makeCall(type, operator, rexOperands);
    }
  }

  private RexNode toRexWindow(
      RelInput relInput, 
      String opName,
      Map map,
      RelDataTypeFactory typeFactory,
      Object jsonType,
      Map window, 
      List<RexNode> rexOperands) throws InvocationTargetException, IllegalAccessException {
    // We cannot use the super method because we need to call our own toAggregation()
    final SqlAggFunction operator = toAggregation(relInput, opName, map);
    final RelDataType type = toType(typeFactory, jsonType);
    List<RexNode> partitionKeys = new ArrayList<>();
    if (window.containsKey("partition")) {
      partitionKeys = toRexList(relInput, (List) window.get("partition"));
    }
    List<RexFieldCollation> orderKeys = new ArrayList<>();
    if (window.containsKey("order")) {
      addRexFieldCollationList.invoke(this, orderKeys, relInput, window.get("order"));
    }
    final RexWindowBound lowerBound;
    final RexWindowBound upperBound;
    final boolean physical;
    if (window.get("rows-lower") != null) {
      lowerBound = (RexWindowBound) toRexWindowBound.invoke(this, relInput, window.get("rows-lower"));
      upperBound = (RexWindowBound) toRexWindowBound.invoke(this, relInput, window.get("rows-upper"));
      physical = true;
    } else if (window.get("range-lower") != null) {
      lowerBound = (RexWindowBound) toRexWindowBound.invoke(this, relInput, window.get("range-lower"));
      upperBound = (RexWindowBound) toRexWindowBound.invoke(this, relInput, window.get("range-upper"));
      physical = false;
    } else {
      // No ROWS or RANGE clause
      lowerBound = null;
      upperBound = null;
      physical = false;
    }
    final boolean distinct = (Boolean) map.get(DISTINCT);
    return relInput.getCluster().getRexBuilder().makeOver(
        type,
        operator,
        rexOperands,
        partitionKeys,
        ImmutableList.copyOf(orderKeys),
        lowerBound,
        upperBound,
        physical,
        true,
        false,
        distinct,
        false);
  }

  private List<RexNode> toRexList(RelInput relInput, List operands) {
    final List<RexNode> list = new ArrayList<>();
    for (Object operand : operands) {
      list.add(toRex(relInput, operand));
    }
    return list;
  }

  @Override
  public RelDataType toType(RelDataTypeFactory typeFactory, Object o) {
    RelDataType type = super.toType(typeFactory, o);

    // Ensure all character types have UTF-16 charset and collation
    // Without this, Strings can have extra "UTF-16LE" appended to them
    // after deserialization
    if (SqlTypeUtil.inCharFamily(type)) {
      type = typeFactory.createTypeWithCharsetAndCollation(
          type, Charset.forName(ConversionUtil.NATIVE_UTF16_CHARSET_NAME), SqlCollation.IMPLICIT
      );
    }

    return type;
  }
}
