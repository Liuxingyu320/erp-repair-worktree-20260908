-- Labor contract internal e-sign evidence upgrade.
-- Scope: document freeze, archive/certificate evidence, audit hash chain, hash verification.

ALTER TABLE oa_labor_contract
    ADD COLUMN document_version varchar(64) DEFAULT NULL COMMENT '签署文件版本号' AFTER contract_file_hash,
    ADD COLUMN preview_file_hash varchar(128) DEFAULT NULL COMMENT '预览PDF SHA-256' AFTER document_version,
    ADD COLUMN archive_file_hash varchar(128) DEFAULT NULL COMMENT '归档PDF SHA-256' AFTER preview_file_hash,
    ADD COLUMN certificate_file_url varchar(500) DEFAULT NULL COMMENT '完成证明PDF下载URL' AFTER archive_file_hash,
    ADD COLUMN certificate_file_hash varchar(128) DEFAULT NULL COMMENT '完成证明PDF SHA-256' AFTER certificate_file_url,
    ADD COLUMN template_file_hash varchar(128) DEFAULT NULL COMMENT '发送时模板文件SHA-256' AFTER certificate_file_hash,
    ADD COLUMN seal_image_hash varchar(128) DEFAULT NULL COMMENT '发送时企业章图片SHA-256' AFTER template_file_hash;

CREATE INDEX idx_oa_labor_contract_preview_hash ON oa_labor_contract (preview_file_hash);
CREATE INDEX idx_oa_labor_contract_archive_hash ON oa_labor_contract (archive_file_hash);
CREATE INDEX idx_oa_labor_contract_certificate_hash ON oa_labor_contract (certificate_file_hash);

ALTER TABLE oa_labor_contract_event
    ADD COLUMN document_version varchar(64) DEFAULT NULL COMMENT '事件对应签署文件版本号' AFTER file_hash,
    ADD COLUMN document_hash varchar(128) DEFAULT NULL COMMENT '事件对应文档SHA-256' AFTER document_version,
    ADD COLUMN prev_event_hash varchar(128) DEFAULT NULL COMMENT '前一事件哈希' AFTER document_hash,
    ADD COLUMN event_hash varchar(128) DEFAULT NULL COMMENT '当前事件哈希' AFTER prev_event_hash,
    ADD COLUMN request_id varchar(64) DEFAULT NULL COMMENT '事件请求ID' AFTER event_hash;

CREATE INDEX idx_oa_labor_contract_event_hash ON oa_labor_contract_event (event_hash);
