# 20CppdData

For your convenience, we preprocessed the dataset and extracted the hashes of the references used for the experiments described in the paper (*.csv).

Please follow the instructions below on how to import the documents to mysql,i.e., maria db 10, running on docker.

## 1 Investigation protocoll (Log moritz 2020-01-26)

### 1.1 Create a new database with a table to hold all k1-hashes:
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
### 1.2 Check out this repo and copy the csv-files to the database container
e.g., via `docker cp . hyplag_database_isg03:/tmp`.

or

Run `cat k3/* >k3.csv` to reassemble the original k3.csv file.

### 1.3 Fill the database with the repective data of the csv-files
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
### 1.4 Add indices
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
### 1.5 Determine the frequency distributions of all hashes
Frequency distributions [k1](/dist/k1.csv), [k2](/dist/k2.csv), [k3](/dist/k3.csv)
```
Select T.freq, count(T.hash) from (Select hash, count(docid) freq from k1 group by hash) T group by T.freq
```
### 1.6 Determine table sizes (tuples)
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
-->
Table,Size (MB)
k1,3.03
k2,40.11
k3,1549.98
```
### 1.7 Adding docid indices
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
### 1.8 Check if the new indices lead to different table sizes 
*Indices do not influence the table sizes in MariaDB*
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
-->
Table,Size (MB)
k1,3.31
k2,48.63
k3,1845.80
```

## 2 Second investiagtion using regular sets 

### 2.1 Importing the new dataset
```
docker cp hyplag_backend_cppd:/root/unique.tar .
tar -xf unique.tar
cat k1/*.csv > k1.csv
rm -rf k1
cat k2/*.csv > k2.csv
rm -rf k2
cat k3/*.csv > k3.csv
rm k3 -rf
mkdir k3
cp k3.csv k3
cd k3
split -b 10M k3.csv
rm k3.csv
cd ..
rm unique.tar
git add -A
```
### 2.2 Upload data to db-container
```
physikerwelt@dke01:~$ mkdir tmp
physikerwelt@dke01:~$ cp 20CppdData/k*.csv tmp/
physikerwelt@dke01:~$ ls tmp/
k1.csv  k2.csv  k3.csv
physikerwelt@dke01:~$ docker cp tmp hyplag_database_isg03:/
```
### 2.3 Truncate tables and load the new data
```
moritz> truncate table k1
[2020-01-26 22:37:46] completed in 410 ms
moritz> truncate table k2
[2020-01-26 22:37:54] completed in 1 s 158 ms
moritz> truncate table k3
[2020-01-26 22:38:22] completed in 16 s 711 ms
```
Load new data
```
moritz> LOAD DATA INFILE '/tmp/k1.csv' into table k1
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 22:42:42] 22658 rows affected in 274 ms
moritz> LOAD DATA INFILE '/tmp/k2.csv' into table k2
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 22:43:03] 717526 rows affected in 9 s 34 ms
moritz> LOAD DATA INFILE '/tmp/k3.csv' into table k3
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-26 23:02:50] 32941436 rows affected in 19 m 35 s 23 ms
```
### 2.4 Determine table sizes (regular sets)
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
Table,Size (MB)
k1,3.38
k2,82.72
k3,3656.00
```

## 3 Third investiagtion using ordered sets to eliminate identical hash combinations

### 3.1 Load the data again (see above)
```
moritz> LOAD DATA INFILE '/tmp/k1.csv' into table k1
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-27 04:49:01] 22658 rows affected in 328 ms
moritz> LOAD DATA INFILE '/tmp/k2.csv' into table k2
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
[2020-01-27 04:49:19] 358763 rows affected in 3 s 988 ms
moritz> LOAD DATA INFILE '/tmp/k3.csv' into table k3
            FIELDS TERMINATED BY ','
            (docid,@var1)
        SET hash = UNHEX(@var1)
```
### 3.2 Determine table sizes (ordered sets)
```
SELECT table_name AS `Table`, round(((data_length + index_length) / 1024 / 1024), 2) `Size (MB)` FROM information_schema.TABLES WHERE table_schema = "moritz";
-->
Table,Size (MB)
k1,3.38
k2,42.64
k3,604.38
```
