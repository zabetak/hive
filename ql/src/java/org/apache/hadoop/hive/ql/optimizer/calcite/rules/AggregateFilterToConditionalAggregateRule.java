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
package org.apache.hadoop.hive.ql.optimizer.calcite.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AggregateFilterToConditionalAggregateRule extends RelRule<RelRule.Config> {

  public AggregateFilterToConditionalAggregateRule() {
    super(new HiveRuleConfig() {
      @Override
      public RelOptRule toRule() {
        throw new IllegalStateException();
      }
    }.withOperandSupplier(a -> a.operand(Aggregate.class).oneInput(f -> f.operand(Filter.class).anyInputs())));
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    Aggregate aggregate = call.rel(0);
    Filter filter = call.rel(1);
    RelBuilder relBuilder = call.builder();
    relBuilder.push(filter.getInput());
    RexBuilder rexBuilder = relBuilder.getRexBuilder();
    List<AggregateCall> newAggCalls = new ArrayList<>();
    RelBuilder b = call.builder().push(filter);
    List<RexNode> projects = new ArrayList<>();
    ImmutableBitSet newGroupSet = newGroupSet(aggregate.getGroupSet(), b, projects);
    List<ImmutableBitSet> newGroupSets = new ArrayList<>();
    for (ImmutableBitSet groupSet : aggregate.getGroupSets()) {
      newGroupSets.add(newGroupSet(groupSet, b, projects));
    }
    for(AggregateCall aggCall : aggregate.getAggCallList()) {
      RexNode thenExpr = null;
      if(aggCall.getArgList().size() == 1) {
        thenExpr = rexBuilder.makeInputRef(filter, aggCall.getArgList().getFirst());
      } else if (aggCall.getArgList().isEmpty()) {
        // TODO: Code assumes that we are dealing with COUNT(*) but not checking it explicitly
        thenExpr = rexBuilder.makeExactLiteral(BigDecimal.ONE);
      }
      if(thenExpr != null) {
        RexNode elseExpr = rexBuilder.makeNullLiteral(thenExpr.getType());
        RexNode exp = rexBuilder.makeCall(SqlStdOperatorTable.CASE, filter.getCondition(), thenExpr, elseExpr);
        int i = addExpression(exp, projects);
        newAggCalls.add(aggCall.withArgList(List.of(i)));
      }
    }
    if(newAggCalls.size() != aggregate.getAggCallList().size()) {
      return;
    }
    relBuilder.project(projects);
    relBuilder.aggregate(relBuilder.groupKey(newGroupSet, newGroupSets), newAggCalls);
    call.transformTo(relBuilder.build());
  }

  private static ImmutableBitSet newGroupSet(ImmutableBitSet groupSet, RelBuilder relBuilder, List<RexNode> projects) {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int groupKey : groupSet) {
      int i = addExpression(relBuilder.field(groupKey), projects);
      builder.set(i);
    }
    return builder.build();
  }

  private static int addExpression(RexNode x, List<RexNode> list) {
    int i = list.indexOf(x);
    if (i == -1) {
      list.add(x);
      i = list.size() - 1;
    }
    return i;
  }
}
