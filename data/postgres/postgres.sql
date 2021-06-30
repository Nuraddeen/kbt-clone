CREATE DATABASE kabuto_db;

\c kabuto_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS application (
    file_number varchar NOT NULL PRIMARY KEY,
    applicant_name varchar NOT NULL,
    created_by varchar NOT NULL,
    created_on timestamptz DEFAULT CURRENT_TIMESTAMP,
    updated_by varchar NOT NULL,
    updated_on timestamptz DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS document (
    document_id uuid NOT NULL PRIMARY KEY,
    file_number varchar NOT NULL,
    file_content text NOT NULL,
    uploaded_by varchar NOT NULL,
    uploaded_on timestamptz NOT NULL,
    CONSTRAINT fk_application FOREIGN KEY (file_number) REFERENCES application (file_number) ON UPDATE CASCADE ON DELETE CASCADE
);
