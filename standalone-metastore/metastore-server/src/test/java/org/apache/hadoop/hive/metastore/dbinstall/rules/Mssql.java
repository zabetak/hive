/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.metastore.dbinstall.rules;

import org.apache.hive.testutils.docker.MssqlContainer;

import java.io.IOException;

/**
 * JUnit TestRule for Mssql.
 */
public class Mssql extends DatabaseRule {
  private final MssqlContainer container = new MssqlContainer("Its-a-s3cret");

  @Override
  public void before() throws IOException, InterruptedException {
    container.start();
  }

  @Override
  public void after() {
    container.stop();
  }

  @Override
  public String getDbType() {
    return "mssql";
  }

  @Override
  public String getDbRootUser() {
    return "SA";
  }

  @Override
  public String getDbRootPassword() {
    return "Its-a-s3cret";
  }

  @Override
  public String getJdbcDriver() {
    return com.microsoft.sqlserver.jdbc.SQLServerDriver.class.getName();
    // return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
  }

  @Override
  public String getJdbcUrl() {
    return "jdbc:sqlserver://" + container.getHostAddress() + ":1433;DatabaseName=" + HIVE_DB + ";";
  }

  @Override
  public String getInitialJdbcUrl() {
    return "jdbc:sqlserver://" + container.getHostAddress() + ":1433";
  }

  @Override
  public String getHivePassword() {
    return "h1vePassword!";
  }
}
