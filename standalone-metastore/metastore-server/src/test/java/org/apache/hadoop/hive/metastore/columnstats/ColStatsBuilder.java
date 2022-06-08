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
package org.apache.hadoop.hive.metastore.columnstats;

import org.apache.hadoop.hive.common.ndv.fm.FMSketch;
import org.apache.hadoop.hive.common.ndv.hll.HyperLogLog;
import org.apache.hadoop.hive.metastore.api.ColumnStatisticsData;
import org.apache.hadoop.hive.metastore.api.LongColumnStatsData;
import org.apache.hadoop.hive.metastore.columnstats.cache.LongColumnStatsDataInspector;

import java.lang.reflect.InvocationTargetException;

public class ColStatsBuilder {
  private Long low;
  private Long high;
  private long nulls;
  private long dvs;
  private byte[] bitVector;

  public ColStatsBuilder() {
  }

  public ColStatsBuilder nulls(long num) {
    this.nulls = num;
    return this;
  }

  public ColStatsBuilder dvs(long num) {
    this.dvs = num;
    return this;
  }

  public ColStatsBuilder high(long val) {
    this.high = val;
    return this;
  }

  public ColStatsBuilder low(long val) {
    this.low = val;
    return this;
  }

  public ColStatsBuilder hll(long... values) {
    HyperLogLog hll = HyperLogLog.builder().build();
    for (long value : values) {
      hll.addLong(value);
    }
    this.bitVector = hll.serialize();
    return this;
  }

  public ColStatsBuilder fms(long... values) {
    FMSketch fm = new FMSketch(1);
    for (long value : values) {
      fm.addToEstimator(value);
    }
    this.bitVector = fm.serialize();
    return this;
  }

  public ColumnStatisticsData buildLong() {
    ColumnStatisticsData data = new ColumnStatisticsData();
    LongColumnStatsData stats = newColData(LongColumnStatsDataInspector.class);
    data.setLongStats(stats);
    return data;
  }

  private <T> T newColData(Class<T> clazz) {
    try {
      T data = clazz.getDeclaredConstructor().newInstance();
      clazz.getMethod("setNumNulls", long.class).invoke(data, nulls);
      clazz.getMethod("setNumDVs", long.class).invoke(data, dvs);
      clazz.getMethod("setBitVectors", byte[].class).invoke(data, bitVector);
      if (low != null) {
        clazz.getMethod("setLowValue", long.class).invoke(data, low);
      }
      if (high != null) {
        clazz.getMethod("setHighValue", long.class).invoke(data, high);
      }
      return data;
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException("Reflection error", e);
    }
  }

}
