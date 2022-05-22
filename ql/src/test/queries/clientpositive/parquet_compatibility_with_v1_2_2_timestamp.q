create table timestamp_1_2_2_parquet (col timestamp)
stored as parquet;

load data local inpath '../../data/files/hive1_2_2_parquet_timestamps' into table timestamp_1_2_2_parquet;

select * from timestamp_1_2_2_parquet;

drop table timestamp_1_2_2_parquet;
