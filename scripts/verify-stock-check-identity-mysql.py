#!/usr/bin/env python3
"""Validate stock-check identity migration/query on a newly created local MySQL fixture schema."""
import argparse
import json
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--source-schema', default='BossERP_NEW', help='Read-only table definitions to clone')
parser.add_argument('--mysql-user', default='root')
args = parser.parse_args()
if not args.source_schema.replace('_', '').isalnum():
    raise SystemExit('Invalid source schema')
root = Path(__file__).resolve().parents[1]
fixture = 'codex_stock_check_' + str(int(time.time()))
def sql(text, schema=None):
    command = ['mysql', '-u' + args.mysql_user, '-N', '-B']
    if schema:
        command += [schema]
    return subprocess.check_output(command, input=text.encode(), stderr=subprocess.STDOUT).decode()
sql(f'CREATE DATABASE `{fixture}` CHARACTER SET utf8mb4')
for table in ['inv_stock_check_detail', 'inv_stock', 'inv_product', 'inv_oe_item', 'inv_gift_box', 'inv_product_category']:
    sql(f'CREATE TABLE `{table}` LIKE `{args.source_schema}`.`{table}`', fixture)
sql("INSERT INTO inv_stock_check_detail(check_id,product_id,product_name,book_qty,actual_qty,diff_qty,cost_price) VALUES (100,101,'legacy',10,8,-2,2.50)", fixture)
before = sql('SELECT book_qty,actual_qty,diff_qty,cost_price FROM inv_stock_check_detail WHERE check_id=100', fixture)
migration = (root/'sql/erp_inventory_stock_check_item_identity_20260909.sql').read_text()
sql(migration, fixture)
sql(migration, fixture)
assert sql('SELECT book_qty,actual_qty,diff_qty,cost_price FROM inv_stock_check_detail WHERE check_id=100', fixture) == before
assert sql('SELECT item_type,item_id,product_id FROM inv_stock_check_detail WHERE check_id=100', fixture).strip() == 'product\t101\t101'
sql("""INSERT INTO inv_product(product_id,product_name,shop_dept_id,unit) VALUES(1,'Product fixture',20,'box');
INSERT INTO inv_oe_item(oe_item_id,oe_item_name,order_unit) VALUES(1,'OE fixture','piece');
INSERT INTO inv_gift_box(gift_id,gift_name,replenishment_unit) VALUES(1,'Gift fixture','set');
INSERT INTO inv_stock(item_type,item_id,product_id,shop_dept_id,warehouse_id,current_quantity,available_quantity,cost_price,total_cost)
VALUES('product',1,1,20,20,10,10,2,20),('oe',1,NULL,20,20,11,11,3,33),('gift',1,NULL,20,20,12,12,4,48);""", fixture)
xml = ET.parse(root/'erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvStockCheckDetailMapper.xml').getroot()
node = xml.find("select[@id='selectStockForCheck']")
query = node.text
for child in node:
    if child.tag == 'choose':
        query += child.find('when').text
    query += child.tail or ''
query = query.replace('#{shopDeptId}', '20').replace('#{warehouseId}', '20')
rows = sql(query, fixture).strip().splitlines()
identities = {tuple(row.split('\t')[:2]) for row in rows}
assert identities == {('product','1'),('oe','1'),('gift','1')}, rows
sql("""INSERT INTO inv_stock_check_detail(check_id,item_type,item_id,product_id,product_name,book_qty,actual_qty,recount_required,recount_qty,diff_qty)
VALUES(200,'product',1,1,'Product fixture',10,10,'0',NULL,0),
(200,'oe',1,NULL,'OE fixture',11,13,'1',13,2),
(200,'gift',1,NULL,'Gift fixture',12,10,'1',10,-2);""", fixture)
select_details = xml.find("sql[@id='selectInvStockCheckDetailVo']").text + ' WHERE d.check_id=200 ORDER BY d.detail_id'
detail_rows = sql(select_details, fixture).strip().splitlines()
assert {tuple(row.split('\t')[2:4]) for row in detail_rows} == identities
for row in detail_rows:
    cols = row.split('\t')
    if cols[2] != 'product':
        assert cols[4] == 'NULL', row
report = {'status':'passed', 'fixtureSchema':fixture, 'sourceSchemaReadOnly':args.source_schema,
          'checks':['migration twice','legacy quantities unchanged','legacy product identity backfill',
                    'full snapshot has product/OE/gift with same numeric ID',
                    'detail persistence and query retain generic identity and nullable product ID'],
          'fixtureRetained':True, 'businessDataModified':False}
output = root/'output/repair-20260909/stock-check-identity-mysql.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report,ensure_ascii=False))
