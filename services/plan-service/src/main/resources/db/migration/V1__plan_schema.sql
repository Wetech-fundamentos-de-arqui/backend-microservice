-- Initial schema owned by this service.

    create table plans (
        discount_percentage integer not null,
        duration_days integer not null,
        free_minutes_per_month integer not null,
        is_active bit not null,
        is_popular bit not null,
        max_minutes_per_trip integer not null,
        max_trips_per_day integer not null,
        price float(23) not null,
        price_per_minute float(23) not null,
        id bigint not null auto_increment,
        color varchar(255),
        currency varchar(255),
        description varchar(255),
        duration varchar(255),
        name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table plan_benefits (
        plan_id bigint not null,
        benefits varchar(1000)
    ) engine=InnoDB;

    alter table plan_benefits 
       add constraint FK83bgjdqayq4p3j41rb05969ct 
       foreign key (plan_id) 
       references plans (id);
