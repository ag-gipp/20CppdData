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
