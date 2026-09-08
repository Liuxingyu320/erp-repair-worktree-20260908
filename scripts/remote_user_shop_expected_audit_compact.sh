#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'

import base64
import json
import subprocess
import zlib

payload = "eNrVXVtPI8u1/ivRfo5Ouqq6bo85l5fzkNdoayuKoihKIiX7HGm/7MdhGDDmYgzDAMYw5n4bMDeDwcbmz7iqu/9FVnUZj8Wecbdxd3si9WiqjU19XrVqrW9dqvnhh+/+/2//9+Nfvvv1d4g4DpZISObCXTC5pMuP6qRuxrklf+7Km7gNKrdBeVqVd3/1m1/1vwi3an6t0zhT8w1VqcCtf/Ckqnl76z8V/eIB/J7v4Z9aWlf1g//5+c9/+cd3f/h1ZrN/frFY1m8P4df+bjAYiilGiFG40x/XvKMlv3rxNTB661zVP6rigrdyacCEg879GwBgoC5X9Nq2qtaiJUAlc6hABO7829tO60atvospAX1b0leVngTsbQ9DF2Gj7heuVLs+diRebddfvPbyD5ELgVxGmWDCIAlOzvTa+etUov+2pxL2RV1ZtCoxWCaIIsaQENKoRGvd37qLK5Crij+3+FkgfbeflRNeAbHMVdXVFYDRhx9U4914IcFHdKka7DxFrhHGyEEOdsLNslVQx7mRVKUxb1VFVefi7Jr4s395nz5PR7zmu3BWsBH5+AqKiUMYx4zDnde80fWc3pwZl45iAlvX5dLYUP+yrkvH/vxmTDCd+1l9W++B6b+NacFTnb33YrD+5DVu/KfT6KVhGAMagcy73k6py8XOfStJe/rsSyLEIhEmSCBs3tUu6qu8Xn2ljqKujj6CP7nT9xed+5x/dRoDAUESIymsOQ+FeJOgjhoY7TZ8K7VQiAHGRS6YLmn3q16fBweb4KrE3SqSOsSB/83UlRO9e+gt3WWvHC6SkjNYHWM+Kjd6/cCf3UrIfNbMTxfWxoDhPt/FsH+qH1Z17TooTUVuVyBcWHCOHPOu81KwVfJnx6alYMeohG3jhu867jQ2vML1KN7Vrsu45jUyWQhlUs2DEDrND5HLwYgkFMhFiAQMzuFCll49pdn7b/+zh6bzMGc+01iLFgqV4FY4Nzpqp/waLH9nXq88BZPHwdpx5LflVAgHSC4NDfSqLtXgSuYLg7pP1YKpZXAY0TA4RRhRG4KdnIHyvc5hZjbRLy1OsPcxaJeARVv+/LsIGMwxa2rE3mlfqKlJuDKjS6nO3nvRWwJXlB+vKMxHGstBoxLsF1VhKgYY7kpDm8wua62pp0n9ZieRjSaR4JITwSwTUrPboC1paHhSEw02Yf9t7neKqlHsj+0HC1e6DnekoCErnlm3mpoA66lfqc0bvdlQcOUuYnAf6iBk+LlLuzZ+71K3qkksM3UwlUxSEdrTy13gEfGpRPxwHWyWfrOlFnbU5YqNk6NQcceBr/uMKn8zqtUbzqmmCODLfjVYvornV6lDKWWwYbgNCoLylHpzknlQQGFnUBcJZvZG8Pa9PtgIyqdDpv2eqe/FPARZnxnp5WKwtQx+KQYG6gKKMD6CcNErP8KVuO7GFkgWYH65kSK0RQIs4kinm/JpNjqPla+h6jTfq8Z7VSyouX1rPuBFL3+iV0tBLufXzr32fqQcEGaMMC64peX68t6LndoYTFi6P91fVeXaGGF0HuaDD7lg/cmsxVwkX6AYE4aFg0MvMp3Xpff6w2Iq/s0/2tMrbW93KaZXwdhlmIjQq+jdCQ2+8WgtK1KX7uxGOrVrP3cTvTyMu5QzbtLmXgFsMcR+G6Mbsuivz12XcKsWnccF7wim3kjKXox39lAyNe9632+dRMqfSMowpxzb+hGo7hCe9vXpg7TmNd/9/n6Ywg2ljgvRBHJFaKOPwY92WjeZbcNUZzcfWTkE0h0tBIhxQR/DxCtw7c5DcVyOnCJwGy5DzLpMb+kufroz6eQaNVYSSwg4rXXy6xde8TWZvswm+tq3NrmLx41oNRAUokEQv4XhNXb17lQqzjLILajGtOVTEPlHCygzZL9044NlZmpqhAnHKuymrs0lwsO9fFnt14LD6UjRMMGRQMjFXdFcPercU+Y5uTRh9N3+9gUstbChNi/jRG4cEckFwcwG8f77LfU0OY6mBcqJAM/HQ+/v3976+ca4jC0gkQycTxg11Q9VbgeuFLJMiU00WDn+q7+a5N/NeitnQW4+UjMEcpBwEAlz5dOzqnUC5mVcLkeYFgFJpU0wbKr7qaHixWCvot5PeFfNTvNALc56+xAHL9vwMXpqShwkOcOWBOjdwyFCpdSSUYIJxIgbFppMTbjWGFBjSSnVIYEacUNYDV9vNtVhNatusxSnDnOhVZ0/Q9KVkZvElPo4QUh0UxmL84n3jvQqAN+PHUns6EEaw0aZtJnKOpB3b7k4pq4aKg1HcmVIRdTSsb9cgivzVgHGKHdcKZxwgaq3XvMIfGMS6XLGuItdMJBhsbVYUydHI7Wn6PVpU4Bb3dRbB16pGTU9R67jUinEs+bdzwebH8fdXJc6Kj1TDCZbNm0/cDNwgh2GJHHDrF91LVj6pOuPSUYxbzaMfDZyano7UiyEOJJzaftmJs9Vax+uxHdmbNbHiSuIIDx07EHznb4tBJurmVcOODgU5Dg8jDSt9YxvQBPq40kRw+hskDNCIIyhVm9Kd537B7iy7ILjjAIdZQiH5cb6yXC1jMTMOOcOxCkQqHSLF0DagvJxGjmJCBgUuJ/rMBIalTk9OeVfrL2uw53a9QAqpBun8c0+h71rSEcXgXewOo72TC6QENgRBNmiqN5YhWukFmK9eA58OKZWCsoodgnvNnzps70hWn8SDlxTB6MuHzvtLb396LUiMwtcCI4dIpmwXSADzEVWQVMWkFTuQlUhqr1QlUa0iCSTWDJbr548H1PfWJowBuWoHm5M6BWdoxKOAzE4GLtQrRd2IUAd0E+UGkcQDnEhvmKh9oDzAuV79QmnJKhTRniGLO4DKlgrwmwFdesYRO21C9kyqRQxfJVJecW3qr4VLR2BXYkZlzZvAYGxX5zPvBEhRRggCn27AtFWTHUJT/9wJ2y0sxMlERILTAilpkvVaoCeOlKn5THuVcwdik1t25YBNJDX2PW7kcup6c4+/MkegQV1TANQmJG5vvFyHzLnjQJL1xRVMe71GIKSjIm4CYJhfQjIpduOM3U04IxRlhVGQQhnAM2y/PMSwEqs53AIipImjEQoCiFSEOqGUYhJwe2feO/uXheOud0oJHzT//4+XhwiCLdHsLsNAglwuFfW8QCJ4C6m3UT0sVpeBDY8ph5lk1kSYPtceyr9HGLkAQXy14TJfr5tuUUEDsEJ5RCdGWuzO6EWcl7xMWNWlB6Gr+eXjt56b/PRu0c6WCAnLBcEpRl1tKdmTrItZgkXAkMHifCRAcbCAGMolkfLI+TPfhkQRoDgjLmIh6UKEATYaD1xkmVWBxyzgyhB2NYVP3UaG/78Q6qVpAg8hEpOsY39ZtbHWoUWlEvsMBxm8mEH+bPV+BFXguw9PRjg8PTDKsjEBtiDdy0VLgW3HHYvGGW9rSdM5GK7HOZwJCVyuvbDn8v+QLIw/YqSI9tR/bitWldwjankKRiTlHFq+/5n1vV6RVVziQRWwJhd/twgXTv1Ps1lFsWkN3XvRX9mAXhlUI484yaYBKVzMO123sGn/EIhS8LHOQ8fcoO7HaG5DwN2X7yCTNgiDLqir0322xLswSAEZYi5hIpud1u7kOxBrf4iawQSRoBCSCyeTyAdD91y+CwO3BVHePg9vuc0J9ZMyz7tPs1lZjnYKmUfKaUII4mGQyElqAwVYedOULozhc7Tcra9ocDzMBgSJ5SRUdujuVFPv7zGj6cIA6yZX/poLYlZoOh1kQ6wCuK43RNBF0Ccx3GETzqSuQCFdU++6JvdTE5epDWvNavedCVe27REjDPsSNsOsX7jFfIQDI0peJYmEAEvJ0nXxcxcqanLkXycjcUsxYzhXwCBpIIg4n5G8HWmG8uq67XtYGXHnP8on0dPLySRnJAwqbJ5BQZP15cyT1WmD6PcUvtH8R7zIpEkAvAQ3s2sX5czPAKd1uwjnn8GWAK5kobGM2gW/MKnbHM7aQEYsdwlsWlIdR0njFQn3nYeK/HPcY1SohwsLEObXcR7bRJvtuB6nV1jXbu2fKV21m0LnprI6bWDaBAS7JrjopCnnR+qi+U04sV4zcMAhoL6OPbc4faarlyMZOaHyXlJgl0BvCN0eSbD8zSZbEPVUIIgENAQTMOj6YYGvNmKr7CJGLj0AIz47CSDjCPhhIchjReqnQ6dSn8ZcNpHWvXVigfLhhKOqHA46z5MdKboHb3J6shBurMP+YBbSamDuHnuhing3y95xxuj5vJfmXuT5miqwyTvHiDTu4fxE8ajEPaU5u1VboN2yeyN3RhrAdRMCFdg63uHSlEnF/inCWNQ4F+dgFgpjv2g3EXmsXDInicDCqvP9kaqbajcTqexpDdP1X6192iqwUISxOUuwbgbcgOCNJxNzFhXUOqaHMBzbeO0HL9JZeR0arqzD98UAngEYhCBPz9Y9N3iAP1IiUanh2HEEqmEnR2GgRbZ8agPO32VhUkLQyLmxYTILsLO83mfg9YQ/TzJ5fSGgjGYJfUa8zrNPa+602uu6xUIIgTiuhhJFj4EPmg09dkuXGMQSGow1Hperb6LajX8599/+unvP/71jz/+6Z8GkL6eUc07uyb94+RbDfTats7PmYTbYt2C0usFrzn9R1X85Ncqfm2v02j8x8//+OnnL+HsNAqmySl84kj/+FvDaQ5UTrVt1ad/nHw6fzScvWTR4KzRGBHaFm57SLF//M3hPF/x59dscaB//K3hNPSuvuTX9l+MvzWcpuGpuNStz/ePk60kj7joQBDX895B48U4hZTZaED9yR2/3e4ffNnRfHjS1yXQXb962YfJvjp7702XkrKO3vZK/2CcaPzFKXVe9Ar5F+Okgh+/9gCu2B6uT8BiV29BzfTW4otxKgctR3eCt+/8ydaLcSp1olH9YMH8OaDqxIvxNwhVLd4FlcMutegbp3FIJQG0rXN7nrA3SLwdLwFW2fqgnm77BykedxjRLe4YJhm8P385zuAE6YhCbl+YP0eyPGn/ktCL25QeD//vAvsLD5If2eF709te+OiL/vE3K+e0AY8o4T/8Cyun9sM="
rows = json.loads(zlib.decompress(base64.b64decode(payload)).decode("utf-8"))


