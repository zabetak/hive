set hive.explain.user=false;

create table person (
    id int,
    name varchar(20)
);

insert into person values (1, 'Stam');
insert into person values (2, null);

explain
    cbo
select p1.id
from person p1
where exists
          (select id
           from person p2
           where coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER'));

select p1.id
from person p1
where exists
          (select id
           from person p2
           where coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER'));

-- explain
-- cbo
-- select p1.id
-- from person p1
-- inner join person p2 on coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER');
-- 
-- select p1.id
-- from person p1
--          inner join person p2 on coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER');



-- explain cbo
-- select p1.id, p2.name
-- from
-- person p1,
-- (select p.name
-- from person p
-- group by p.name) p2
-- where coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER');
-- 
-- select p1.id, p2.name
-- from
--     person p1,
--     (select p.name
--      from person p
--      group by p.name) p2
-- where coalesce(p1.name,'MARKER') = coalesce(p2.name,'MARKER');

-- explain cbo
-- select n1, n2
-- from
-- (select p.name n1, coalesce(p.name,'MARKER') n2
-- from person p
-- group by p.name) sub
-- where n1 IS NOT NULL;
-- 
-- select n1, n2
-- from
--     (select p.name n1, coalesce(p.name,'MARKER') n2
--      from person p
--      group by p.name) sub
-- where n1 IS NOT NULL;

-- explain cbo
-- select p1.id
-- from
-- (select p.id, coalesce(p.name,'MARKER') nm
-- from person p) p1,
-- (select p.id, coalesce(p.name,'MARKER') nm
--  from person p) p2
-- where p1.nm = p2.nm;
-- 
-- select p1.id
-- from
--     (select p.id, coalesce(p.name,'MARKER') nm
--      from person p) p1,
--     (select p.id, coalesce(p.name,'MARKER') nm
--      from person p) p2
-- where p1.nm = p2.nm;

-- explain cbo
-- select p1.id, p2.id, p3.id
-- from person p1
--          inner join person p2
--          inner join person p3
-- where coalesce(p1.name, 'MARKER') = coalesce(p2.name, 'MARKER')
--   AND coalesce(p2.name, 'MARKER') = coalesce(p3.name, 'MARKER');
-- 
-- select p1.id, p2.id, p3.id
-- from person p1
--          inner join person p2
--          inner join person p3
-- where coalesce(p1.name, 'MARKER') = coalesce(p2.name, 'MARKER')
--   AND coalesce(p2.name, 'MARKER') = coalesce(p3.name, 'MARKER');
-- explain
-- cbo
-- select sub.id, sub.m
-- from (select p.id, coalesce(p.name,'MARKER') as m 
-- from person p) sub
-- where sub.m IS NOT NULL;
-- 
-- select sub.id, sub.m
-- from (select p.id, coalesce(p.name,'MARKER') as m
--       from person p) sub
-- where sub.m IS NOT NULL; 

-- explain
--     cbo
-- select p1.id
-- from person p1
-- where exists
--           (select id
--            from person p2
--            where p1.name = p2.name);

