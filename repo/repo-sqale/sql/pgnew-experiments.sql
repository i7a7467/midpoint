-- @formatter:off because of terribly unreliable IDEA reformat for SQL

-- various internal PG selects
SELECT version();
select * from pg_tables where tableowner='midpoint' order by tablename ;
select * from pg_tables where schemaname='public' order by tablename ;
select * from pg_trigger order by tgname;
select * from pg_available_extensions order by name;

-- DB clean: drop schema does it all with one command
-- drop schema public CASCADE;
-- drop table m_object;
-- DROP TRIGGER m_resource_oid_insert_tr ON m_resource;

-- DB data initialization (after pgnew-repo.sql)
-- one user with random name
INSERT INTO m_user (oid, name_norm, name_orig, version)
VALUES (gen_random_uuid(), md5(random()::TEXT), md5(random()::TEXT), 1);

select * from m_resource;
-- creates new row with generated UUID, repeated run must fail on unique name_norm
insert into m_resource (name_norm, name_orig, version) VALUES ('resource0', 'resource0', 1) RETURNING OID;
-- should fail the second time because oid is PK of the table (even with changed name_norm)
insert into m_resource (oid, name_norm, name_orig, version)
    VALUES ('66eb4861-867d-4a41-b6f0-41a3874bd48f', 'resource1', 'resource1', 1);
-- this should fail after previous due to cross-table m_object unique constraint
insert into m_user (oid, name_norm, name_orig, version)
    VALUES ('66eb4861-867d-4a41-b6f0-41a3874bd48f', 'conflict', 'conflict', 1);
-- must fail, update trigger does not allow OID changes
update m_object set oid='66eb4861-867d-4a41-b6f0-41a3874bd48e'
    where oid='66eb4861-867d-4a41-b6f0-41a3874bd48f';

SELECT * from m_object;
SELECT * from m_object_oid where oid not in (SELECT oid FROM m_object);

-- inner transaction should fail due to cross-table m_object unique constraint
delete from m_object where oid='66eb4861-867d-4a41-b6f0-41a3874bd48f';
-- switch Tx to manual in IDE to avoid autocommit
START TRANSACTION;
insert into m_resource (oid, name_norm, name_orig, version)
    VALUES ('66eb4861-867d-4a41-b6f0-41a3874bd48f', 'resource1', 'resource1', 1);

    START TRANSACTION;
    insert into m_user (oid, name_norm, name_orig, version)
        VALUES ('66eb4861-867d-4a41-b6f0-41a3874bd48f', 'conflict', 'conflict', 1);
    commit;
commit;

-- switch Tx back to Auto if desired - only resource1 should be inserted
select * from m_object where oid='66eb4861-867d-4a41-b6f0-41a3874bd48f';

-- Delete in two steps without trigger, much faster than normal.
SET session_replication_role = replica; -- disables triggers for the current session
-- HERE the delete you want, e.g.:
delete from m_user where name_norm > 'user-0001000000';

-- this is the cleanup of unused OIDs
DELETE FROM m_object_oid oo WHERE NOT EXISTS (SELECT * from m_object o WHERE o.oid = oo.oid);
SET session_replication_role = default; -- re-enables normal operation (triggers)
SHOW session_replication_role;

-- adding x users
-- 100_000 inserts: 3 inherited tables ~6s, for 25 inherited tables ~13s, for 50 ~20s, for 100 ~34s
-- change with volume (100 inherited tables): 200k previous rows ~34s, with 1m rows ~37s
-- with 3 inherited tables and 5M existing rows, adding 100k rows takes ~7s
-- delete from m_object;
-- delete from m_object_oid;
select count(*) from m_object_oid;
explain
select count(*) from m_user;
select * from m_user order by name_norm offset 990 limit 50;
-- vacuum full analyze; -- this requires exclusive lock on processed table and can be very slow, with 1M rows it takes 10s
vacuum analyze; -- this is normal operation version (can run in parallel, ~25s/25m rows)

-- 100k takes 6s, whether we commit after each 1000 or not
-- This answer also documents that LOOP is 2x slower than generate_series: https://stackoverflow.com/a/53242452/658826
DO $$ BEGIN
    FOR r IN 1001..2000 LOOP
        INSERT INTO m_user (
--             oid, -- normally generated automatically
            name_norm,
            name_orig,
            fullobject,
            version)
        VALUES (
--             gen_random_uuid(),
            'user-' || LPAD(r::text, 10, '0'),
            'user-' || LPAD(r::text, 10, '0'),
--             zero_bytea(100, 20000),
            random_bytea(100, 20000),
            1);

        -- regular commit to keep transactions reasonable (negligible performance impact)
        IF r % 1000 = 0 THEN
            COMMIT;
        END IF;
    END LOOP;
END; $$;

