CREATE DATABASE kabuto_db;


CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS 'application' (
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



CREATE TABLE document_metadata (
	id uuid NOT NULL,
	file_path text NOT NULL,
	file_number varchar NOT NULL,
	file_type varchar NOT NULL,
	title varchar NOT NULL,
	captured_at timestamp NOT NULL,
	updated_at timestamp NULL,
	created_by varchar NOT NULL,
	updated_by varchar NULL
);
ALTER TABLE document_metadata ADD CONSTRAINT document_metadata_file_path_key UNIQUE (file_path);
ALTER TABLE document_metadata ADD CONSTRAINT document_metadata_pkey PRIMARY KEY (id);


 

