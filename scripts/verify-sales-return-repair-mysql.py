from pathlib import Path
import subprocess, json, re, xml.etree.ElementTree as ET, time
import argparse
parser = argparse.ArgumentParser(description='Verify sales-return repair SQL using empty table copies and synthetic rows only; retains the isolated schema.')
parser.add_argument('--source-schema', required=True, help='Existing schema read only for CREATE TABLE LIKE')
parser.add_argument('--schema', required=True, help='New schema name, must start codex_sales_return_')
args = parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_]+', args.source_schema) or not re.fullmatch(r'codex_sales_return_[A-Za-z0-9_]+', args.schema):
    parser.error('Invalid schema name or missing isolated schema prefix')
w = Path(__file__).resolve().parents[1]
db = args.schema
def run(sql, schema=db):
 args=['mysql','-uroot','-N','-B']+([schema] if schema else [])
 r=subprocess.run(args,input=sql,text=True,capture_output=True)
 if r.returncode: raise RuntimeError(r.stderr)
 return r.stdout.strip()
run(f'CREATE DATABASE {db};',None)
for table in ['inv_sales_return','inv_sales_return_detail','inv_sales_detail','inv_stock_log']:
 run(f'CREATE TABLE {table} LIKE {args.source_schema}.{table};')
run("""INSERT INTO inv_sales_detail(detail_id,order_id,item_type,item_id,product_id,product_name,unit_price,delivered_quantity) VALUES
(1,900,'product',101,101,'Product',10,10),(2,900,'gift',101,NULL,'Gift',90,5),
(3,900,'product',202,202,'Repeated',20,5),(4,900,'product',202,202,'Repeated',30,5);
INSERT INTO inv_sales_return(return_id,return_no,sales_order_id,sales_order_no,return_title,customer_name,shop_dept_id,status) VALUES
(1,'R1',900,'SO900','Return1','Test',10,'submitted'),(2,'R2',900,'SO900','Return2','Test',10,'returned'),
(3,'R3',900,'SO900','Return3','Test',10,'submitted');
INSERT INTO inv_sales_return_detail(detail_id,return_id,product_id,product_name,quantity,unit_price,amount)
VALUES(1,1,101,'Product',2,10,20),(2,2,202,'Repeated',1,20,20);""")
migration=(w/'sql/erp_inventory_sales_return_item_20260909.sql').read_text()
run(migration)
assert run('SELECT COUNT(*) FROM inv_sales_return_detail WHERE returned_cost_amount IS NOT NULL') == '0'
assert run("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='inv_stock_log' AND COLUMN_NAME='cost_amount'") == 'YES'
assert run('SELECT CONCAT(item_type,":",item_id,":",sales_detail_id) FROM inv_sales_return_detail WHERE detail_id=1')=='product:101:1'
assert run('SELECT sales_detail_id IS NULL FROM inv_sales_return_detail WHERE detail_id=2')=='1'
before=run('SELECT * FROM inv_sales_return_detail ORDER BY detail_id');run(migration);assert before==run('SELECT * FROM inv_sales_return_detail ORDER BY detail_id')
run("INSERT INTO inv_sales_return_detail(detail_id,return_id,sales_detail_id,item_type,item_id,product_id,product_name,quantity,unit_price,amount) VALUES(3,3,2,'gift',101,NULL,'Gift',1,90,90)")
mapper=ET.parse(w/'erp-modules/erp-inventory/src/main/resources/mapper/inventory/InvSalesReturnDetailMapper.xml').getroot()
def query(stmt,params):
 e=mapper.find(f"select[@id='{stmt}']")
 sql=e.text or ''
 for child in e:
  assert child.tag=='if'
  if params.get('excludeReturnId') is not None: sql+=''.join(child.itertext())
  sql+=child.tail or ''
 def replace(m):
  v=params[m.group(1)];return str(v) if isinstance(v,int) else "'"+v.replace("'","''")+"'"
 return run(re.sub(r'#\{([^}]+)\}',replace,sql))
params=dict(salesOrderId=900,itemId=101,excludeReturnId=999)
assert query('sumHistoricalReturnQuantityByItem',dict(params,itemType='gift'))=='1.00'
assert query('sumHistoricalReturnQuantityByItem',dict(params,itemType='product'))=='2.00'
assert query('sumHistoricalReturnQuantityBySalesDetailId',dict(salesOrderId=900,salesDetailId=3,excludeReturnId=999))=='1.00'
assert query('sumHistoricalReturnQuantityBySalesDetailId',dict(salesOrderId=900,salesDetailId=4,excludeReturnId=999))=='1.00'
assert query('sumHistoricalReturnQuantityBySalesDetailId',dict(salesOrderId=900,salesDetailId=1,excludeReturnId=1))=='0.00'
races=[]
for first,second in [('returned','cancelled'),('cancelled','returned')]:
 run("UPDATE inv_sales_return SET status='submitted' WHERE return_id=3")
 firstsql=f"START TRANSACTION; SELECT status FROM inv_sales_return WHERE return_id=3 FOR UPDATE; SELECT SLEEP(0.3); UPDATE inv_sales_return SET status='{first}' WHERE return_id=3 AND status='submitted'; SELECT ROW_COUNT(); COMMIT;"
 p=subprocess.Popen(['mysql','-uroot','-N','-B',db],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
 p.stdin.write(firstsql);p.stdin.close();time.sleep(.1)
 secondout=run(f"START TRANSACTION; SELECT status FROM inv_sales_return WHERE return_id=3 FOR UPDATE; UPDATE inv_sales_return SET status='{second}' WHERE return_id=3 AND status='submitted'; SELECT ROW_COUNT(); COMMIT;")
 firstout=p.stdout.read();err=p.stderr.read();p.wait();assert p.returncode==0,err
 assert secondout.splitlines()==[first,'0'],secondout
 assert run('SELECT status FROM inv_sales_return WHERE return_id=3')==first
 races.append(dict(first=first,second=second,loserAffectedRows=0,terminal=first))
result=dict(database=db,server=run('SELECT VERSION()'),scope='isolated synthetic MySQL SQL verification; not Java service E2E or production',passed=['migration old NOT NULL schema','safe unique product backfill','ambiguous source remains unresolved','migration rerun unchanged','historical return cost stays unknown','nullable exact stock cost column','gift insert with product_id NULL','same numeric ID independent histories','unresolved legacy quantity conservative per-line occupancy','current return excluded'],races=races)
(w/'output/repair-20260909').mkdir(parents=True, exist_ok=True)
(w/'output/repair-20260909/sales-return-mysql.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False))
