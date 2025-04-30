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
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.ql.exec.ColumnInfo;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.NotNullConstraint;
import org.apache.hadoop.hive.ql.metadata.PrimaryKeyInfo;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.metadata.VirtualColumn;
import org.apache.hadoop.hive.ql.optimizer.calcite.translator.TypeConverter;
import org.apache.hadoop.hive.ql.parse.ColumnStatsList;
import org.apache.hadoop.hive.ql.parse.PrunedPartitionList;
import org.apache.hadoop.hive.ql.parse.QueryTables;
import org.apache.hadoop.hive.ql.parse.RowResolver;
import org.apache.hadoop.hive.serde2.Deserializer;
import org.apache.hadoop.hive.serde2.SerDeException;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HMSRelOptSchema implements RelOptSchema {
  private final HiveConf conf;
  private final RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl(new HiveTypeSystemImpl());
  private final QueryTables queryTables = new QueryTables();
  private final Map<String, PrunedPartitionList> partitionCache = new HashMap<>();
  private final Map<String, ColumnStatsList> colStatsCache = new HashMap<>();
  private final AtomicInteger noColsMissingStats = new AtomicInteger(0);

  public HMSRelOptSchema(HiveConf conf) {
    this.conf = conf;
  }

  @Override
  public RelOptTable getTableForMember(final List<String> names) {
    try {
      Table metaTable = Hive.get(conf).getTable(names.get(0), names.get(1));
      return createTable(metaTable);
    } catch (HiveException | SerDeException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RelDataTypeFactory getTypeFactory() {
    return typeFactory;
  }

  @Override
  public void registerRules(final RelOptPlanner planner) {

  }

  private RelOptTable createTable(Table tabMetaData) throws SerDeException {
    RowResolver rr = new RowResolver();
    String tableAlias = tabMetaData.getTableName();
    Deserializer deserializer = tabMetaData.getDeserializer();
    StructObjectInspector rowObjectInspector = (StructObjectInspector) deserializer.getObjectInspector();

    deserializer.handleJobLevelConfiguration(conf);

    List<? extends StructField> fields = rowObjectInspector.getAllStructFieldRefs();
    ColumnInfo colInfo;
    String colName;
    ArrayList<ColumnInfo> cInfoLst = new ArrayList<>();

    final NotNullConstraint nnc = tabMetaData.getNotNullConstraint();
    final PrimaryKeyInfo pkc = tabMetaData.getPrimaryKeyInfo();

    for (StructField structField : fields) {
      colName = structField.getFieldName();
      colInfo = new ColumnInfo(
          structField.getFieldName(),
          TypeInfoUtils.getTypeInfoFromObjectInspector(structField.getFieldObjectInspector()),
          isNullable(colName, nnc, pkc),
          tableAlias,
          false);
      // TODO skewed column is not set
      rr.put(tableAlias, colName, colInfo);
      cInfoLst.add(colInfo);
    }
    // TODO: Fix this
    ArrayList<ColumnInfo> nonPartitionColumns = new ArrayList<ColumnInfo>(cInfoLst);
    ArrayList<ColumnInfo> partitionColumns = new ArrayList<ColumnInfo>();

    // 3.2 Add column info corresponding to partition columns
    for (FieldSchema part_col : tabMetaData.getPartCols()) {
      colName = part_col.getName();
      colInfo = new ColumnInfo(
          colName,
          TypeInfoFactory.getPrimitiveTypeInfo(part_col.getType()),
          isNullable(colName, nnc, pkc),
          tableAlias,
          true);
      rr.put(tableAlias, colName, colInfo);
      cInfoLst.add(colInfo);
      partitionColumns.add(colInfo);
    }
    // put virtual columns into RowResolver.
    List<VirtualColumn> vcList = tabMetaData.getVirtualColumns();

    vcList.forEach(vc -> rr.put(
        tableAlias,
        vc.getName().toLowerCase(),
        new ColumnInfo(vc.getName(), vc.getTypeInfo(), tableAlias, true, vc.getIsHidden())));

    // Build row type from field <type, name>
    RelDataType rowType = TypeConverter.getType(typeFactory, rr, null);
    // Build RelOptAbstractTable
    List<String> fullyQualifiedTabName = new ArrayList<>();
    if (tabMetaData.getDbName() != null && !tabMetaData.getDbName().isEmpty()) {
      fullyQualifiedTabName.add(tabMetaData.getDbName());
    }

    fullyQualifiedTabName.add(tabMetaData.getTableName());

    return new RelOptHiveTable(
        this,
        typeFactory,
        fullyQualifiedTabName,
        rowType,
        tabMetaData,
        nonPartitionColumns,
        partitionColumns,
        vcList,
        conf,
        queryTables,
        partitionCache,
        colStatsCache,
        noColsMissingStats);
  }

  private boolean isNullable(String colName, NotNullConstraint notNullConstraints, PrimaryKeyInfo primaryKeyInfo) {
    if (notNullConstraints != null && notNullConstraints.getNotNullConstraints().containsValue(colName)) {
      return false;
    }

    if (primaryKeyInfo != null && primaryKeyInfo.getColNames().containsValue(colName)) {
      return false;
    }

    return true;
  }
}
