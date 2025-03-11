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

import com.github.zabetak.jdbc.faulty.DelayFault;
import com.github.zabetak.jdbc.faulty.FaultyJDBCDriver;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.metastore.annotation.MetastoreUnitTest;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.client.builder.DatabaseBuilder;
import org.apache.hadoop.hive.metastore.client.builder.TableBuilder;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf;
import org.apache.hadoop.hive.metastore.conf.MetastoreConf.ConfVars;
import org.apache.hadoop.hive.metastore.dbinstall.rules.DatabaseRule;
import org.apache.hadoop.hive.metastore.dbinstall.rules.Mysql;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Category(MetastoreUnitTest.class)
public class TestObjectStoreConnectionLeak {
  private static final Logger LOG = LoggerFactory.getLogger(TestObjectStoreConnectionLeak.class);
  private static final DatabaseRule DBMS = new Mysql();

  @BeforeAll
  static void setup() throws Exception {
    DBMS.before();
    DBMS.install();
    try (Connection connection = DriverManager.getConnection(DBMS.getJdbcUrl(), DBMS.getDbRootUser(),
        DBMS.getDbRootPassword())) {
      Statement stmt = connection.createStatement();
      stmt.execute("SET GLOBAL wait_timeout=2");
    }
  }

  @AfterAll
  static void teardown() throws Exception {
    DBMS.after();
  }

  private static ObjectStore create() {
    Configuration conf = MetastoreConf.newMetastoreConf();
    MetastoreConf.setBoolVar(conf, ConfVars.HIVE_IN_TEST, true);
    conf.set("hikaricp.connectionTimeout", "500");
    MetaStoreTestUtils.setConfForStandloneMode(conf);
    MetastoreConf.setLongVar(conf, ConfVars.CONNECTION_POOLING_MAX_CONNECTIONS, 1);
    MetastoreConf.setVar(conf, ConfVars.CONNECTION_DRIVER, DBMS.getJdbcDriver());
    MetastoreConf.setVar(conf, ConfVars.CONNECT_URL_KEY, DBMS.getJdbcUrl());
    MetastoreConf.setVar(conf, ConfVars.PWD, DBMS.getHivePassword());
    MetastoreConf.setVar(conf, ConfVars.CONNECTION_USER_NAME, DBMS.getHiveUser());
    MetastoreConf.setVar(conf, ConfVars.CONNECT_URL_KEY, DBMS.getJdbcUrl().replace("jdbc:", "jdbc:faulty:"));
    ObjectStore store = new ObjectStore();
    store.setConf(conf);
    return store;
  }


  @Test
  public void testCreateTableWithRandomTimeoutOnCommit() throws Exception {
    ObjectStore store = create();
    store.createDatabase(
        new DatabaseBuilder().setName("default").setLocation("file:///path/to/default").build(store.getConf()));
    // Add a fault creating a delay that is bigger than the wait_timeout of the underlying database
    // in 8% of the Connection#commit calls that will eventually cause the operation to fail
    FaultyJDBCDriver.addFault("f1", new DelayFault(0.08,"commit", 3000));
    for (int i = 0; i < 200; i++) {
      String tableName = "table_" + i;
      try {
        ObjectStore s = create();
        s.createTable(newTable(tableName, s.getConf()));
        LOG.info("Created table {}", tableName);
      } catch (Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.startsWith("objectstore-secondary - Connection is not available")) {
          throw e;
        }
        LOG.error("Failed to create table" + tableName, e);
      }
    }
  }

  private static Table newTable(String name, Configuration conf) throws MetaException {
    TableBuilder builder = new TableBuilder()
        .setCatName("hive")
        .setDbName("default")
        .setTableName(name)
        .setLocation("file:///path/to/default/" + name);
    for (int i = 0; i < 10; i++) {
      builder.addCol("col"+i, "int");
    }
    return builder.build(conf);
  }

}
