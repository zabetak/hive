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
package org.apache.hadoop.hive.cli.control;

import org.apache.hadoop.hive.ql.MetaStoreDumpUtility;
import org.apache.hadoop.hive.ql.QTestSystemProperties;

/**
 * CliDriver initialising the metastore with basic table and column statistics from various TPC-DS datasets.
 *
 * The table statistics do not reflect cardinalities from a specific TPC-DS scale factor (SF). Below we outline the
 * tables and the TPC-DS SF to which it corresponds.
 *
 * Tables in 30TB dataset (SF=30000):
 * <ul>
 *  <li>call_center</li>
 *  <li>catalog_page</li>
 *  <li>customer</li>
 *  <li>customer_address</li>
 *  <li>item</li>
 *  <li>promotions</li>
 *  <li>store</li>
 *  <li>warehouse</li>
 *  <li>web_page</li>
 *  <li>web_site</li>
 * </ul>
 *
 * Tables in 200GB dataset (SF=300):
 * <ul>
 *   <li>catalog_returns</li>
 *   <li>catalog_sales</li>
 *   <li>store_returns</li>
 *   <li>store_sales</li>
 *   <li>web_sales</li>
 * </ul>
 *
 * Table in 3GB dataset (SF=3): inventory
 * Table with zero rows: ship_mode
 *
 * The fact that tables are in datasets with different scale factors is most likely a bug but given that this driver
 * has been in use for quite some time now it may make sense to keep around for a while.
 */
public class TPCDSMetastoreCliDriver extends CorePerfCliDriver {
  public TPCDSMetastoreCliDriver(AbstractCliConfig testCliConfig) {
    super(testCliConfig);
  }

  @Override
  protected void beforeClassSpec() {
    MetaStoreDumpUtility
        .setupMetaStoreTableColumnStatsFor30TBTPCDSWorkload(getQt().getConf(), QTestSystemProperties.getTempDir());
  }
}
