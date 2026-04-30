CREATE SEQUENCE IF NOT EXISTS todo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS users (
    id        BIGSERIAL    PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    email     VARCHAR(255) NOT NULL UNIQUE,
    password  VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS todo (
    id          BIGINT       NOT NULL DEFAULT nextval('todo_seq') PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    completed   BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL,
    due_date    TIMESTAMP,
    user_id     BIGINT       NOT NULL REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_todo_completed           ON todo(completed);
CREATE INDEX IF NOT EXISTS idx_todo_due_date            ON todo(due_date);
CREATE INDEX IF NOT EXISTS idx_todo_completed_due_date  ON todo(completed, due_date);
