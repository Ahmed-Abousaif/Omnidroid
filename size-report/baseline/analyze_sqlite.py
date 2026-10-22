import sqlite3
import os
import zlib
import shutil

db_path = 'omnidroid-metadata-libretro-db/src/main/assets/libretro-detect.sqlite'
orig_size = os.path.getsize(db_path)

with open(db_path, 'rb') as f:
    orig_bytes = f.read()
orig_deflated = len(zlib.compress(orig_bytes, 6))

print(f"Original SQLite file size: {orig_size:,} bytes")
print(f"Original deflated size:   {orig_deflated:,} bytes")

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Get tables
cursor.execute("SELECT type, name, sql FROM sqlite_master WHERE type IN ('table', 'index');")
schema = cursor.fetchall()
print("\n--- Tables and Indexes ---")
for typ, name, sql in schema:
    if typ == 'table':
        cursor.execute(f'SELECT COUNT(*) FROM "{name}"')
        cnt = cursor.fetchone()[0]
        print(f"TABLE {name}: {cnt:,} rows")
        print(f"  SQL: {sql}")
    else:
        print(f"INDEX {name}")
        print(f"  SQL: {sql}")

conn.close()

# Test VACUUM in copy
test_copy = 'size-report/baseline/test_vacuum.sqlite'
shutil.copyfile(db_path, test_copy)
v_conn = sqlite3.connect(test_copy)
v_conn.execute("VACUUM;")
v_conn.close()

vac_size = os.path.getsize(test_copy)
with open(test_copy, 'rb') as f:
    vac_bytes = f.read()
vac_deflated = len(zlib.compress(vac_bytes, 6))

print(f"\nPost-VACUUM SQLite file size: {vac_size:,} bytes (delta: {orig_size - vac_size:,} bytes)")
print(f"Post-VACUUM deflated size:   {vac_deflated:,} bytes (delta: {orig_deflated - vac_deflated:,} bytes)")
