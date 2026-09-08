#!/usr/bin/env node
"use strict"

const ERP_QA_PASSWORD = process.env.ERP_QA_PASSWORD
if (ERP_QA_PASSWORD === undefined || ERP_QA_PASSWORD === "") {
  console.error("ERP_QA_PASSWORD is required")
  process.exit(2)
}

console.error("旧审批引擎移动端回归脚本已退役，请使用统一审批发布检查。")
process.exit(2)
