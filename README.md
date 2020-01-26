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
Check out this repo and copy the csv files to the database container, e.g., via `docker cp . hyplag_database_isg03:/root
`. Run `cat k3/* >k3.csv` to get get the original k3.csv file.
