import importlib.util
from pathlib import Path
import unittest
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('storage', ROOT/'scripts/prepare-host-data-storage.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)

class StorageTests(unittest.TestCase):
    def test_mysql_preserves_other_options_and_changes_only_server_temp(self):
        source='[client]\nsocket=/tmp/mysql.sock\n[mysqld]\ndatadir=/www/server/data\npassword=unchanged\ntmpdir=/tmp\n[mysqldump]\nquick\n'
        result=module.mysql_config(source)
        self.assertIn('socket=/tmp/mysql.sock', result)
        self.assertIn('datadir=/www/server/data', result)
        self.assertIn('password=unchanged', result)
        self.assertEqual(1,result.count('tmpdir=/data/erp-new-data/tmp/mysql'))
        self.assertEqual(result,module.mysql_config(result))
    def test_redis_preserves_auth_and_persistence_and_removes_duplicate_paths(self):
        source='requirepass "keep secret"\ndir /old\ndir /other\nappendonly no\ndbfilename dump.rdb\n'
        result=module.redis_config(source)
        self.assertIn('requirepass "keep secret"',result)
        self.assertIn('appendonly no',result)
        self.assertEqual(1,result.count('dir /data/erp-new-data/redis'))
        self.assertEqual(result,module.redis_config(result))
    def test_reject_missing_mysql_section_and_unsafe_unit_paths(self):
        with self.assertRaises(ValueError):module.mysql_config('[client]\nx=1')
        with self.assertRaises(ValueError):module.unit_guards(['/data/x;touch /tmp/y'])
        with self.assertRaises(ValueError):module.prepare('/tmp/not-production')
    def test_every_guard_checks_the_effective_mount(self):
        out=module.unit_guards(['/data/erp-new-data/mysql'])
        self.assertIn('RequiresMountsFor=/data/erp-new-data/mysql',out)
        self.assertIn('findmnt -n -o TARGET -T /data/erp-new-data/mysql',out)
        self.assertIn('test -w /data/erp-new-data/mysql',out)
    def test_real_host_runner_rejects_system_disk_before_launch(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            runner=root/'run.sh';runner.write_text((ROOT/'docker/run-erp-service.sh').read_text());runner.chmod(0o700)
            (root/'.env').write_text('SPRING_PROFILE=local\n')
            result=subprocess.run([str(runner),'gateway'],capture_output=True,text=True)
            self.assertEqual(73,result.returncode)
            self.assertIn('required project path must be writable on the /data mount',result.stderr)

if __name__=='__main__':unittest.main()
