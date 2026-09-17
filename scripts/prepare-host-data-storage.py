#!/usr/bin/env python3
"""Prepare reviewed storage configuration in a private staging directory; never stop live services.

Run on the target host after the native MySQL/Redis backup/restore rehearsal.
All live configuration and data are left untouched. The manifest identifies the
shared-service maintenance boundary and the required cold-copy/rollback steps.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

SERVICES = ('gateway', 'auth', 'system', 'oa', 'inventory', 'file', 'approval', 'job', 'monitor')
ROOTS = ('/data/erp-new/releases', '/data/erp-new/packages', '/data/erp-new/backups',
         '/data/erp-new-data/logs', '/data/erp-new-data/tmp', '/data/erp-new-data/cache',
         '/data/erp-new-data/mysql', '/data/erp-new-data/redis')


def mysql_config(source):
    """Keep connection/socket compatibility; legacy data path will become a symlink."""
    lines = source.splitlines()
    out, section, inserted = [], '', False
    for line in lines:
        if line.strip().startswith('['):
            if section == 'mysqld' and not inserted:
                out.append('tmpdir=/data/erp-new-data/tmp/mysql'); inserted = True
            section = line.strip()[1:-1].lower()
        if section == 'mysqld' and re.match(r'^\s*tmpdir\s*=', line):
            if not inserted: out.append('tmpdir=/data/erp-new-data/tmp/mysql'); inserted = True
        else: out.append(line)
    if section == 'mysqld' and not inserted:
        out.append('tmpdir=/data/erp-new-data/tmp/mysql'); inserted = True
    if not inserted: raise ValueError('missing mysqld section')
    return '\n'.join(out) + '\n'


def redis_config(source):
    replacements = {'dir': '/data/erp-new-data/redis', 'logfile': '/data/erp-new-data/logs/redis/redis.log',
                    'pidfile': '/data/erp-new-data/redis/redis.pid'}
    out, seen = [], set()
    for line in source.splitlines():
        match = re.match(r'^\s*(dir|logfile|pidfile)\s+', line)
        if match:
            key = match.group(1)
            if key in seen: continue
            out.append(key + ' ' + replacements[key]); seen.add(key)
        else: out.append(line)
    out.extend(key + ' ' + value for key, value in replacements.items() if key not in seen)
    return '\n'.join(out) + '\n'


def unit_guards(paths, log=None):
    for path in paths:
        if not re.fullmatch(r'/data/[A-Za-z0-9_./-]+', path): raise ValueError('unsafe storage path')
    lines = ['[Unit]', 'RequiresMountsFor=' + ' '.join(paths), '[Service]']
    for path in paths:
        lines += ['ExecStartPre=/usr/bin/test -d ' + path, 'ExecStartPre=/usr/bin/test -w ' + path,
                  'ExecStartPre=/bin/sh -c \'test "$(findmnt -n -o TARGET -T ' + path + ')" = /data\'']
    if log: lines += ['StandardOutput=append:' + log, 'StandardError=append:' + log]
    return '\n'.join(lines) + '\n'


def prepare(destination):
    destination = Path(destination)
    if not str(destination).startswith('/data/maintenance/'):
        raise ValueError('staging must be under /data/maintenance')
    mount = subprocess.check_output(['findmnt', '-n', '-o', 'TARGET', '-T', str(destination.parent)], universal_newlines=True).strip()
    if mount != '/data': raise ValueError('data mount is unavailable')
    if destination.exists(): raise ValueError('refusing to overwrite prior candidate')
    destination.mkdir(mode=0o700)
    parent = destination.parent
    for filename in ['rehearsal.json', 'redis-rehearsal.json']:
        evidence = json.loads((parent / filename).read_text())
        if evidence.get('status') != 'passed' or not evidence.get('isolatedShutdown'):
            raise ValueError('native restore rehearsal is incomplete: ' + filename)
    sources = {'my.cnf': Path('/etc/my.cnf'), 'redis.conf': Path('/www/server/redis/redis.conf')}
    manifest = {'status': 'prepared_only', 'liveFilesModified': False, 'sourceSha256': {}, 'requiredDirectories': list(ROOTS),
                'sharedServices': ['mysqld.service', 'zyx-redis.service', 'zyx-invoice.service', 'zyx-legacy-admin.service'],
                'activeErpServices': [], 'configCandidates': ['my.cnf', 'redis.conf']}
    for name, source in sources.items():
        content = source.read_text()
        manifest['sourceSha256'][str(source)] = hashlib.sha256(source.read_bytes()).hexdigest()
        result = mysql_config(content) if name == 'my.cnf' else redis_config(content)
        (destination / name).write_text(result); (destination / name).chmod(0o600)
    for service in SERVICES:
        unit = 'erp-new@' + service + '.service'
        active = subprocess.run(['systemctl', 'is-active', '--quiet', unit]).returncode == 0
        if active: manifest['activeErpServices'].append(unit)
        paths = ['/data/erp-new-data/uploadPath', '/data/erp-new/releases',
                 '/data/erp-new-data/logs', '/data/erp-new-data/tmp', '/data/erp-new-data/cache']
        (destination / (unit + '.conf')).write_text(unit_guards(paths))
        for kind in ('logs', 'tmp', 'cache'):
            manifest['requiredDirectories'].append('/data/erp-new-data/' + kind + '/' + service)
    manifest['requiredDirectories'] += ['/data/erp-new-data/tmp/' + n for n in ('mysql','attendance','reimbursement')]
    manifest['requiredDirectories'].append('/data/erp-new-data/logs/redis')
    (destination / 'mysqld.service.conf').write_text(unit_guards(['/data/erp-new-data/mysql', '/data/erp-new-data/tmp/mysql']))
    (destination / 'zyx-redis.service.conf').write_text(unit_guards(['/data/erp-new-data/redis', '/data/erp-new-data/logs/redis']))
    (destination / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(json.dumps({'status': 'prepared_only', 'candidate': str(destination), 'liveFilesModified': False}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True)
    prepare(parser.parse_args().output)
