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

import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgramBuilder;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.optimizer.calcite.rules.HiveInBetweenExpandRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonTableExpressionAggFilterSuggester implements CommonTableExpressionSuggester {
  @Override
  public List<RelNode> suggest(RelNode input, Configuration configuration) {
    HepProgramBuilder b = new HepProgramBuilder();
    b.addRuleInstance(HiveInBetweenExpandRule.FILTER_INSTANCE);
    b.addRuleInstance(new AggregateFilterScanRegisterRule());
    ScanRegistry sr = new ScanRegistry();
    HepPlanner planner = new HepPlanner(b.build(), Contexts.of(sr));
    planner.setRoot(input);
    planner.findBestExp();
    RelBuilder builder = HiveRelFactories.HIVE_BUILDER.create(input.getCluster(), null);
    List<RelNode> suggestions = new ArrayList<>();
    sr.names().forEach(name -> {
      List<ScanRegistry.NodeInfo> info = sr.getInfo(name);
      TableScan scan = sr.getScan(name);
      Map<RexNode, List<ScanRegistry.NodeInfo>> filterToAggregate = new HashMap<>();

      for (ScanRegistry.NodeInfo nodeInfo : info) {
        List<ScanRegistry.NodeInfo> filterAggs =
            filterToAggregate.getOrDefault(nodeInfo.filterCondition, new ArrayList<>());
        filterAggs.add(nodeInfo);
        filterToAggregate.put(nodeInfo.filterCondition, filterAggs);
      }
      ImmutableBitSet allAggGroupSet = ImmutableBitSet.of();
      List<AggregateCall> allCalls = new ArrayList<>();
      filterToAggregate.forEach((key, value) -> {
        builder.clear();
        builder.push(scan);
        builder.filter(key);
        ImmutableBitSet groupSet = ImmutableBitSet.of();
        List<AggregateCall> aggCalls = new ArrayList<>();
        for (ScanRegistry.NodeInfo nodeInfo : value) {
          groupSet = groupSet.union(nodeInfo.groupSet);
          aggCalls.addAll(nodeInfo.aggCalls);
        }

        builder.aggregate(builder.groupKey(groupSet), aggCalls);
        suggestions.add(builder.build());
        allAggGroupSet.union(groupSet);
        allCalls.addAll(aggCalls);
      });
      // Create a big disjunctive filter over the table scan
      builder.clear();
      builder.push(scan);
      builder.filter(builder.or(filterToAggregate.keySet()));
      suggestions.add(builder.build());

      // Create a big disjunctive filter with all possible aggregations
      builder.clear();
      builder.push(scan);
      builder.filter(builder.or(filterToAggregate.keySet()));
      builder.aggregate(builder.groupKey(allAggGroupSet), allCalls);
      suggestions.add(builder.build());
    });
    return suggestions;
  }
}
