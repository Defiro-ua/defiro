-- The final document of a closed procedure, kept once. UC22 writes it, and UC23 reads it. As in
-- engine.file, the bytes are in the File store, found by storage_key, and sha256 tells if they
-- changed.
create table engine.generated_document (
    procedure_id uuid         primary key references engine.procedure,
    filename     varchar(255) not null,
    media_type   varchar(100) not null,
    storage_key  varchar(255) not null unique,
    sha256       char(64)     not null,
    generated_at timestamptz  not null default now()
);
