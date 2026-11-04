-- 더미 민원 전부 삭제 (tracking_id 'DM-')
DELETE FROM reports WHERE tracking_id LIKE 'DM-%';
