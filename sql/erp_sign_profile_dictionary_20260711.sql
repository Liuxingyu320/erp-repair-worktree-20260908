-- Normalize employee signing profile dictionary values.
-- Safety order: snapshot, reject unknown values, update known legacy values only.

CREATE TABLE IF NOT EXISTS bak_sys_user_profile_signing_20260711
LIKE sys_user_profile;

INSERT INTO bak_sys_user_profile_signing_20260711
SELECT p.*
FROM sys_user_profile p
LEFT JOIN bak_sys_user_profile_signing_20260711 b ON b.user_id = p.user_id
WHERE b.user_id IS NULL;

-- Visible preflight details for the deployment record.
SELECT user_id, contract_type, social_type
FROM sys_user_profile
WHERE (contract_type IS NOT NULL AND contract_type <> ''
       AND contract_type NOT IN ('固定期限劳动合同', '无固定期限劳动合同', '劳动合同', '劳务协议', '劳务合同',
                                 '实习协议', '外包合同', 'LABOR_CONTRACT', 'SERVICE_CONTRACT',
                                 'INTERNSHIP_AGREEMENT', 'OUTSOURCING_CONTRACT'))
   OR (social_type IS NOT NULL AND social_type <> ''
       AND social_type NOT IN ('本地社保', '异地社保', '有社保', '无需缴纳', '无社保', '劳务派遣', '待确认',
                               'SOCIAL_INSURED', 'SOCIAL_UNINSURED', 'DISPATCHED', 'PENDING_CONFIRMATION'));

DROP PROCEDURE IF EXISTS migrate_sign_profile_dictionary_20260711;
DELIMITER $$
CREATE PROCEDURE migrate_sign_profile_dictionary_20260711()
BEGIN
    DECLARE unknown_value_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO unknown_value_count
    FROM sys_user_profile
    WHERE (contract_type IS NOT NULL AND contract_type <> ''
           AND contract_type NOT IN ('固定期限劳动合同', '无固定期限劳动合同', '劳动合同', '劳务协议', '劳务合同',
                                     '实习协议', '外包合同', 'LABOR_CONTRACT', 'SERVICE_CONTRACT',
                                     'INTERNSHIP_AGREEMENT', 'OUTSOURCING_CONTRACT'))
       OR (social_type IS NOT NULL AND social_type <> ''
           AND social_type NOT IN ('本地社保', '异地社保', '有社保', '无需缴纳', '无社保', '劳务派遣', '待确认',
                                   'SOCIAL_INSURED', 'SOCIAL_UNINSURED', 'DISPATCHED', 'PENDING_CONFIRMATION'));

    IF unknown_value_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '检测到未知合同或社保值，迁移已停止，请先补充映射';
    END IF;

    UPDATE sys_user_profile
    SET contract_term = CASE
        WHEN contract_term = '固定期限' THEN 'FIXED_TERM'
        WHEN contract_term = '无固定期限' THEN 'OPEN_ENDED'
        WHEN contract_type IN ('固定期限劳动合同', '劳动合同')
             AND NULLIF(TRIM(contract_term), '') IS NULL THEN 'FIXED_TERM'
        WHEN contract_type = '无固定期限劳动合同'
             AND NULLIF(TRIM(contract_term), '') IS NULL THEN 'OPEN_ENDED'
        ELSE contract_term
    END
    WHERE contract_term IN ('固定期限', '无固定期限')
       OR (NULLIF(TRIM(contract_term), '') IS NULL
           AND contract_type IN ('固定期限劳动合同', '劳动合同', '无固定期限劳动合同'));

    UPDATE sys_user_profile
    SET contract_type = CASE
        WHEN contract_type IN ('固定期限劳动合同', '无固定期限劳动合同', '劳动合同')
            THEN 'LABOR_CONTRACT'
        WHEN contract_type IN ('劳务协议', '劳务合同') THEN 'SERVICE_CONTRACT'
        WHEN contract_type = '实习协议' THEN 'INTERNSHIP_AGREEMENT'
        WHEN contract_type = '外包合同' THEN 'OUTSOURCING_CONTRACT'
        ELSE contract_type
    END
    WHERE contract_type IN ('固定期限劳动合同', '无固定期限劳动合同', '劳动合同',
                            '劳务协议', '劳务合同', '实习协议', '外包合同');

    UPDATE sys_user_profile
    SET social_type = CASE
        WHEN social_type IN ('本地社保', '异地社保', '有社保') THEN 'SOCIAL_INSURED'
        WHEN social_type IN ('无需缴纳', '无社保') THEN 'SOCIAL_UNINSURED'
        WHEN social_type = '劳务派遣' THEN 'DISPATCHED'
        WHEN social_type = '待确认' THEN 'PENDING_CONFIRMATION'
        ELSE social_type
    END
    WHERE social_type IN ('本地社保', '异地社保', '有社保', '无需缴纳', '无社保', '劳务派遣', '待确认');
END$$
DELIMITER ;

CALL migrate_sign_profile_dictionary_20260711();
DROP PROCEDURE IF EXISTS migrate_sign_profile_dictionary_20260711;

SELECT
    SUM(contract_type IN ('固定期限劳动合同', '无固定期限劳动合同', '劳动合同',
                          '劳务协议', '劳务合同', '实习协议', '外包合同')) AS legacy_contract_value_count,
    SUM(social_type IN ('本地社保', '异地社保', '有社保', '无需缴纳', '无社保', '劳务派遣', '待确认'))
        AS legacy_social_value_count
FROM sys_user_profile;
