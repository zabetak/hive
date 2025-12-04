/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.optimizer.calcite;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.optimizer.calcite.rules.views.TestRuleBase;
import org.apache.hadoop.hive.ql.parse.QueryTables;
import org.apache.hive.testutils.HiveTestEnvSetup;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jline.utils.DiffHelper;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class TestRelPlanParser extends TestRuleBase {

  private RelNode ts1;
  private RelNode ts2;
  private HiveConf conf;
  private static final Logger LOG = LoggerFactory.getLogger(TestRelPlanParser.class);

  @Override
  public void setup() {
    super.setup();
    ts1 = createTS(t1NativeMock, "t1");
    ts2 = createTS(t2NativeMock, "t2");
    conf = new HiveConf();
  }

  @Test
  public void testSimpleJoin() throws IOException {
    RexNode joinCondition = REX_BUILDER.makeCall(SqlStdOperatorTable.EQUALS,
        REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(0).getType(), 0),
        REX_BUILDER.makeInputRef(ts2.getRowType().getFieldList().get(0).getType(), 5));

    /*
     * HiveProject(a=[$0], b=[$1], d=[$5])
     *   HiveJoin(condition=[=($0, $5)], joinType=[inner], algorithm=[none], cost=[not available])
     *     HiveFilter(condition=[IS NOT NULL($0)])
     *       HiveTableScan(table=[[default, t1]], table:alias=[t1])
     *     HiveFilter(condition=[IS NOT NULL($0)])
     *       HiveTableScan(table=[[default, t2]], table:alias=[t2])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .filter(REX_BUILDER.makeCall(SqlStdOperatorTable.IS_NOT_NULL, REX_BUILDER.makeInputRef(ts1, 0)))
        .push(ts2)
        .filter(REX_BUILDER.makeCall(SqlStdOperatorTable.IS_NOT_NULL, REX_BUILDER.makeInputRef(ts2, 0)))
        .join(JoinRelType.INNER, joinCondition)
        .project(
            REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(0).getType(), 0),
            REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(1).getType(), 1),
            REX_BUILDER.makeInputRef(ts2.getRowType().getFieldList().get(0).getType(), ts1.getRowType().getFieldCount()))
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testAggregate() throws IOException {
    /*
     * HiveAggregate(group=[{0, 1}], cs=[COUNT()], agg#1=[MIN($2)], agg#2=[SUM($2)])
     *   HiveTableScan(table=[[default, t1]], table:alias=[t1])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .aggregate(
            REL_BUILDER.groupKey(0, 1),
            REL_BUILDER.countStar("cs"),
            REL_BUILDER.min(REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(2).getType(), 2)),
            REL_BUILDER.sum(REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(2).getType(), 2)))
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testAntiJoin() throws IOException {
    RexNode joinCondition = REX_BUILDER.makeCall(SqlStdOperatorTable.EQUALS,
        REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(0).getType(), 0),
        REX_BUILDER.makeInputRef(ts2.getRowType().getFieldList().get(0).getType(), 5));

    /*
     * HiveAntiJoin(condition=[=($0, $5)], joinType=[anti])
     *   HiveTableScan(table=[[default, t1]], table:alias=[t1])
     *   HiveTableScan(table=[[default, t2]], table:alias=[t2])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .push(ts2)
        .join(JoinRelType.ANTI, joinCondition)
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testSortExchange() throws IOException {
    /*
     * HiveSortExchange(distribution=[any], collation=[[0]])
     *   HiveFilter(condition=[IS NOT NULL($0)])
     *     HiveTableScan(table=[[default, t1]], table:alias=[t1])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .filter(REX_BUILDER.makeCall(SqlStdOperatorTable.IS_NOT_NULL, REX_BUILDER.makeInputRef(ts1, 0)))
        .sortExchange(HiveRelDistribution.ANY, RelCollations.of(0))
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testSortLimit() throws IOException {
    /*
     * HiveSortLimit(sort0=[$0], dir0=[ASC], fetch=[10])
     *   HiveProject(a=[$0], b=[$1])
     *     HiveTableScan(table=[[default, t1]], table:alias=[t1])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .project(
            REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(0).getType(), 0),
            REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(1).getType(), 1))
        .sortLimit(-1, 10,
            REX_BUILDER.makeInputRef(ts1.getRowType().getFieldList().get(0).getType(), 0))
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testUnion() throws IOException {
    /*
     * HiveUnion(all=[true])
     *   HiveTableScan(table=[[default, t1]], table:alias=[t1])
     *   HiveTableScan(table=[[default, t2]], table:alias=[t2])
     */
    RelNode planToSerialize = REL_BUILDER
        .push(ts1)
        .push(ts2)
        .union(true)
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  @Test
  public void testValues() throws IOException {
    List<RexLiteral> values = ImmutableList.of(
        REX_BUILDER.makeExactLiteral(BigDecimal.ONE),
        REX_BUILDER.makeExactLiteral(BigDecimal.TEN)
    );

    List<RelDataType> schema = ImmutableList.of(
        TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER),
        TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER)
    );

    /*
     * HiveValues(tuples=[[{ 1, 10 }]])
     */
    RelNode planToSerialize = REL_BUILDER
        .values(ImmutableList.of(values), TYPE_FACTORY.createStructType(schema, ImmutableList.of("a", "b")))
        .build();

    serializeDeserializeAndAssertEquals(planToSerialize);
  }

  private void serializeDeserializeAndAssertEquals(RelNode plan) throws IOException {
    Optional<String> planJson = HiveRelOptUtil.serializeToJSON(plan);
    if (planJson.isPresent()) {
      RelPlanParser parser = new RelPlanParser(relOptCluster, conf, null);
      RelNode parsedPlan = parser.parse(planJson.get());

      assertEquals(RelOptUtil.toString(plan), RelOptUtil.toString(parsedPlan));
    } else {
      fail("failed to serialize plan");
    }
  }
  
  @Test
  public void testDeserializeFromFile() throws IOException {
    final File tpcdsJsonDirectory =
        new File(HiveTestEnvSetup.HIVE_ROOT + "ql/src/test/results/clientpositive/perf/tpcds30tb/json");
    final File tpcdsCBODirectory =
        new File(HiveTestEnvSetup.HIVE_ROOT + "ql/src/test/results/clientpositive/perf/tpcds30tb/tez");

    Path tpcdsCBOActualDir = Files.createTempDirectory("cbo_actual");

    File[] jsonFiles = Objects.requireNonNull(tpcdsJsonDirectory.listFiles());
    Arrays.sort(jsonFiles);

    List<Executable> executables = new ArrayList<>();
    for (File jsonFile : jsonFiles) {
      String cboFileName = jsonFile.getName().replace("query", "cbo_query");
      File cboFile = new File(tpcdsCBODirectory, cboFileName);
      if (!cboFile.exists()) {
        LOG.warn("CBO file not found for JSON file: {}, expected at: {}", jsonFile.getName(), cboFile.getPath());
        continue;
      }

      System.out.println("Processing JSON file: " + jsonFile.getName() + " with CBO file: " + cboFile.getName());
      ObjectMapper mapper = new ObjectMapper();
      mapper.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
      String jsonPlan = Files.readAllLines(Paths.get(jsonFile.getPath()))
          .stream()
          .filter(line -> !line.startsWith("Warning"))
          .collect(Collectors.joining());
      JsonNode node = mapper.readTree(jsonPlan);
      Map<List<String>, RelDataType> tables = new HashMap<>();
      for(JsonNode scan: node.findParents("table")){
        List<String> names = new ArrayList<>();
        for (JsonNode n : scan.get("table")) {
          names.add(n.asText());
        }
        JsonNode type = scan.get("rowType");
        tables.put(names, RelJsonReader.readType(relOptCluster.getTypeFactory(), type.toString()));
      }
      RelOptSchema  relOptSchema = new RelOptSchema() {
        @Override
        public @Nullable RelOptTable getTableForMember(List<String> names) {
          RelDataType type = tables.get(names);

          return new RelOptHiveTable(
              this,
              relOptCluster.getTypeFactory(),
              names,
              type,
              new Table(names.get(0), names.get(1)),
              new ArrayList<>(),
              new ArrayList<>(),
              new ArrayList<>(),
              conf,
              new QueryTables(true),
              new HashMap<>(),
              new HashMap<>(),
              new AtomicInteger()
          );
        }

        @Override
        public RelDataTypeFactory getTypeFactory() {
          return null;
        }

        @Override
        public void registerRules(RelOptPlanner planner) throws Exception {

        }
      };

      Stream<String> cboPlan = Files.readAllLines(Paths.get(cboFile.getPath()))
          .stream()
          .filter(line -> !line.startsWith("Warning"))
          .filter(line -> !line.startsWith("CBO PLAN:"))
          .filter(line -> !line.isBlank());
      Stream<String> deserializedPlan =
          RelOptUtil.toString(HiveRelOptUtil.deserializePlan(conf, node.get("CBOPlan").toString(), relOptSchema)).lines()
              .filter(line -> !line.isBlank());
//      if (!cboPlan.equals(deserializedPlan)) {
//        Files.writeString(tpcdsCBOActualDir.resolve(cboFileName), String.join("", deserializedPlan), StandardOpenOption.CREATE);
//      }
      executables.add(() ->
        Assertions.assertLinesMatch(cboPlan, deserializedPlan,
          "Failed for: " + jsonFile.getName() + "\n"
        )
      );
    }
    
    Assertions.assertAll("Deserialized plans do not match CBO plans", executables);
  }
}