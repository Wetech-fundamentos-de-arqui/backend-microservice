-- Initial schema owned by this service.

    create table location_amenities (
        location_id bigint not null,
        amenities varchar(1000)
    ) engine=InnoDB;

    create table locations (
        available_spots integer not null,
        capacity integer not null,
        is_active bit not null,
        lat float(53),
        lng float(53),
        id bigint not null auto_increment,
        address varchar(255),
        close varchar(255),
        description TEXT,
        district varchar(255),
        image varchar(255),
        name varchar(255),
        open varchar(255),
        type varchar(255),
        primary key (id)
    ) engine=InnoDB;

    alter table location_amenities 
       add constraint FKbm5v8hmopdmmw5nnv762jfwph 
       foreign key (location_id) 
       references locations (id);
