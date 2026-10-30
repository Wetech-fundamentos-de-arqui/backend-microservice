-- Initial schema owned by this service.

    create table outbox_events (
        attempts integer not null,
        next_attempt_at datetime(6),
        occurred_at datetime(6),
        last_error varchar(500),
        event_id varchar(255) not null,
        event_type varchar(255),
        payload LONGTEXT,
        state varchar(255),
        user_id varchar(255),
        primary key (event_id)
    ) engine=InnoDB;

    create table problem_reports (
        booking_checked bit not null,
        charge_waived bit not null,
        garage_updated bit not null,
        report_date datetime(6),
        payload_hash varchar(64),
        request_key varchar(128),
        description varchar(2000),
        booking_id varchar(255),
        id varchar(255) not null,
        photo LONGTEXT,
        processing_status varchar(255),
        status varchar(255),
        user_id varchar(255),
        vehicle_id varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table problem_report_categories (
        categories varchar(255),
        problem_report_id varchar(255) not null
    ) engine=InnoDB;

    create table travel_histories (
        created_at datetime(6),
        id bigint not null auto_increment,
        image varchar(255) not null,
        location varchar(255) not null,
        travel_distance varchar(255) not null,
        trip_duration varchar(255) not null,
        user_id varchar(255) not null,
        vehicle varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table trips (
        average_speed float(53) not null,
        calories_burned integer not null,
        carbon_saved float(53) not null,
        distance float(53) not null,
        duration integer not null,
        max_speed float(53) not null,
        temperature integer not null,
        total_cost float(53) not null,
        end_date datetime(6),
        id bigint not null auto_increment,
        start_date datetime(6),
        completion_hash varchar(64),
        booking_id varchar(255),
        end_location_id varchar(255),
        route varchar(255),
        route_source varchar(255),
        start_location_id varchar(255),
        status varchar(255),
        user_id varchar(255),
        vehicle_id varchar(255),
        weather varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table trip_incident_reports (
        trip_id bigint not null,
        incident_reports varchar(1000)
    ) engine=InnoDB;

    create table trip_photos (
        trip_id bigint not null,
        photos varchar(1000)
    ) engine=InnoDB;

    create table trip_route_coordinates (
        lat float(53),
        lng float(53),
        trip_id bigint not null
    ) engine=InnoDB;

    create table unlock_requests (
        attempts integer not null,
        actual_unlock_time datetime(6),
        code_expires_at datetime(6),
        requested_at datetime(6),
        scheduled_unlock_time datetime(6),
        access_code varchar(255),
        booking_id varchar(255),
        error_message varchar(255),
        id varchar(255) not null,
        method varchar(255),
        status varchar(255),
        unlock_code varchar(255),
        user_id varchar(255),
        vehicle_id varchar(255),
        primary key (id)
    ) engine=InnoDB;

    alter table problem_reports 
       add constraint UKpf5vw5rd37emqheu90s7w7et6 unique (user_id, request_key);

    alter table trips 
       add constraint UK86ymq9no9ibr2j9o0kvac4lhl unique (booking_id);

    alter table problem_report_categories 
       add constraint FKomx39qf830lhj5k005mxmhjgw 
       foreign key (problem_report_id) 
       references problem_reports (id);

    alter table trip_incident_reports 
       add constraint FKbivwnrut8neg3hnwytjxgo9pc 
       foreign key (trip_id) 
       references trips (id);

    alter table trip_photos 
       add constraint FKjqtkwd984vvc053hxgkijw6yv 
       foreign key (trip_id) 
       references trips (id);

    alter table trip_route_coordinates 
       add constraint FKd1pupwpsoa1ao2b3e7yer8oxf 
       foreign key (trip_id) 
       references trips (id);

CREATE TABLE user_guards (user_id VARCHAR(255) NOT NULL PRIMARY KEY,deleted BOOLEAN NOT NULL DEFAULT FALSE,deletion_pending BOOLEAN NOT NULL DEFAULT FALSE,workflow_id VARCHAR(255));
CREATE INDEX idx_outbox_due ON outbox_events(state,next_attempt_at);
