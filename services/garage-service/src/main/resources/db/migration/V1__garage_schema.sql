-- Initial schema owned by this service.

    create table favorites (
        added_at datetime(6) not null,
        id bigint not null auto_increment,
        vehicle_id bigint not null,
        notes varchar(500),
        user_id varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table vehicle_features (
        vehicle_id bigint not null,
        features varchar(1000)
    ) engine=InnoDB;

    create table vehicles (
        battery integer,
        max_speed integer,
        price_per_minute float(53) not null,
        rating float(53) not null,
        total_kilometers float(53) not null,
        vehicle_range integer,
        weight float(53) not null,
        year integer,
        id bigint not null auto_increment,
        last_maintenance datetime(6),
        next_maintenance datetime(6),
        image varchar(500),
        brand varchar(255),
        color varchar(255),
        company_id varchar(255),
        license_plate varchar(255),
        location varchar(255),
        maintenance_status varchar(255),
        model varchar(255),
        status varchar(255),
        type varchar(255),
        primary key (id)
    ) engine=InnoDB;

    alter table favorites 
       add constraint UK4t9v064bii51dg5i5oggj6dp0 unique (user_id, vehicle_id);

    alter table vehicle_features 
       add constraint FK5gpmfcffxs7w11yckeuvwd3jk 
       foreign key (vehicle_id) 
       references vehicles (id);

CREATE TABLE user_guards (user_id VARCHAR(255) NOT NULL PRIMARY KEY,deleted BOOLEAN NOT NULL DEFAULT FALSE,deletion_pending BOOLEAN NOT NULL DEFAULT FALSE,workflow_id VARCHAR(255));
