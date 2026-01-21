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

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.hep.HepPlanner;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConfForTest;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hive.testutils.HiveTestEnvSetup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.fail;

public class TestHiveRelJsonReader {
  private static final Path TPCDS_RESULTS_PATH =
      Path.of(HiveTestEnvSetup.HIVE_ROOT, "ql/src/test/results/clientpositive/perf/tpcds30tb/");

  static Stream<Path> inputJsonFiles() throws IOException {
    return Files.list(TPCDS_RESULTS_PATH.resolve("json"));
  }

  @BeforeAll
  static void setupSession() {
    // The FunctionRegistry API that is used under the hood requires a SessionState
    // so we need to start one.
    HiveConf conf = new HiveConfForTest(TestHiveRelJsonReader.class);
    SessionState.start(conf);
  }

  @AfterAll
  static void tearDownSession() throws IOException {
    SessionState.get().close();
  }

  @ParameterizedTest
  @MethodSource("inputJsonFiles")
  public void testReadJson(Path jsonFile) throws IOException {
    String jsonContent =
        Files.readAllLines(jsonFile).stream().filter(line -> !line.startsWith("Warning")).collect(Collectors.joining());
    RelOptCluster cluster = RelOptCluster.create(
        new HepPlanner(HepProgram.builder().build(), Contexts.of(SessionState.get().getConf())),
        new HiveRexJsonBuilder());
    Stream<String> actualPlan = RelOptUtil.toString(new HiveRelJsonReader(cluster).readJson(jsonContent)).lines();
    Stream<String> expectedPlan = readExpectedPlan(jsonFile);
    assertLinesMatch(normalize(jsonFile, expectedPlan), normalize(jsonFile, actualPlan), "Failed for: " + jsonFile);
  }

  private static Stream<String> readExpectedPlan(Path jsonFile) throws IOException {
    String cboFileName = jsonFile.getFileName().toString().replace("query", "cbo_query");
    Path cboFile = TPCDS_RESULTS_PATH.resolve("tez").resolve(cboFileName);
    if (!Files.exists(cboFile)) {
      fail("CBO file not found for JSON file: " + jsonFile + ", expected at: " + cboFile);
    }
    return Files.readAllLines(cboFile).stream();
  }

  private static Stream<String> normalize(Path file, Stream<String> planLines) {
    Function<String, String> normalizer = Function.identity();
    for (Issue i : Issue.values()) {
      if (i.affects(file.getFileName().toString())) {
        normalizer = normalizer.andThen(i::normalize);
      }
    }
    return planLines.filter(line -> !line.startsWith("Warning"))
        .filter(line -> !line.startsWith("CBO PLAN:"))
        .filter(line -> !line.isBlank())
        .map(normalizer);
  }

  /**
   * A known issue that affects certain files and requires normalization of plan lines for comparison.
   */
  private enum Issue {
    /**
     * An issue with scientific notation discrepancies.
     */
    SCIENTIFIC_NOTATION(
        Pattern.compile("\\d+(\\.\\d+)?E\\d+"),
        "query34.q.out",
        "query39.q.out",
        "query73.q.out",
        "query83.q.out") {
      /**
       * Normalizes the input line by replacing scientific notation with plain string representation.
       */
      @Override
      String normalize(String line) {
        return pattern.matcher(line).replaceAll(mr -> new BigDecimal(mr.group()).toPlainString());
      }
    },
    /**
     * An issue with aggregate function discrepancies. At the moment the deserializer fails to
     * distinguish between COUNT (from {@link org.apache.calcite.sql.fun.SqlStdOperatorTable}) and
     * count (from {@link org.apache.hadoop.hive.ql.optimizer.calcite.functions.HiveSqlCountAggFunction})
     * operators. The deserializer always picks the latter no matter which one is really present
     * in the original plan.
     */
    AGG_FUNCTION(
        Pattern.compile("COUNT\\(", Pattern.CASE_INSENSITIVE),
        "query6.q.out",
        "query14.q.out",
        "query54.q.out",
        "query58.q.out") {
      /**
       * Normalizes the input line by converting occurrences of the COUNT aggregate function name to lowercase.
       */
      @Override
      String normalize(String line) {
        return pattern.matcher(line).replaceAll(mr -> mr.group().toLowerCase());
      }
    };

    protected final Pattern pattern;
    private final Set<String> affectedFiles;

    Issue(Pattern pattern, String... files) {
      this.pattern = pattern;
      this.affectedFiles = ImmutableSet.copyOf(files);
    }

    boolean affects(String fileName) {
      return affectedFiles.contains(fileName);
    }

    abstract String normalize(String line);
  }
}