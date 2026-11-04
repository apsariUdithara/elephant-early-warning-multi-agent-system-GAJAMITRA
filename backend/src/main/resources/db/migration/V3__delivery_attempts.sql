-- How many times a delivery has been handed to the SMS provider. The retry job stops after MAX_ATTEMPTS.
alter table alert_delivery add column attempts integer not null default 0 check (attempts >= 0);
create index delivery_retry_idx on alert_delivery (updated_at) where status in ('PENDING', 'FAILED');
