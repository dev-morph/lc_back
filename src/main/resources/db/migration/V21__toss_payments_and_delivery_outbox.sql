alter table payment_transaction modify heart_product_id bigint null;
alter table payment_transaction modify provider_transaction_id varchar(128) null;
alter table payment_transaction add column order_id varchar(64) unique;
alter table payment_transaction add column order_name varchar(120);
alter table payment_transaction add column heart_amount_snapshot int not null default 0;
alter table payment_transaction add column meeting_application_id bigint;
alter table payment_transaction add column return_path varchar(512);
alter table payment_transaction add column customer_key varchar(64);
alter table payment_transaction add column refund_reason varchar(512);
alter table payment_transaction add column refunded_at datetime(6);
create table notification_outbox (
 id bigint auto_increment primary key,user_id bigint not null,event_type varchar(64) not null,
 reference_id bigint not null,dedupe_key varchar(160) not null unique,
 link_path varchar(512) not null,status varchar(32) not null,attempts int not null default 0,
 next_attempt_at datetime(6) not null,provider_message_id varchar(255),failure_reason varchar(512),
 created_at datetime(6) not null,sent_at datetime(6), index idx_outbox_pending(status,next_attempt_at)
);