DO $$ BEGIN
    FOR r IN 1001..2000 LOOP
            INSERT INTO m_user (name_norm, name_orig, fullobject, version)
            VALUES ('user-' || LPAD(r::text, 10, '0'), 'user-' || LPAD(r::text, 10, '0'),
                    random_bytea(100, 20000), 1);
--                     zero_bytea(100, 20000), 1);
        END LOOP;
END; $$;

-- 100k takes 4s, gets slower with volume, of course
INSERT INTO m_user (name_norm, name_orig, version)
    SELECT 'user-' || LPAD(n::text, 10, '0'), 'user-' || LPAD(n::text, 10, '0'), 1
    FROM generate_series(38000001, 40000000) AS n;

-- MUST fail on OID constraint if existing OID is used in SET:
update m_object
set oid='66eb4861-867d-4a41-b6f0-41a3874bd48f'
where oid='f7a0362f-37a5-4dea-ac16-9c84dce333dc';

select * from m_user;

select * from m_object
where oid<'812c5e7a-8a94-4bd1-944d-389e7294b831'
order by oid;

-- EXPLAIN selects
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM m_object
where oid='cf72947b-f7b5-4b44-a2b1-07452b9056cc'
;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT count(*)
-- SELECT *
FROM m_focus
;

--------------
-- sandbox

select ctid, * from m_object
;

select count(*) from pg_inherits
;

-- JSON experiments

drop table jtest;

create TABLE jtest (
    id SERIAL PRIMARY KEY,
    text text,
    ext jsonb
);

delete from jtest;
insert into jtest (text,ext) values ('empty', '{}');
insert into jtest (text,ext) values ('string', '"just string"');
insert into jtest (text,ext) values ('array', '["first", "second", true, 4]');
insert into jtest (text,ext) values ('Richard Richter',
    '{"firstName": "Richard", "lastName": "Richter", "hobbies": ["photo", "video", "rowing"]}');
insert into jtest (text,ext) values ('Robin Richter',
    '{"firstName": "Robin", "lastName": "Richter", "hobbies": ["reading", "sleeping", "watching youtube"]}');
insert into jtest (text,ext) values ('Someone Else',
    '{"firstName": "Someone", "lastName": "Else", "hobbies": ["hardly", "any"]}');

-- see also https://www.postgresql.org/docs/13/functions-json.html some stuff is only for JSONB
select * from jtest
    where ext->>'hobbies' is not null;
select count(*) from jtest where ext->'hobbies' is not null; -- the one below does the same
select count(*) from jtest where ext ? 'hobbies'; -- ? works with JSONB, but not JSON
select ext->>'hobbies' from jtest where ext ? 'hobbies'; -- return values of ext as text (or number)
select * from jtest where ext->'hobbies' @> '"video"'; -- contains in [] or as value directly
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
select *
-- select count(*)
from jtest where ext->'hobbies' @> '"video"'
and id >= 1307213
order by id
limit 500
;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
-- select count(*)
select *
from jtest
where
--     text = 'hobby-problem' and
    -- without array test jsonb_array_elements_text function can fail on scalar value (if allowed)
    exists (select from jsonb_array_elements_text(ext->'hobbies') v
        where jsonb_typeof(ext->'hobbies') = 'array'
        and upper(v::text) LIKE '%ING')
-- order by id
;

insert into jtest (text, ext) values ('hobby-problem', '{"hobbies":"just-one"}');

set jit=on;

-- TODO: jsonb_path_ops
DROP INDEX jtest_ext_gin_idx;
CREATE INDEX jtest_ext_gin_idx ON jtest USING gin (ext);

/*
60ms @ 500k
Gather  (cost=1000.00..9841.05 rows=5000 width=55)
  Workers Planned: 2
  ->  Parallel Seq Scan on jtest  (cost=0.00..8341.05 rows=2083 width=55)
"        Filter: ((ext -> 'hobbies'::text) @> '""video""'::jsonb)"

70ms @ 500k (but not parallel)
Bitmap Heap Scan on jtest  (cost=66.75..5499.17 rows=5000 width=55)
"  Recheck Cond: ((ext -> 'hobbies'::text) @> '""video""'::jsonb)"
  ->  Bitmap Index Scan on jtest_ext_gin_idx2  (cost=0.00..65.50 rows=5000 width=0)
"        Index Cond: ((ext -> 'hobbies'::text) @> '""video""'::jsonb)"
 */

