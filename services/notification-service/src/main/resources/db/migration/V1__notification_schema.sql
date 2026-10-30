-- Initial schema owned by this service.

    create table notifications (
        action_required bit not null,
        is_read bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        read_at datetime(6),
        source_service varchar(32),
        event_hash varchar(64),
        event_id varchar(128),
        message varchar(2000),
        category varchar(255),
        color varchar(255),
        icon varchar(255),
        priority varchar(255),
        public_id varchar(255) not null,
        related_entity_id varchar(255),
        related_entity_type varchar(255),
        title varchar(255),
        type varchar(255),
        user_id varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    alter table notifications 
       add constraint UK7m2aswjxhv7rjtgkvvki8pn47 unique (source_service, event_id);

    alter table notifications 
       add constraint UKqwcfwkb22prkvpgl4uyya4c8h unique (public_id);

CREATE TABLE user_guards (user_id VARCHAR(255) NOT NULL PRIMARY KEY,deleted BOOLEAN NOT NULL DEFAULT FALSE,deletion_pending BOOLEAN NOT NULL DEFAULT FALSE,workflow_id VARCHAR(255));
