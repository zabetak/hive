-- Reproducer:
-- Load the Parquet data file produced by Spark in a Hive table
-- Set the default timezone to America/Phoenix
-- Disable legacy timestamp conversion to trigger the problem simulating what happens in older Hive versions

--! qt:timezone:America/Phoenix
create table t1 (event_time timestamp) stored as parquet;

load data local inpath '../../data/files/engesc30381_data.parquet' into table t1;

set hive.parquet.timestamp.legacy.conversion.enabled=false;
select event_time from t1;