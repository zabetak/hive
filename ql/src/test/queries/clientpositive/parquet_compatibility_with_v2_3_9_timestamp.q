create table timestamp_2_3_9_parquet (col timestamp)
stored as parquet;

load data local inpath '../../data/files/hive2_3_9_parquet_timestamps' into table timestamp_2_3_9_parquet;

select * from timestamp_2_3_9_parquet;

drop table timestamp_2_3_9_parquet;
