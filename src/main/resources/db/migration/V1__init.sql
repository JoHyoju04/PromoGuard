create table promotions (
    id bigserial primary key,
    name varchar(120) not null,
    status varchar(20) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    created_at timestamp with time zone not null default now(),
    constraint ck_promotions_period check (starts_at < ends_at)
);

create table promotion_rewards (
    id bigserial primary key,
    promotion_id bigint not null references promotions(id),
    reward_type varchar(30) not null,
    name varchar(120) not null,
    total_quantity integer not null,
    claimed_quantity integer not null default 0,
    per_user_limit integer not null,
    status varchar(20) not null,
    created_at timestamp with time zone not null default now(),
    constraint ck_promotion_rewards_total_quantity check (total_quantity > 0),
    constraint ck_promotion_rewards_claimed_quantity check (claimed_quantity >= 0 and claimed_quantity <= total_quantity),
    constraint ck_promotion_rewards_per_user_limit check (per_user_limit > 0)
);

create table reward_user_counters (
    id bigserial primary key,
    reward_id bigint not null references promotion_rewards(id),
    user_id varchar(80) not null,
    claim_count integer not null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uk_reward_user_counters_reward_user unique (reward_id, user_id),
    constraint ck_reward_user_counters_claim_count check (claim_count > 0)
);

create table reward_claims (
    id bigserial primary key,
    promotion_id bigint not null references promotions(id),
    reward_id bigint not null references promotion_rewards(id),
    user_id varchar(80) not null,
    claim_type varchar(30) not null,
    status varchar(20) not null,
    claimed_at timestamp with time zone not null,
    created_at timestamp with time zone not null default now()
);

create index idx_reward_claims_reward_user on reward_claims(reward_id, user_id);

create table outbox_events (
    id bigserial primary key,
    aggregate_type varchar(50) not null,
    aggregate_id bigint not null,
    event_type varchar(80) not null,
    payload text not null,
    status varchar(20) not null,
    retry_count integer not null default 0,
    next_retry_at timestamp with time zone not null default now(),
    published_at timestamp with time zone,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint uk_outbox_events_aggregate_event unique (aggregate_type, aggregate_id, event_type)
);

create index idx_outbox_events_status_retry on outbox_events(status, next_retry_at, id);

create table notification_deliveries (
    id bigserial primary key,
    reward_claim_id bigint not null references reward_claims(id),
    user_id varchar(80) not null,
    channel varchar(30) not null,
    status varchar(20) not null,
    failure_reason text,
    sent_at timestamp with time zone,
    created_at timestamp with time zone not null default now()
);

create table dead_letter_events (
    id bigserial primary key,
    outbox_event_id bigint references outbox_events(id),
    event_type varchar(80) not null,
    payload text not null,
    failure_reason text not null,
    status varchar(20) not null,
    retry_count integer not null default 0,
    last_retried_at timestamp with time zone,
    created_at timestamp with time zone not null default now()
);

create index idx_dead_letter_events_status on dead_letter_events(status, id);
