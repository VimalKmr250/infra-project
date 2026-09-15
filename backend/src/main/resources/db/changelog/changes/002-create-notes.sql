--liquibase formatted sql

--changeset vksiv:002-create-notes
--comment: Sample feature. Delete this changeset's table together with the note package.
CREATE TABLE notes (
    id         UUID           NOT NULL,
    user_id    UUID           NOT NULL,
    title      VARCHAR(200)   NOT NULL,
    body       VARCHAR(10000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notes PRIMARY KEY (id),
    CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
--rollback DROP TABLE notes;

--changeset vksiv:002-notes-user-index
--comment: Every note query is scoped by owner and ordered newest first.
CREATE INDEX ix_notes_user_created ON notes (user_id, created_at DESC);
--rollback DROP INDEX ix_notes_user_created;
