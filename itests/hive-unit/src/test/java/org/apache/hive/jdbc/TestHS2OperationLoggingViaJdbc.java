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

package org.apache.hive.jdbc;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.conf.HiveConf.ConfVars;
import org.apache.hadoop.hive.metastore.utils.TestTxnDbUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for sending multiple queries in HS2 to test operation logging (behavior & performance)
 * 
 * JDBC was chosen merely for convenience.
 */
public class TestHS2OperationLoggingViaJdbc {
  private static final String tableName = "testjdbcdrivertbl";

  @BeforeClass
  public static void setup() throws Exception {
    HiveConf conf = new HiveConf();
    TestTxnDbUtil.setConfValues(conf);
    TestTxnDbUtil.prepDb(conf);
    Class.forName("org.apache.hive.jdbc.HiveDriver");
    System.setProperty(ConfVars.HIVE_SERVER2_LOGGING_OPERATION_LEVEL.varname, "verbose");
    System.setProperty(ConfVars.HIVE_AUTHORIZATION_MANAGER.varname,
        "org.apache.hadoop.hive.ql.security.authorization.DefaultHiveAuthorizationProvider");
    System.setProperty(ConfVars.HIVE_SERVER2_PARALLEL_OPS_IN_SESSION.varname, "false");

    try (Connection con = DriverManager.getConnection("jdbc:hive2:///default", "", "")) {
      Statement stmt = con.createStatement();
      stmt.execute("create table " + tableName
          + " (under_col int comment 'the under column', value string) comment 'some comment'");
      String dataFileDir = conf.get("test.data.files").replace('\\', '/').replace("c:", "");
      Path dataPath = new Path(dataFileDir, "kv1.txt");
      stmt.execute("load data local inpath '" + dataPath + "' into table " + tableName);
      stmt.close();
    }
  }

  @Test
  public void testConcurrentQueries() throws InterruptedException {
    executeSelectCountQueries(300, 100);
  }

  @Test
  public void testSerialQueries() throws InterruptedException {
    executeSelectCountQueries(300, 1);
  }

  private void executeSelectCountQueries(int totalQueries, int concurrentQueries) throws InterruptedException {
    ExecutorService ex = Executors.newFixedThreadPool(concurrentQueries);
    AtomicInteger queryId = new AtomicInteger(0);
    for (int i = 0; i < totalQueries; i++) {
      ex.execute(() -> {
        try (Connection c = DriverManager.getConnection("jdbc:hive2:///default", "", "")) {
          // Change slightly the query on every run to avoid caching and be able to recognise it easily in the logs
          String sql = "select count(*) + " + queryId.getAndIncrement() + " from " + tableName;
          try (Statement stmt = c.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(sql)) {
              while (rs.next()) {
                System.out.println("Count=" + rs.getInt(1));
              }
            }
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
    }
    ex.shutdown();
    ex.awaitTermination(1, TimeUnit.DAYS);
  }

}
