-- The file table of the team's database diagram: the files attached to a procedure, and to the
-- document they came with. The bytes are not here: the architecture keeps them in the File store,
-- found by storage_key, and sha256 tells if they changed.
create table engine.file (
    id           uuid         primary key,
    procedure_id uuid         not null references engine.procedure,
    document_id  uuid         references engine.document,
    filename     varchar(255) not null,
    storage_key  varchar(255) not null unique,
    sha256       char(64)     not null
);

-- TEMPORARY, for UC15 (Dinis): the File store is not built yet, so UC17 keeps the bytes here,
-- behind the FileStore interface. When the File store exists, its FileStore replaces
-- TemporaryFileStore and a later migration drops this table.
create table engine.file_content (
    storage_key varchar(255) primary key,
    content     bytea        not null
);
