-- Workaround 2: Change the default timezone of the JVM (e.g., use MST)
--! qt:timezone:MST
create table t1 (event_time timestamp) stored as parquet;

load data local inpath '../../data/files/engesc30381_data.parquet' into table t1;

set hive.parquet.timestamp.legacy.conversion.enabled=false;
select event_time from t1;
