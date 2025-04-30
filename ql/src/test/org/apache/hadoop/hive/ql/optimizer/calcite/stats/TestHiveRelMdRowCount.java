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
package org.apache.hadoop.hive.ql.optimizer.calcite.stats;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.tools.RelBuilder;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveDefaultRelMetadataProvider;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveRelFactories;
import org.apache.hadoop.hive.ql.optimizer.calcite.ScottRelOptSchema;
import org.apache.hadoop.hive.ql.parse.CalcitePlanner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestHiveRelMdRowCount {

  @Test
  public void testAntiJoin() {
    HiveConf conf = new HiveConf();
    RelOptPlanner planner = CalcitePlanner.createPlanner(conf);
    ScottRelOptSchema schema = new ScottRelOptSchema();
    RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(schema.getTypeFactory()));
    HiveDefaultRelMetadataProvider mdProvider = new HiveDefaultRelMetadataProvider(conf);
    cluster.setMetadataProvider(mdProvider.getMetadataProvider());
    RelBuilder builder = HiveRelFactories.HIVE_BUILDER.create(cluster, schema);
    RelNode plan = builder.scan("scott", "emp")
        .scan("scott", "dept")
        .antiJoin(builder.equals(builder.field(2, "emp", "deptno"), builder.field(2, "dept", "deptno")))
        .build();

    assertEquals(13, cluster.getMetadataQuery().getRowCount(plan).doubleValue(), 0.05);

  }
}
