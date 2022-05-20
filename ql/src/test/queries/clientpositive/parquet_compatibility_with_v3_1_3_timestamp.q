create table timestamp_3_1_3_parquet (col timestamp)
stored as parquet;

load data local inpath '../../data/files/hive3_1_3_parquet_timestamps' into table timestamp_3_1_3_parquet;

select * from timestamp_3_1_3_parquet;

drop table timestamp_3_1_3_parquet;
