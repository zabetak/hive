create table test (m map<string,string>);
insert into table test values (map("a","b"));
select * from test where m in (map("a","b"));