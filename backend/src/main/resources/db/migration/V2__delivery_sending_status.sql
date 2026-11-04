-- SENDING: the sender has claimed the delivery and is calling the SMS provider. A row left in SENDING (crash or
-- failed status save after the call) means "maybe sent": a retry must never send it again (invariant 4).
alter table alert_delivery drop constraint alert_delivery_status_check;
alter table alert_delivery add constraint alert_delivery_status_check
    check (status in ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED_SHADOW'));
