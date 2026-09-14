#!/usr/bin/env python3
"""Offline conservative classification. Never connects to or writes a database.

Input: JSON lines exported by audit-contract-salary-sources.sql.
Output: deterministic review candidates, evidence IDs and current salary fingerprint.
Candidates are NOT approval or executable backfill commands. Source/amount corrections
require a fresh profile/source version check and reviewed evidence before any write.
"""
import argparse
import hashlib
import json
from collections import defaultdict
from decimal import Decimal, InvalidOperation
from datetime import datetime

FIELDS = ('baseSalary', 'postSalary', 'fieldAllowance', 'performanceSalary', 'salaryTotal')


def amounts(value):
    if isinstance(value, str):
        value = json.loads(value)
    result = []
    for field in FIELDS:
        number = Decimal(str((value or {}).get(field)))
        if not number.is_finite() or number < 0 or number > Decimal('99999999999999.99') or number != number.quantize(Decimal('.01')):
            raise ValueError('工资金额缺失、精度错误或负数')
        result.append(number)
    if sum(result[:4]) != result[4] or result[4] <= 0:
        raise ValueError('工资合计无效')
    return tuple(result)


def timestamp(value):
    if not value:
        raise ValueError('缺少实际确认时间')
    return datetime.fromisoformat(str(value))


def fingerprint(values):
    canonical = 'salary-v1|' + '|'.join(format(x.normalize(), 'f') if x else '0' for x in values) + '|'
    return hashlib.sha256(canonical.encode()).hexdigest()


def classify_employee(employee_id, records):
    out = {'employeeId': employee_id, 'classification': 'REVIEW_REQUIRED', 'reason': '', 'evidence': []}
    try:
        profiles = [r for r in records if r['kind'] == 'PROFILE']
        if len(profiles) != 1:
            raise ValueError('员工当前档案缺失或不唯一')
        current = amounts(profiles[0]['salary'])
        out['expectedProfileHash'] = fingerprint(current)
        audits = [r for r in records if r['kind'] == 'AUDIT']
        contracts = [r for r in records if r['kind'] == 'CONTRACT' and r.get('status') == 'SIGNED']
        actions = [r for r in records if r['kind'] == 'ACTION' and r.get('status') == 'CONFIRMED']
        if not audits:
            if len(contracts) == 1 and contracts[0].get('identityValid') == 1 and amounts(contracts[0]['salary']) == current:
                out.update(classification='HISTORICAL_CONTRACT_REVIEW', reason='唯一已签合同金额及身份匹配；需人工补证，不能伪造原入档人和时间', evidence=[{'kind':'CONTRACT','id':contracts[0]['id']}])
                return out
            raise ValueError('缺少入档审计或存在多份历史合同，需核对原始证据')
        if len(audits) != 1:
            raise ValueError('存在多次入档审计，不能按最大行号或相同金额推断唯一来源')
        root = audits[0]
        if root.get('evidenceValid') != 1 or not root.get('operatorId'):
            raise ValueError('导入审计与员工身份、批次、文件或经办人证据不完整')
        value = amounts(root['salary']); root_time = timestamp(root['at'])
        out['evidence'].append({'kind':'AUDIT','id':root['id']})
        seen = {value}; cursor = root_time
        later = sorted((r for r in actions if timestamp(r.get('at')) >= root_time), key=lambda r: timestamp(r['at']))
        effective = None
        for action in later:
            at = timestamp(action['at'])
            if at <= cursor:
                raise ValueError('确认时间相同，无法证明工资变更顺序')
            if amounts(action.get('before')) != value:
                raise ValueError('正式人事变更前值未衔接，工资来源链存在缺口')
            date = action.get('effectiveDate')
            if not date or (effective and date < effective):
                raise ValueError('正式变更适用日期缺失或存在追溯冲突')
            effective = date
            value = amounts(action.get('after')); seen.add(value); cursor = at
            out['evidence'].append({'kind':'ACTION','id':action['id']})
        if value != current:
            raise ValueError('最终来源金额与当前档案不一致')
        for contract in contracts:
            if contract.get('identityValid') != 1 or amounts(contract['salary']) not in seen:
                raise ValueError('已签合同身份或金额与可验证的工资链不一致')
        out.update(classification='CHAIN_CANDIDATE' if later else 'AUDIT_CANDIDATE', reason='仅可作为补来源关联候选；不改金额，入职适用日期仍须按原始资料核对')
    except (ValueError, InvalidOperation, TypeError, KeyError) as error:
        out['reason'] = str(error) or '证据字段无效'
    return out


def classify(records):
    groups = defaultdict(list)
    for record in records:
        if record.get('kind') not in ('PROFILE','AUDIT','ACTION','CONTRACT'):
            raise ValueError('未知证据类型')
        employee = record.get('employeeId')
        if not isinstance(employee, int) or isinstance(employee, bool) or employee <= 0:
            raise ValueError('无效员工编号')
        groups[employee].append(record)
    return [classify_employee(employee, groups[employee]) for employee in sorted(groups)]


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', help='restricted JSONL snapshot; output is written to stdout')
    args = parser.parse_args()
    with open(args.input, encoding='utf-8') as source:
        records = [json.loads(line, parse_float=Decimal) for line in source if line.strip()]
    for row in classify(records):
        print(json.dumps(row, ensure_ascii=False, sort_keys=True))
