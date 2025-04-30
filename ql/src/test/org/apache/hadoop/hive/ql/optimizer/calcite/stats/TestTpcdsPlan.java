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
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.tools.RelBuilder;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.dbinstall.rules.PostgresTPCDS;
import org.apache.hadoop.hive.ql.optimizer.calcite.HMSRelOptSchema;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveDefaultRelMetadataProvider;
import org.apache.hadoop.hive.ql.optimizer.calcite.HiveRelFactories;
import org.apache.hadoop.hive.ql.parse.CalcitePlanner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestTpcdsPlan {

  private static final PostgresTPCDS HMS_DB = new PostgresTPCDS();
  private static final HiveConf CONF = new HiveConf();

  @BeforeClass
  public static void setup() throws Exception {
    HMS_DB.before();
    HMS_DB.install();
    MetastoreConf.setVar(CONF, MetastoreConf.ConfVars.CONNECT_URL_KEY, HMS_DB.getJdbcUrl());
    MetastoreConf.setVar(CONF, MetastoreConf.ConfVars.CONNECTION_DRIVER, HMS_DB.getJdbcDriver());
    MetastoreConf.setVar(CONF, MetastoreConf.ConfVars.CONNECTION_USER_NAME, HMS_DB.getHiveUser());
    MetastoreConf.setVar(CONF, MetastoreConf.ConfVars.PWD, HMS_DB.getHivePassword());
    // In this case we can disable auto_create which is enabled by default for every test
    MetastoreConf.setBoolVar(CONF, MetastoreConf.ConfVars.AUTO_CREATE_ALL, false);
  }

  @AfterClass
  public static void tearDown() {
    HMS_DB.after();
  }

  @Test
  public void testAntiJoin() {
    RelOptPlanner planner = CalcitePlanner.createPlanner(CONF);
    RelOptSchema schema = new HMSRelOptSchema(CONF);
    RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(schema.getTypeFactory()));
    HiveDefaultRelMetadataProvider mdProvider = new HiveDefaultRelMetadataProvider(CONF);
    cluster.setMetadataProvider(mdProvider.getMetadataProvider());
    RelBuilder builder = HiveRelFactories.HIVE_BUILDER.create(cluster, schema);
    RelNode plan = builder.scan("default", "store_sales")
        .as("ss")
        .scan("default", "store_returns")
        .as("sr")
        .antiJoin(builder.equals(
            builder.field(2, "ss", "ss_ticket_number"),
            builder.field(2, "sr", "sr_ticket_number")))
        .build();

    assertEquals(8.6404891377E10, cluster.getMetadataQuery().getRowCount(plan).doubleValue(), 0.0001);
    assertEquals(
        "HiveAntiJoin(condition=[=($8, $35)], joinType=[anti])\n" + "  HiveTableScan(table=[[default, store_sales]], table:alias=[store_sales])\n" + "  HiveTableScan(table=[[default, store_returns]], table:alias=[store_returns])\n",
        RelOptUtil.toString(plan));

  }

  @Test
  public void testSemiJoin() {
    RelOptPlanner planner = CalcitePlanner.createPlanner(CONF);
    RelOptSchema schema = new HMSRelOptSchema(CONF);
    RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(schema.getTypeFactory()));
    HiveDefaultRelMetadataProvider mdProvider = new HiveDefaultRelMetadataProvider(CONF);
    cluster.setMetadataProvider(mdProvider.getMetadataProvider());
    RelBuilder builder = HiveRelFactories.HIVE_BUILDER.create(cluster, schema);
    RelNode plan = builder.scan("default", "store_sales")
        .as("ss")
        .scan("default", "store_returns")
        .as("sr")
        .semiJoin(builder.equals(
            builder.field(2, "ss", "ss_ticket_number"),
            builder.field(2, "sr", "sr_ticket_number")))
        .build();

    assertEquals(8.6404891377E10, cluster.getMetadataQuery().getRowCount(plan).doubleValue(), 0.0001);
    assertEquals(
        "HiveSemiJoin(condition=[=($8, $35)], joinType=[semi])\n" + "  HiveTableScan(table=[[default, store_sales]], table:alias=[store_sales])\n" + "  HiveTableScan(table=[[default, store_returns]], table:alias=[store_returns])\n",
        RelOptUtil.toString(plan));

  }
}