def q(value):
    value = "" if value is None else str(value)
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


values = ",\n".join("(" + ", ".join(q(item) for item in row) + ")" for row in rows)
sql = f"""
CREATE TEMPORARY TABLE expected_user_shop_scope (
  match_type varchar(32) not null,
  match_key varchar(128) not null,
  expected_name varchar(128) not null,
  expected_full_path varchar(1000) not null,
  expected_default char(1) not null,
  source_name varchar(128) not null
);
INSERT INTO expected_user_shop_scope(match_type, match_key, expected_name, expected_full_path, expected_default, source_name) VALUES
{values};
CREATE TEMPORARY TABLE expected_user_shop_scope_lookup AS
SELECT * FROM expected_user_shop_scope;

CREATE TEMPORARY TABLE active_dept_paths AS
WITH RECURSIVE dept_tree AS (
    SELECT dept_id, parent_id, dept_name, CAST(dept_name AS CHAR(1000)) AS full_path
    FROM sys_dept
    WHERE dept_id = 100 AND del_flag = '0'
    UNION ALL
    SELECT d.dept_id, d.parent_id, d.dept_name, CONCAT(t.full_path, ' / ', d.dept_name) AS full_path
    FROM sys_dept d
    JOIN dept_tree t ON t.dept_id = d.parent_id
    WHERE d.del_flag = '0'
)
SELECT dept_id, full_path
FROM dept_tree;

CREATE TEMPORARY TABLE actual_user_shop_scope AS
SELECT
    CASE WHEN u.create_by = 'missing_phone_reseed' THEN 'missing_name' ELSE 'phone' END AS match_type,
    CASE WHEN u.create_by = 'missing_phone_reseed' THEN u.nick_name ELSE COALESCE(NULLIF(u.phonenumber, ''), u.user_name) END AS match_key,
    u.user_id,
    u.nick_name,
    p.full_path,
    us.is_default,
    u.create_by
FROM sys_user u
JOIN sys_user_shop us ON us.user_id = u.user_id
JOIN active_dept_paths p ON p.dept_id = us.dept_id
WHERE u.del_flag = '0'
  AND u.create_by IN ('employee_xls_import', 'missing_phone_reseed');

select 'shop_scope_counts' as section;
select count(*) as expected_rows,
       sum(source_name='员工Excel') as expected_employee_rows,
       sum(source_name='无手机号员工明细_含负责人.xlsx') as expected_missing_phone_rows
from expected_user_shop_scope;

select 'actual_counts' as section;
select count(*) as actual_rows,
       sum(create_by='employee_xls_import') as actual_employee_rows,
       sum(create_by='missing_phone_reseed') as actual_missing_phone_rows
from actual_user_shop_scope;

select 'missing_expected_shop_scope' as section;
select e.source_name, e.match_type, e.match_key, e.expected_name, e.expected_full_path, e.expected_default
from expected_user_shop_scope e
where not exists (
    select 1
    from actual_user_shop_scope a
    where a.match_type = e.match_type
      and a.match_key = e.match_key
      and a.full_path = e.expected_full_path
      and a.is_default = e.expected_default
)
order by e.source_name, e.expected_name, e.expected_full_path;

select 'extra_actual_shop_scope' as section;
select a.create_by, a.match_type, a.match_key, a.user_id, a.nick_name, a.full_path, a.is_default
from actual_user_shop_scope a
where not exists (
    select 1
    from expected_user_shop_scope_lookup e
    where e.match_type = a.match_type
      and e.match_key = a.match_key
      and e.expected_full_path = a.full_path
      and e.expected_default = a.is_default
)
order by a.create_by, a.nick_name, a.full_path;

select 'default_count_errors' as section;
select a.create_by, a.match_type, a.match_key, max(a.nick_name) as nick_name,
       count(*) as shop_count,
       sum(a.is_default='Y') as default_count,
       group_concat(concat(a.full_path, ':', a.is_default) order by a.is_default desc, a.full_path separator ' | ') as shops
from actual_user_shop_scope a
group by a.create_by, a.match_type, a.match_key
having sum(a.is_default='Y') <> 1;

select 'invalid_active_user_shop_depts' as section;
select count(*) as invalid_count
from sys_user_shop us
join sys_user u on u.user_id = us.user_id and u.del_flag = '0'
left join sys_dept d on d.dept_id = us.dept_id and d.del_flag = '0'
where d.dept_id is null;
"""
password = open("/root/.erp-mysql-root-pass").read().strip()
cmd = ["mysql", "--protocol=tcp", "-uroot", f"-p{password}", "BossERP_NEW", "--batch", "--raw"]
subprocess.run(cmd, input=sql, universal_newlines=True, check=True)
PY
