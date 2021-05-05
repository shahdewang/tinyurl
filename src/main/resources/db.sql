create schema tinyurl;
create table tinyurl.url_mapping (
 code varchar(25),
 full_url text,
 primary key (code)
);
