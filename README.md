# 20CppdData

## Log moritz 2020-01-26
```
[2020-01-26 19:31:17] Connected
> create database moritz
[2020-01-26 19:31:17] 1 row affected in 102 ms
> use moritz
[2020-01-26 19:31:28] completed in 112 ms
moritz> create table k1
        (
        	docid mediumint not null,
        	hash binary(20) not null
        )
[2020-01-26 19:36:43] completed in 222 ms
```
Check out this repo and copy the csv files to the database container, e.g., via `docker cp . hyplag_database_isg03:/tmp
`. Run `cat k3/* >k3.csv` to get get the original k3.csv file.
```
LOAD DATA INFILE '/tmp/k1.csv' into table k1
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 19:59:37] 18416 rows affected in 174 ms
moritz> create or replace table moritz.k2
        (
        	docid mediumint not null,
        	hash binary(20) not null
        )
[2020-01-26 20:01:51] completed in 104 ms
moritz> create or replace table moritz.k3
        (
        	docid mediumint not null,
        	hash binary(20) not null
        )
[2020-01-26 20:01:57] completed in 155 ms
moritz> LOAD DATA INFILE '/tmp/k2.csv' into table k2
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 20:03:01] 476066 rows affected in 1 s 599 ms
moritz> LOAD DATA INFILE '/tmp/k3.csv' into table k3
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 20:04:42] 18522790 rows affected in 1 m 1 s 55 ms
```
Add indexe
```
create index k1_hash_index
        	on k1 (hash)
[2020-01-26 20:07:40] completed in 365 ms
moritz> create index k2_hash_index
        	on k2 (hash)
[2020-01-26 20:11:16] completed in 3 s 518 ms
moritz> create index k3_hash_index
            on k3 (hash)
[2020-01-26 20:13:03] completed in 1 m 17 s 1 ms
```
Frequency distributions [k1](/dist/k1.csv), [k2](/dist/k2.csv), [k3](/dist/k3.csv)
```
Select T.freq, count(T.hash) from (Select hash, count(docid) freq from k1 group by hash) T group by T.freq
```
Table sizes (index nur auf hash)
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
-->
Table,Size (MB)
k1,3.03
k2,40.11
k3,1549.98
```
Adding docid indexe
```
moritz> create index k2_doc_index
            on k2 (docid)
[2020-01-26 20:48:34] completed in 1 s 748 ms
moritz> create index k1_doc_index
            on k1 (docid)
[2020-01-26 20:48:37] completed in 376 ms
moritz> create index k3_doc_index
            on k3 (docid)
[2020-01-26 20:49:20] completed in 41 s 304 ms
```
leads to new sizes? no!
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
-->
Table,Size (MB)
k1,3.31
k2,48.63
k3,1845.80
```
