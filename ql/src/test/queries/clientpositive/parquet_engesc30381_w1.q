-- Workaround 1: Handle dates before 1970 by rewriting the SQL queries and manually adjusting the offset
--! qt:timezone:America/Phoenix
create table t1 (event_time timestamp) stored as parquet;

load data local inpath '../../data/files/engesc30381_data.parquet' into table t1;
set hive.parquet.timestamp.legacy.conversion.enabled=false;

select case
           when event_time < TIMESTAMP '1970-01-01 00:00:00'
               then event_time + INTERVAL '28' minute + INTERVAL '18' second
           else event_time end
from t1;