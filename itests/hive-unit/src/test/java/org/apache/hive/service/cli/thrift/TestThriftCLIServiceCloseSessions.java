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
package org.apache.hive.service.cli.thrift;

import org.apache.hadoop.hive.common.auth.HiveAuthUtils;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hive.jdbc.miniHS2.MiniHS2;
import org.apache.hive.service.auth.HiveAuthConstants;
import org.apache.hive.service.cli.HiveSQLException;
import org.apache.hive.service.cli.OperationHandle;
import org.apache.hive.service.cli.SessionHandle;
import org.apache.hive.service.rpc.thrift.TCLIService;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;

public class TestThriftCLIServiceCloseSessions {
  private static final HiveConf CONF = new HiveConf();
  protected static String USERNAME = "anonymous";
  protected static String PASSWORD = "anonymous";
  private static ClientProvider clientProvider;
  private static MiniHS2 hs2;

  @BeforeClass
  public static void setup() throws Exception {
    CONF.setVar(HiveConf.ConfVars.HIVE_LOCK_MANAGER, "org.apache.hadoop.hive.ql.lockmgr.EmbeddedLockManager");
    CONF.setBoolVar(HiveConf.ConfVars.HIVE_SERVER2_PARALLEL_COMPILATION, true);
    CONF.setIntVar(HiveConf.ConfVars.HIVE_SERVER2_PARALLEL_COMPILATION_LIMIT, 1);
    CONF.setBoolVar(HiveConf.ConfVars.HIVE_SUPPORT_CONCURRENCY, true);
    CONF.setVar(HiveConf.ConfVars.HIVE_TXN_MANAGER, "org.apache.hadoop.hive.ql.lockmgr.DbTxnManager");
    CONF.setVar(HiveConf.ConfVars.HIVE_SERVER2_AUTHENTICATION, HiveAuthConstants.AuthTypes.NOSASL.toString());
    CONF.setBoolVar(HiveConf.ConfVars.METASTORE_EVENT_DB_NOTIFICATION_API_AUTH, false);
    CONF.setVar(HiveConf.ConfVars.HIVE_TXN_TIMEOUT, "1s");
    hs2 = new MiniHS2.Builder().withConf(CONF).withRemoteMetastore().build();
    Map<String, String> overlay = new HashMap<>();
    overlay.put(HiveConf.ConfVars.METASTORE_EVENT_DB_NOTIFICATION_API_AUTH.varname, "false");
    overlay.put(HiveConf.ConfVars.HIVE_TXN_MANAGER.varname, "org.apache.hadoop.hive.ql.lockmgr.DbTxnManager");
    overlay.put(HiveConf.ConfVars.HIVE_SUPPORT_CONCURRENCY.varname, "true");
    hs2.start(overlay);
    clientProvider = new ClientProvider(hs2.getHost(), hs2.getBinaryPort());

    ThriftCLIServiceClient client = clientProvider.newClient();
    SessionHandle si = client.openSession(USERNAME, PASSWORD, new HashMap<>());
    client.executeStatement(si,
        "CREATE TABLE person (name STRING, age INT) stored as orc TBLPROPERTIES ('transactional'='true')",
        Collections.emptyMap());
    client.closeSession(si);
  }

  @AfterClass
  public static void cleanup() throws Exception {
    clientProvider.close();
    hs2.stop();
    hs2.cleanup();
  }

  @Test
  public void testNoTxnLeakWhenClosingSession() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    List<QueryTask> queries = new ArrayList<>();
    queries.add(new QueryTask(bigQuery()));
    queries.add(new QueryTask("EXPLAIN SELECT AVG(age) FROM person GROUP BY name"));
    queries.forEach(executor::submit);
    Thread.sleep(2000);
    ThriftCLIServiceClient c = clientProvider.newClient();
    c.closeSession(queries.get(1).activeSession());
    queries.forEach(QueryTask::stop);

    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);
    Thread.sleep(15000);
    List<String> transactions = showTransactions();
    assertTrue("Transaction leak:" + transactions, transactions.isEmpty());
  }

  private static List<String> showTransactions() throws HiveSQLException {
    ThriftCLIServiceClient c = clientProvider.newClient();
    SessionHandle s = c.openSession(USERNAME, PASSWORD, Collections.emptyMap());
    OperationHandle h = c.executeStatement(s, "SHOW TRANSACTIONS", Collections.emptyMap());
    List<String> txnIds = new ArrayList<>();
    for (Object[] row : c.fetchResults(h)) {
      System.out.println(Arrays.toString(row));
      txnIds.add(String.valueOf(row[0]));
    }
    c.closeOperation(h);
    c.closeSession(s);
    return txnIds;
  }

  private static class QueryTask implements Runnable {
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final String query;
    private final AtomicReference<SessionHandle> session = new AtomicReference<>(null);
    private HiveSQLException error = null;

    public QueryTask(String query) {
      this.query = query;
    }

    @Override
    public void run() {
      ThriftCLIServiceClient c = clientProvider.newClient();
      SessionHandle s = null;
      try {
        s = c.openSession(USERNAME, PASSWORD, Collections.emptyMap());
        session.set(s);
        while (!stopped.get()) {
          OperationHandle h = c.executeStatement(s, query, Collections.emptyMap());
          c.closeOperation(h);
        }
      } catch (HiveSQLException e) {
        error = e;
      } finally {
        session.set(null);
        if (s != null) {
          try {
            c.closeSession(s);
          } catch (HiveSQLException e) {
            if (error != null) {
              error.addSuppressed(e);
            } else {
              error = e;
            }
          }
        }
      }
    }

    private void stop() {
      stopped.set(true);
    }

    private SessionHandle activeSession() {
      return session.get();
    }

  }

  private static String bigQuery() {
    StringBuilder sb = new StringBuilder("EXPLAIN SELECT COUNT(*) FROM ");
    for (int i = 0; i < 80; i++) {
      if (i > 0)
        sb.append(",");
      sb.append("person p");
      sb.append(i);
    }
    sb.append(" GROUP BY p0.name");
    return sb.toString();
  }

  private static final class ClientProvider {
    private final List<TTransport> all = new CopyOnWriteArrayList<>();
    private final String host;
    private final int port;

    public ClientProvider(String host, int port) {
      this.host = host;
      this.port = port;
    }

    public ThriftCLIServiceClient newClient() {
      TTransport transport;
      try {
        transport = HiveAuthUtils.getSocketTransport(host, port, 300000, Integer.MAX_VALUE - 1);
        transport.open();
      } catch (TTransportException e) {
        throw new RuntimeException(e);
      }
      // Store the transport so that we can close it all at the end
      all.add(transport);
      return new ThriftCLIServiceClient(new TCLIService.Client(new TBinaryProtocol(transport)), CONF);
    }

    public void close() {
      for (TTransport t : all) {
        if (t.isOpen()) {
          t.close();
        }
      }
      all.clear();
    }
  }
}
