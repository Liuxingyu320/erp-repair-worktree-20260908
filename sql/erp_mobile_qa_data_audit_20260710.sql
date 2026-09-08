-- Mobile R2 QA candidate inventory (read only).
-- Run with an account that has SELECT permission only. Review every row and its
-- relations before a separately approved cleanup script is written.

SELECT
    candidates.source_table,
    candidates.source_id,
    candidates.identifier,
    candidates.organization_id,
    candidates.create_by,
    candidates.create_time
FROM (
    SELECT
        'sys_user' AS source_table,
        CAST(u.user_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', u.user_name, u.nick_name) AS identifier,
        CAST(u.dept_id AS CHAR) AS organization_id,
        u.create_by,
        u.create_time
    FROM sys_user u

    UNION ALL

    SELECT
        'inv_customer' AS source_table,
        CAST(c.customer_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', c.customer_code, c.customer_name, c.contact_phone) AS identifier,
        CAST(c.shop_dept_id AS CHAR) AS organization_id,
        c.create_by,
        c.create_time
    FROM inv_customer c

    UNION ALL

    SELECT
        'inv_sales_order' AS source_table,
        CAST(o.order_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', o.order_no, o.order_title, o.customer_name) AS identifier,
        CAST(o.shop_dept_id AS CHAR) AS organization_id,
        o.create_by,
        o.create_time
    FROM inv_sales_order o

    UNION ALL

    SELECT
        'inv_purchase_order' AS source_table,
        CAST(o.order_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', o.order_no, o.order_title, o.supplier_name) AS identifier,
        CAST(o.shop_dept_id AS CHAR) AS organization_id,
        o.create_by,
        o.create_time
    FROM inv_purchase_order o

    UNION ALL

    SELECT
        'inv_sales_return' AS source_table,
        CAST(r.return_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', r.return_no, r.return_title, r.customer_name) AS identifier,
        CAST(r.shop_dept_id AS CHAR) AS organization_id,
        r.create_by,
        r.create_time
    FROM inv_sales_return r

    UNION ALL

    SELECT
        'inv_purchase_return' AS source_table,
        CAST(r.return_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', r.return_no, r.return_title, r.supplier_name) AS identifier,
        CAST(r.shop_dept_id AS CHAR) AS organization_id,
        r.create_by,
        r.create_time
    FROM inv_purchase_return r

    UNION ALL

    SELECT
        'inv_stock_check' AS source_table,
        CAST(s.check_id AS CHAR) AS source_id,
        s.check_no AS identifier,
        CAST(s.shop_dept_id AS CHAR) AS organization_id,
        s.create_by,
        s.create_time
    FROM inv_stock_check s

    UNION ALL

    SELECT
        'inv_transfer_order' AS source_table,
        CAST(t.transfer_id AS CHAR) AS source_id,
        CONCAT_WS(' · ', t.order_no, t.from_dept_name, t.to_dept_name) AS identifier,
        CAST(t.from_dept_id AS CHAR) AS organization_id,
        t.create_by,
        t.create_time
    FROM inv_transfer_order t
) candidates
WHERE LOCATE('qa_', LOWER(CONCAT_WS(' ', candidates.identifier, candidates.create_by))) > 0
   OR LOCATE('qa-', LOWER(CONCAT_WS(' ', candidates.identifier, candidates.create_by))) > 0
   OR LOCATE('test_', LOWER(CONCAT_WS(' ', candidates.identifier, candidates.create_by))) > 0
   OR LOCATE('test-', LOWER(CONCAT_WS(' ', candidates.identifier, candidates.create_by))) > 0
ORDER BY candidates.create_time DESC, candidates.source_table, candidates.source_id;
