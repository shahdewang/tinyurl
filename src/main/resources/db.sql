-- The line below is commented as it is not supported for H2 DB.
-- create database tinyurl;
create table url_mapping (code varchar(25), full_url varchar(2084), created_at timestamptz, primary key (code));