select count(*) from jtest;
DO
$$ BEGIN
    FOR r IN 8000001..12000000 LOOP
            IF r % 10 = 0 THEN
                INSERT INTO jtest (text, ext)
                VALUES ('json-' || LPAD(r::text, 10, '0'), ('{"likes": ' ||
                    array_to_json(random_pick(ARRAY['eating', 'books', 'music', 'dancing', 'walking', 'jokes', 'video', 'photo'], 0.4))::text || '}')::jsonb);
            ELSE
                INSERT INTO jtest (text, ext)
                VALUES ('json-' || LPAD(r::text, 10, '0'), ('{"fanOf": ' ||
                    array_to_json(random_pick(ARRAY['eating', 'books', 'music', 'dancing', 'walking', 'jokes', 'video', 'photo'], 0.3))::text || '}')::jsonb);
            END IF;
        END LOOP;
END $$;

SELECT * from jtest
where text > 'json-0000020000';

-- MANAGEMENT queries

-- See: https://wiki.postgresql.org/wiki/Disk_Usage

-- top 20 biggest tables or their TOAST (large object storage) from public schema
SELECT
    t.oid,
    CASE
        WHEN tft.relname IS NOT NULL
            THEN tft.relname || ' (TOAST)'
        ELSE t.relname
    END AS object,
    pg_size_pretty(pg_relation_size(t.oid)) AS size
FROM pg_class t
    INNER JOIN pg_namespace ns ON ns.oid = t.relnamespace
    -- table for toast
    LEFT JOIN pg_class tft ON tft.reltoastrelid = t.oid
    LEFT JOIN pg_namespace tftns ON tftns.oid = tft.relnamespace
WHERE 'public' IN (ns.nspname, tftns.nspname)
ORDER BY pg_relation_size(t.oid) DESC
LIMIT 20;

vacuum full analyze;
-- database size
SELECT pg_size_pretty( pg_database_size('midpoint') );

-- show tables + their toast tables ordered from the largest toast table
-- ut = user table, tt = toast table
select ut.oid, ut.relname, ut.relkind, tt.relkind, tt.relname, tt.relpages, tt.reltuples
from pg_class ut
    inner join pg_class tt on ut.reltoastrelid = tt.oid
    inner join pg_namespace ns ON ut.relnamespace = ns.oid
where ut.relkind = 'r' and tt.relkind = 't'
    and ns.nspname = 'public'
order by relpages desc;

-- PRACTICAL UTILITY FUNCTIONS

-- based on https://dba.stackexchange.com/a/22571
CREATE OR REPLACE FUNCTION random_bytea(min_len integer, max_len integer)
    RETURNS bytea
    LANGUAGE sql
    -- VOLATILE - default behavior, can't be optimized, other options are IMMUTABLE or STABLE
AS $$
    SELECT decode(string_agg(lpad(to_hex(width_bucket(random(), 0, 1, 256) - 1), 2, '0'), ''), 'hex')
    -- width_bucket starts with 1, we counter it with series from 2; +1 is there to includes upper bound too
    -- should be marginally more efficient than: generate_series(1, $1 + trunc(random() * ($2 - $1 + 1))::integer)
    FROM generate_series(2, $1 + width_bucket(random(), 0, 1, $2 - $1 + 1));
$$;

CREATE OR REPLACE FUNCTION zero_bytea(min_len integer, max_len integer)
    RETURNS bytea
    LANGUAGE sql
AS $$
    SELECT decode(string_agg('00', ''), 'hex')
    FROM generate_series(2, $1 + width_bucket(random(), 0, 1, $2 - $1 + 1));
$$;

-- should return 10 and 20, just to check the ranges
select min(length(i)), max(length(i))
from (select random_bytea(10, 20) as i from generate_series(1, 200)) q;

-- returns random element from array (NULL for empty arrays)
CREATE OR REPLACE FUNCTION random_pick(vals ANYARRAY)
    RETURNS ANYELEMENT
    LANGUAGE plpgsql
AS $$ BEGIN
    -- array_lower is used if array subscript doesn't start with 1 (which is default)
    RETURN vals[array_lower(vals, 1) - 1 + width_bucket(random(), 0, 1, array_length(vals, 1))];
END $$;

-- returns random elements from array
-- output is of random length based on ratio (0-1), 0 returns nothing, 1 everything
CREATE OR REPLACE FUNCTION random_pick(vals ANYARRAY, ratio numeric, ignore ANYELEMENT = NULL)
    RETURNS ANYARRAY
    LANGUAGE plpgsql
AS $$
DECLARE
    rval vals%TYPE := '{}';
    val ignore%TYPE;
BEGIN
    IF vals IS NULL THEN
        RETURN NULL;
    END IF;

    -- Alternative FOR i IN array_lower(vals, 1) .. array_upper(vals, 1) LOOP does not need "ignore" parameter.
    -- Functions array_lower/upper are better if array subscript doesn't start with 1 (which is default).
    FOREACH val IN ARRAY vals LOOP
        IF random() < ratio THEN
            rval := array_append(rval, val); -- alt. vals[i]
        END IF;
    END LOOP;
    -- It's also possible to iterate without index with , but requires
    -- more
    RETURN rval;
END $$;