create table t11_n1 (c1 string, c2 string);
-- insert into t11_n1 VALUES ('A', 'A');
-- insert into t11_n1 VALUES ('A', 'B');
-- 
-- explain cbo select * from t11_n1 where c1 IN (c2,'100');
-- explain select * from t11_n1 where c1 IN (c2,'100');
-- 
-- explain cbo select * from t11_n1 where c1 IN (c2);
-- explain select * from t11_n1 where c1 IN (c2);
-- select * from t11_n1 where c1 IN (c2);

create table t_map (id int, c1 map<int,int>, c2 map<int,int>);
insert into t_map VALUES (1, map(1,1), map(2,1));
insert into t_map VALUES (2, map(1,2), map(2,2));
insert into t_map VALUES (3, map(1,3), map(2,3));
insert into t_map VALUES (4, map(1,4), map(1,4));

select id from t_map where c1 IN (c1);
select id from t_map where c1 IN (c2);
select id from t_map where c1 IN (map(1,1));
select id from t_map where map(1,1) IN (c1);
select id from t_map where c1 IN (map(1,1), map(1,2));
select id from t_map where c1 IN (map(1,1), map(1,2), map(1,3));
select id from t_map where c1 IN (c2, map(1,1), map(1,2), map(1,3));
select id from t_map where map(1,1) IN (c1, c2);

-- explain select * from t_map where c1 IN (c2);
-- select * from t_map where c1 IN (c2);
-- 

