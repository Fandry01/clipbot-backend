-- VXX__normalize_job_status.sql
UPDATE job SET status = 'FAILED'   WHERE status = 'ERROR';
UPDATE job SET status = 'COMPLETE' WHERE status = 'DONE';
