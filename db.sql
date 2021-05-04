-- CREATE DATABASE kabuto_db;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS document_meta_data (
    id uuid NOT NULL PRIMARY KEY,
    file_path TEXT NOT NULL UNIQUE,
    file_number VARCHAR NOT NULL UNIQUE,
    file_type VARCHAR NOT NULL,
    title VARCHAR NOT NULL,
    date_captured TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    number_of_pages INTEGER NOT NULL,
    created_by VARCHAR NOT NULL,
    updated_by VARCHAR
);