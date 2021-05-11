-- CREATE DATABASE kabuto_db;
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS document_metadata
(
    id          uuid      NOT NULL PRIMARY KEY,
    file_path   TEXT      NOT NULL UNIQUE,
    file_number VARCHAR   NOT NULL UNIQUE,
    file_type   VARCHAR   NOT NULL,
    title       VARCHAR   NOT NULL,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR   NOT NULL,
    updated_by  VARCHAR
);