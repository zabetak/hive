-- Workaround 3: Patch the JDK installation using custom timezone data (see parquet_engesc30381_w3.md)
--! qt:timezone:America/Phoenix
create table t1 (event_time timestamp) stored as parquet;

load data local inpath '../../data/files/engesc30381_data.parquet' into table t1;

set hive.parquet.timestamp.legacy.conversion.enabled=false;
select event_time from t1;