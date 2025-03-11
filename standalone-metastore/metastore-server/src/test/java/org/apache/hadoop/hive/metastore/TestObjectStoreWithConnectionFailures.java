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
package org.apache.hadoop.hive.metastore;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.annotation.MetastoreUnitTest;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.client.builder.DatabaseBuilder;
import org.apache.hadoop.hive.metastore.client.builder.TableBuilder;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf.ConfVars;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

@Category(MetastoreUnitTest.class)
public class TestObjectStoreWithConnectionFailures {
  private static final AtomicInteger ID = new AtomicInteger();

  private static ObjectStore create() throws Exception {
    Configuration conf = MetastoreConf.newMetastoreConf();
    MetastoreConf.setBoolVar(conf, ConfVars.HIVE_IN_TEST, true);
    conf.set("hikaricp.connectionTimeout", "300");
    MetaStoreTestUtils.setConfForStandloneMode(conf);
    String url = "jdbc:derby:memory:metastore_db_" + ID.getAndIncrement() + ";create=true";
    try (Connection connection = DriverManager.getConnection(url)) {
      Statement stmt = connection.createStatement();
      // Decrease timeout in Derby to fail-fast when lock cannot be obtained
      stmt.execute("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.locks.waitTimeout','0')");
    }
    MetastoreConf.setVar(conf, ConfVars.CONNECT_URL_KEY, url.replace("jdbc:", "jdbc:faulty:"));
    System.setProperty("jdbc.faulty.random.seed", "33");
    System.setProperty("jdbc.faulty.failure.percentage", "0.1");
    ObjectStore store = new ObjectStore();
    store.setConf(conf);
    return store;
  }

  @Test
  public void testConnectionLeakOnSecondaryPool() throws Exception {
    ObjectStore store = create();
    store.createDatabase(
        new DatabaseBuilder().setName("default").setLocation("file:///path/to/default").build(store.getConf()));
    for (int i = 0; i < 100; i++) {
      String tblName = "test_table_" + i;
      Table tbl = new TableBuilder()
          .setDbName("default")
          .setTableName(tblName)
          .addCol("col1", "int")
          .addCol("col2", "int")
          .setLocation("file:///test/warehouse/" + tblName)
          .build(store.getConf());
      try {
        store.createTable(tbl);
      } catch (Exception e) {
        if (e.getMessage().startsWith("objectstore-secondary - Connection is not available")) {
          throw e;
        }
        // Ignore any exceptions that are not related to the leak
      }
    }
  }
}

