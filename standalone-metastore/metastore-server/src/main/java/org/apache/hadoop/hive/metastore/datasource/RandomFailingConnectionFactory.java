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
package org.apache.hadoop.hive.metastore.datasource;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Random;

/**
 * A factory for connections that will occasionally fail on commit.
 */
public class RandomFailingConnectionFactory {
  private static final Random RANDOM = new Random(11);

  public static Connection create(Connection realConnection) {
    return (Connection) Proxy.newProxyInstance(RandomFailingConnectionFactory.class.getClassLoader(),
        new Class[] { Connection.class }, (proxy, method, args) -> {
          if ("commit".equals(method.getName()) && RANDOM.nextInt(100) > 90) {
            if (Arrays.stream(Thread.currentThread().getStackTrace())
                .anyMatch(e -> e.getMethodName().equalsIgnoreCase("obtainGenerationBlock"))) {
              throw new SQLException("Random exception");
            }
          }
          return method.invoke(realConnection, args);
        });
  }
}
