-- Workaround 4: Enable legacy timestamp conversion (only applicable in newer Hive versions with HIVE-24074)
--! qt:timezone:America/Phoenix
create table t1 (event_time timestamp) stored as parquet;

load data local inpath '../../data/files/engesc30381_data.parquet' into table t1;

set hive.parquet.timestamp.legacy.conversion.enabled=true;
select event_time from t1;
