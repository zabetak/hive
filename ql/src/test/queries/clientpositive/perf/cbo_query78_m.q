set hive.auto.convert.anti.join=true;
EXPLAIN CBO JOINCOST
select ss_item_sk, ss_customer_sk
from store_sales
left join store_returns
    on sr_ticket_number=ss_ticket_number and ss_item_sk=sr_item_sk
where sr_ticket_number is null
group by ss_item_sk, ss_customer_sk;
