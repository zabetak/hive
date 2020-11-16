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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

/**
 * JUnit TestRule for Postgres metastore with TPCDS schema and stat information.
 */
public class PostgresTPCDS extends Postgres {
  @Override
  public String getDockerImageName() {
    return "zabetak/postgres-tpcds-metastore:1.1";
  }

  @Override
  public String getJdbcUrl() {
    return "jdbc:postgresql://localhost:5432/metastore";
  }

  @Override
  public String getHiveUser() {
    return "hive";
  }

  @Override
  public String getHivePassword() {
    return "hive";
  }

  @Override
  public void install() {
    // Do not do anything since the postgres container contains a fully initialized
    // metastore
  }

  @Override
  public boolean isContainerReady(String logOutput) {
    SocketAddress socketAddress = new InetSocketAddress("localhost", 5432);
    Socket socket = new Socket();

    try {
      socket.connect(socketAddress, 1000);
      socket.close();
      return logOutput.contains("PostgreSQL init process complete; ready for start up");
    } catch (SocketTimeoutException exception) {
      return false;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}

