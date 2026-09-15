--liquibase formatted sql

--changeset vksiv:001-create-users
--comment: Application user accounts.
CREATE TABLE users (
    id            UUID          NOT NULL,
    email         VARCHAR(320)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    display_name  VARCHAR(80)   NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);
--rollback DROP TABLE users;

--changeset vksiv:001-users-email-unique
--comment: Emails are compared case-insensitively, so the uniqueness guarantee must be too.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
--rollback DROP INDEX ux_users_email_lower;
