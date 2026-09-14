import copy
import unittest
from audit_contract_salary_sources import classify


def salary(total=5000):
    return dict(baseSalary=total, postSalary=0, fieldAllowance=0, performanceSalary=0, salaryTotal=total)


class SalaryAuditTests(unittest.TestCase):
    def root(self):
        return [{'kind':'PROFILE','employeeId':11,'salary':salary()},
                {'kind':'AUDIT','employeeId':11,'id':2,'at':'2026-01-01 12:00:00','operatorId':9,'evidenceValid':1,'salary':salary()}]
    def test_unique_audit_candidate_is_deterministic_without_mutating_input(self):
        rows=self.root(); before=copy.deepcopy(rows)
        self.assertEqual(classify(rows)[0]['classification'],'AUDIT_CANDIDATE')
        self.assertEqual(classify(rows),classify(list(reversed(rows))))
        self.assertEqual(rows,before)
    def test_formal_chain_matches_current(self):
        rows=self.root(); rows[0]['salary']=salary(6000)
        rows.append(dict(kind='ACTION',employeeId=11,id=8,at='2026-02-01 12:00:00',effectiveDate='2026-02-01',status='CONFIRMED',before=salary(),after=salary(6000)))
        self.assertEqual(classify(rows)[0]['classification'],'CHAIN_CANDIDATE')
        rows[2]['before']=salary(5500)
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
    def test_ambiguous_roots_do_not_pick_latest(self):
        rows=self.root(); rows.append({**rows[1],'id':3})
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
    def test_mismatch_and_invalid_identity_require_review(self):
        rows=self.root();rows[0]['salary']=salary(8000)
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
        rows=self.root();rows[1]['evidenceValid']=0
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
    def test_missing_audit_signed_contract_never_automatically_verifies(self):
        rows=self.root()[:1]; rows.append(dict(kind='CONTRACT',employeeId=11,id=3,status='SIGNED',identityValid=1,salary=salary()))
        self.assertEqual(classify(rows)[0]['classification'],'HISTORICAL_CONTRACT_REVIEW')
        rows.append({**rows[1],'id':4})
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
    def test_timestamp_tie_and_invalid_salary_are_not_guessed(self):
        rows=self.root(); rows.append(dict(kind='ACTION',employeeId=11,id=8,at=rows[1]['at'],effectiveDate='2026-01-01',status='CONFIRMED',before=salary(),after=salary()))
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')
        rows=self.root(); rows[0]['salary']['postSalary']=None
        self.assertEqual(classify(rows)[0]['classification'],'REVIEW_REQUIRED')

if __name__=='__main__': unittest.main()
