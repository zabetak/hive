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
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.parse.QueryTables;
import org.apache.hive.testutils.HiveTestEnvSetup;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

public class TestRelPlanParser {

  static File[] inputJsonFiles() {
    final File tpcdsJsonDirectory =
        new File(HiveTestEnvSetup.HIVE_ROOT + "ql/src/test/results/clientpositive/perf/tpcds30tb/json");
    String qfile = System.getProperty("qfile");
    if (qfile == null || qfile.isBlank()) {
      return Objects.requireNonNull(tpcdsJsonDirectory.listFiles());
    } else {
      return new File[] {new File(tpcdsJsonDirectory, qfile)};
    }
  }

  @ParameterizedTest
  @MethodSource("inputJsonFiles")
  public void testDeserializeFromFile(File jsonFile) throws IOException {
    HiveConf conf = new HiveConf();
    HepPlanner planner = new HepPlanner(HepProgram.builder().build());
    RelOptCluster relOptCluster = RelOptCluster.create(planner, new RexBuilder(new HiveTypeFactory()));
    final File tpcdsCBODirectory =
        new File(HiveTestEnvSetup.HIVE_ROOT + "ql/src/test/results/clientpositive/perf/tpcds30tb/tez");

    Path tpcdsCBOActualDir = Files.createTempDirectory("cbo_actual");

    String cboFileName = jsonFile.getName().replace("query", "cbo_query");
    File cboFile = new File(tpcdsCBODirectory, cboFileName);
    if (!cboFile.exists()) {
      fail("CBO file not found for JSON file: " + jsonFile.getName() + ", expected at: " + cboFile.getPath());
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
    for (JsonNode scan : node.findParents("table")) {
      List<String> names = new ArrayList<>();
      for (JsonNode n : scan.get("table")) {
        names.add(n.asText());
      }
      JsonNode type = scan.get("rowType");
      tables.put(names, RelJsonReader.readType(relOptCluster.getTypeFactory(), type.toString()));
    }
    RelOptSchema relOptSchema = new RelOptSchema() {
      @Override
      public @Nullable RelOptTable getTableForMember(List<String> names) {
        RelDataType type = tables.get(names);

        return new RelOptHiveTable(this, relOptCluster.getTypeFactory(), names, type,
            new Table(names.get(0), names.get(1)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), conf,
            new QueryTables(true), new HashMap<>(), new HashMap<>(), new AtomicInteger());
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
        RelOptUtil.toString(HiveRelOptUtil.deserializePlan(conf, node.get("CBOPlan").toString(), relOptSchema))
            .lines()
            .filter(line -> !line.isBlank());
    //      if (!cboPlan.equals(deserializedPlan)) {
    //        Files.writeString(tpcdsCBOActualDir.resolve(cboFileName), String.join("", deserializedPlan), StandardOpenOption.CREATE);
    //      }
    Assertions.assertLinesMatch(cboPlan, deserializedPlan, "Failed for: " + jsonFile.getName() + "\n");
  }
}