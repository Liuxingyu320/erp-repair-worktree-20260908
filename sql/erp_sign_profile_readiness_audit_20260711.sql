-- Read-only readiness audit for contract automation. Export results to the deployment ticket only.

WITH assigned_post AS (
    SELECT user_id, COUNT(*) AS post_count
    FROM sys_user_post
    GROUP BY user_id
), legacy_pending AS (
    SELECT employee_id, COUNT(*) AS pending_count
    FROM oa_labor_contract
    WHERE status IN ('draft', 'pending_sign')
    GROUP BY employee_id
    UNION ALL
    SELECT employee_id, COUNT(*) AS pending_count
    FROM oa_sign_package
    WHERE status IN ('draft', 'pending_sign', 'part_viewed')
    GROUP BY employee_id
), pending_by_employee AS (
    SELECT employee_id, SUM(pending_count) AS pending_count
    FROM legacy_pending
    GROUP BY employee_id
)
SELECT
    COALESCE(NULLIF(p.store_name, ''), d.dept_name, '未归属门店') AS store_name,
    COUNT(DISTINCT u.user_id) AS employee_total,
    SUM(CASE WHEN NULLIF(TRIM(u.phonenumber), '') IS NULL THEN 1 ELSE 0 END) AS missing_phone_count,
    SUM(CASE WHEN NULLIF(TRIM(p.id_number), '') IS NULL THEN 1 ELSE 0 END) AS missing_id_number_count,
    SUM(CASE WHEN NULLIF(TRIM(p.current_address), '') IS NULL THEN 1 ELSE 0 END) AS missing_address_count,
    SUM(CASE WHEN COALESCE(ap.post_count, 0) = 0 THEN 1 ELSE 0 END) AS missing_post_count,
    SUM(CASE WHEN NULLIF(TRIM(p.job_grade), '') IS NULL THEN 1 ELSE 0 END) AS missing_grade_count,
    SUM(CASE WHEN NULLIF(TRIM(p.legal_entity), '') IS NULL THEN 1 ELSE 0 END) AS missing_legal_entity_count,
    SUM(CASE WHEN p.contract_start_date IS NULL OR p.contract_end_date IS NULL THEN 1 ELSE 0 END)
        AS missing_contract_dates_count,
    SUM(CASE WHEN p.contract_start_date IS NOT NULL AND p.contract_end_date IS NOT NULL
                  AND p.contract_end_date < p.contract_start_date THEN 1 ELSE 0 END)
        AS invalid_contract_dates_count,
    SUM(CASE WHEN NULLIF(TRIM(p.contract_type), '') IS NULL THEN 1 ELSE 0 END)
        AS missing_contract_type_count,
    SUM(CASE WHEN NULLIF(TRIM(p.social_type), '') IS NULL THEN 1 ELSE 0 END)
        AS missing_social_type_count,
    SUM(CASE WHEN NULLIF(TRIM(p.contract_type), '') IS NOT NULL
                  AND p.contract_type NOT IN ('LABOR_CONTRACT', 'SERVICE_CONTRACT',
                                              'INTERNSHIP_AGREEMENT', 'OUTSOURCING_CONTRACT')
             THEN 1 ELSE 0 END) AS unknown_contract_type_count,
    SUM(CASE WHEN NULLIF(TRIM(p.social_type), '') IS NOT NULL
                  AND p.social_type NOT IN ('SOCIAL_INSURED', 'SOCIAL_UNINSURED',
                                            'DISPATCHED', 'PENDING_CONFIRMATION')
             THEN 1 ELSE 0 END) AS unknown_social_type_count,
    SUM(COALESCE(pb.pending_count, 0)) AS legacy_pending_contract_count
FROM sys_user u
LEFT JOIN sys_user_profile p ON p.user_id = u.user_id
LEFT JOIN sys_dept d ON d.dept_id = u.dept_id
LEFT JOIN assigned_post ap ON ap.user_id = u.user_id
LEFT JOIN pending_by_employee pb ON pb.employee_id = u.user_id
WHERE u.del_flag = '0'
GROUP BY COALESCE(NULLIF(p.store_name, ''), d.dept_name, '未归属门店')
ORDER BY store_name;

SELECT
    template_source,
    COUNT(*) AS template_total,
    SUM(CASE WHEN NULLIF(TRIM(source_file_url), '') IS NULL THEN 1 ELSE 0 END)
        AS template_source_missing_count
FROM (
    SELECT 'oa_labor_contract_template' AS template_source, template_file_url AS source_file_url
    FROM oa_labor_contract_template
    WHERE status = '0'
    UNION ALL
    SELECT 'oa_sign_template' AS template_source, file_url AS source_file_url
    FROM oa_sign_template
    WHERE status = '0'
) template_sources
GROUP BY template_source
ORDER BY template_source;

SELECT
    pending.employee_id,
    u.nick_name AS employee_name,
    COUNT(*) AS legacy_pending_contract_count
FROM (
    SELECT employee_id, contract_id AS business_id, 'labor_contract' AS business_type
    FROM oa_labor_contract
    WHERE status IN ('draft', 'pending_sign')
    UNION ALL
    SELECT employee_id, package_id AS business_id, 'sign_package' AS business_type
    FROM oa_sign_package
    WHERE status IN ('draft', 'pending_sign', 'part_viewed')
) pending
LEFT JOIN sys_user u ON u.user_id = pending.employee_id
GROUP BY pending.employee_id, u.nick_name
HAVING COUNT(*) > 1
ORDER BY legacy_pending_contract_count DESC, pending.employee_id;
