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

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.Project;
import org.apache.hadoop.hive.ql.optimizer.calcite.reloperators.HiveTableScan;
import org.apache.hadoop.hive.ql.optimizer.calcite.rules.HiveRuleConfig;

public class AggregateProjectScanRegisterRule extends RelRule<RelRule.Config> {
  private static final HiveRuleConfig INSTANCE = new HiveRuleConfig() {
    @Override
    public RelOptRule toRule() {
      throw new IllegalStateException();
    }
  };

  public AggregateProjectScanRegisterRule() {
    super(INSTANCE.withOperandSupplier(a -> a.operand(Aggregate.class)
        .oneInput(f -> f.operand(Project.class).oneInput(s -> s.operand(HiveTableScan.class).noInputs()))));
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    Aggregate a = call.rel(0);
    Project p = call.rel(1);
    HiveTableScan ts = call.rel(2);
    ScanRegistry r = call.getPlanner().getContext().unwrap(ScanRegistry.class);
    r.add(ts.copy(ts.getRowType()), new ScanRegistry.NodeInfo(p.getProjects(), call.builder().literal(true), a.getGroupSet(), a.getAggCallList()));
  }
}
