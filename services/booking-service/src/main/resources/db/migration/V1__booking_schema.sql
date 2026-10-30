-- Initial schema owned by this service.

    create table bookings (
        archived bit,
        average_speed float(53),
        destination_change_fees float(53),
        destination_lat float(53),
        destination_lng float(53),
        discount float(53),
        distance float(53),
        duration integer,
        estimated_cost float(53),
        estimated_distance float(53),
        estimated_duration integer,
        final_cost float(53),
        score integer,
        total_cost float(53),
        vehicle_relocated bit not null,
        actual_end_date datetime(6),
        actual_start_date datetime(6),
        end_date datetime(6),
        reserved_at datetime(6),
        start_date datetime(6),
        completion_hash varchar(64),
        comment varchar(1000),
        destination_address varchar(255),
        end_location_id varchar(255),
        id varchar(255) not null,
        payment_method varchar(255),
        payment_status varchar(255),
        pending_completion LONGTEXT,
        route_source varchar(255),
        start_location_id varchar(255),
        status varchar(255),
        tags varchar(255),
        user_id varchar(255),
        vehicle_id varchar(255),
        waiver_report_id varchar(255),
        route_coordinates json,
        primary key (id)
    ) engine=InnoDB;

    create table booking_drafts (
        duration float(53) not null,
        email_confirmation bit not null,
        push_notification bit not null,
        sms_reminder bit not null,
        expires_at datetime(6),
        saved_at datetime(6),
        id varchar(255) not null,
        selected_date varchar(255),
        unlock_time varchar(255),
        user_id varchar(255),
        vehicle_id varchar(255),
        primary key (id)
    ) engine=InnoDB;

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

CREATE TABLE user_guards (user_id VARCHAR(255) NOT NULL PRIMARY KEY,deleted BOOLEAN NOT NULL DEFAULT FALSE,deletion_pending BOOLEAN NOT NULL DEFAULT FALSE,workflow_id VARCHAR(255));

CREATE TABLE vehicle_schedules (vehicle_id VARCHAR(255) NOT NULL PRIMARY KEY);
CREATE TABLE booking_requests (user_id VARCHAR(255) NOT NULL,request_key VARCHAR(128) NOT NULL,payload_hash VARCHAR(64) NOT NULL,booking_id VARCHAR(255) NOT NULL,PRIMARY KEY (user_id,request_key));
CREATE INDEX idx_bookings_vehicle_status ON bookings(vehicle_id,status);
CREATE INDEX idx_bookings_user_status ON bookings(user_id,status);
CREATE INDEX idx_outbox_due ON outbox_events(state,next_attempt_at);
