#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import time
from pathlib import Path


TABLES = {
    "inv_product_category": "category_id",
    "inv_supplier": "supplier_id",
    "inv_product": "product_id",
}


INSERT_RE = re.compile(
    r"^INSERT INTO (?P<table>inv_product_category|inv_supplier|inv_product) "
    r"\((?P<columns>.*?)\) VALUES \((?P<values>.*)\);$"
)


def quote_identifier(name: str) -> str:
    return "`" + name.strip(" `").replace("`", "``") + "`"


def convert_insert(line: str) -> str:
    match = INSERT_RE.match(line.strip())
    if not match:
        raise ValueError(f"not a supported insert line: {line[:120]}")
    table = match.group("table")
    columns = [item.strip().strip("`") for item in match.group("columns").split(",")]
    pk = TABLES[table]
    update_columns = [col for col in columns if col not in {pk, "create_time"}]
    assignments = [f"{quote_identifier(col)}=VALUES({quote_identifier(col)})" for col in update_columns]
    assignments.extend(["`update_by`='xlsx_reimport_upsert'", "`update_time`=NOW()"])
    return line.rstrip().rstrip(";") + "\nON DUPLICATE KEY UPDATE " + ", ".join(assignments) + ";"


def generate(source: Path, output: Path) -> None:
    lines = source.read_text(encoding="utf-8").splitlines()
    inserts = []
    counts = {table: 0 for table in TABLES}
    for line in lines:
        match = INSERT_RE.match(line.strip())
        if not match:
            continue
        table = match.group("table")
        counts[table] += 1
        inserts.append(convert_insert(line))

    stamp = time.strftime("%Y%m%d_%H%M%S")
    sql = [
        "-- Safe product/category/supplier upsert generated from work/reimport_products_categories_suppliers_20260626.sql",
        "-- This file intentionally does not delete inventory stock, logs, orders, or history tables.",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        f"CREATE TABLE IF NOT EXISTS `backup_inv_product_category_safe_upsert_{stamp}` LIKE `inv_product_category`;",
        f"INSERT INTO `backup_inv_product_category_safe_upsert_{stamp}` SELECT * FROM `inv_product_category`;",
        f"CREATE TABLE IF NOT EXISTS `backup_inv_supplier_safe_upsert_{stamp}` LIKE `inv_supplier`;",
        f"INSERT INTO `backup_inv_supplier_safe_upsert_{stamp}` SELECT * FROM `inv_supplier`;",
        f"CREATE TABLE IF NOT EXISTS `backup_inv_product_safe_upsert_{stamp}` LIKE `inv_product`;",
        f"INSERT INTO `backup_inv_product_safe_upsert_{stamp}` SELECT * FROM `inv_product`;",
        "",
        *inserts,
        "",
        "COMMIT;",
        "",
        "SELECT 'inv_product_category' AS table_name, COUNT(*) AS rows_count FROM inv_product_category",
        "UNION ALL SELECT 'inv_supplier', COUNT(*) FROM inv_supplier",
        "UNION ALL SELECT 'inv_product', COUNT(*) FROM inv_product",
        "UNION ALL SELECT 'inv_stock', COUNT(*) FROM inv_stock",
        "UNION ALL SELECT 'inv_stock_log', COUNT(*) FROM inv_stock_log;",
        "",
    ]
    output.write_text("\n".join(sql), encoding="utf-8")
    print("generated", output)
    for table, count in counts.items():
        print(f"{table}={count}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="work/reimport_products_categories_suppliers_20260626.sql")
    parser.add_argument("--output", default=f"work/safe_product_supplier_upsert_{time.strftime('%Y%m%d%H%M%S')}.sql")
    args = parser.parse_args()
    generate(Path(args.source), Path(args.output))


if __name__ == "__main__":
    main()
