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
package org.apache.hadoop.hive.ql.optimizer.calcite;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.ColumnInfo;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.metadata.VirtualColumn;
import org.apache.hadoop.hive.ql.optimizer.calcite.translator.TypeConverter;
import org.apache.hadoop.hive.ql.parse.ColumnStatsList;
import org.apache.hadoop.hive.ql.parse.ParsedQueryTables;
import org.apache.hadoop.hive.ql.parse.PrunedPartitionList;
import org.apache.hadoop.hive.ql.parse.QueryTables;
import org.apache.hadoop.hive.ql.plan.ColStatistics;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ScottRelOptSchema implements RelOptSchema {
  private final Map<List<String>, RelOptTable> tableMap = new HashMap<>();
  private final RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl(new HiveTypeSystemImpl());

  public ScottRelOptSchema() {
    RelDataType deptType = typeFactory.builder()
        .add("deptno", SqlTypeName.DECIMAL, 2, 0)
        .nullable(false)
        .add("dname", SqlTypeName.VARCHAR, 14)
        .add("loc", SqlTypeName.VARCHAR, 13)
        .build();
    RelDataType empType = typeFactory.builder()
        .add("empno", SqlTypeName.DECIMAL, 4, 0)
        .nullable(false)
        .add("ename", SqlTypeName.VARCHAR, 10)
        .add("job", SqlTypeName.VARCHAR, 9)
        .add("mgr", SqlTypeName.DECIMAL, 4, 0)
        .add("deptno", SqlTypeName.DECIMAL, 2, 0)
        .nullable(false)
        .build();
    RelOptTable dept = createTable(Arrays.asList("scott", "dept"), deptType, 4);
    RelOptTable emp = createTable(Arrays.asList("scott", "emp"), empType, 13);
    tableMap.put(dept.getQualifiedName(), dept);
    tableMap.put(emp.getQualifiedName(), emp);
  }

  private RelOptTable createTable(List<String> name, RelDataType type, double rowCount) {
    Table hiveTable = new Table(name.get(0), name.get(1));
    List<ColumnInfo> columns = new ArrayList<>();
    List<ColStatistics>  colStats = new ArrayList<>();
    for (RelDataTypeField f : type.getFieldList()) {
      TypeInfo info = TypeConverter.convert(f.getType());
      columns.add(new ColumnInfo(f.getName(), info, f.getType().isNullable(), name.get(1), false, false));
      ColStatistics stats = new ColStatistics(f.getName(), info.getTypeName());
      colStats.add(stats);
    }
    TestTable tbl =
        new TestTable(this,hiveTable, name,type,columns);
    tbl.setRowCount(rowCount);
    tbl.stats = colStats;
    return tbl;
  }

  @Override
  public RelOptTable getTableForMember(final List<String> names) {
    return tableMap.get(names);
  }

  @Override
  public RelDataTypeFactory getTypeFactory() {
    return typeFactory;
  }

  @Override
  public void registerRules(final RelOptPlanner planner) {

  }

  private class TestTable extends RelOptHiveTable {
    private List<ColStatistics> stats = Collections.emptyList();
    public TestTable(RelOptSchema schema, Table hiveTable, List<String> name, RelDataType type, List<ColumnInfo>columns) {
      super(schema, typeFactory, name, type, hiveTable, columns, Collections.emptyList(),
          Collections.emptyList(), new HiveConf(), new QueryTables(), new HashMap<>(), new HashMap<>(),
          new AtomicInteger(0));
    }

    @Override
    public List<ColStatistics> getColStat(final List<Integer> projIndxLst) {
      List<ColStatistics> r = new ArrayList<>();
      for (int i = 0; i < projIndxLst.size(); i++) {
        r.add(stats.get(projIndxLst.get(i)));
      }
      return r;
    }
  }
}
