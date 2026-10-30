-- Initial schema owned by this service.

    create table profiles (
        average_rating float(53),
        date_of_birth date,
        notifications bit,
        total_distance float(53),
        total_spent float(53),
        total_trips integer,
        id bigint not null auto_increment,
        address varchar(255),
        emergency_contact varchar(255),
        language varchar(255),
        name varchar(255),
        phone varchar(255),
        profile_picture LONGTEXT,
        theme varchar(255),
        user_id varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    alter table profiles 
       add constraint UK4ixsj6aqve5pxrbw2u0oyk8bb unique (user_id);

CREATE TABLE user_guards (user_id VARCHAR(255) NOT NULL PRIMARY KEY,deleted BOOLEAN NOT NULL DEFAULT FALSE,deletion_pending BOOLEAN NOT NULL DEFAULT FALSE,workflow_id VARCHAR(255));
