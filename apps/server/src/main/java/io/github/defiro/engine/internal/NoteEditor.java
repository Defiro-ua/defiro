package io.github.defiro.engine.internal;

import io.github.defiro.engine.internal.Notes.Note;
import io.github.defiro.identity.Person;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing the notes of a task (UC19): only who took it, while it is open. A note is not empty, has
 * at most 1000 characters, and is on a field the requester filled in: an answer of the request, or
 * one of its files. What comes from Entra ID takes no note.
 */
@Component
class NoteEditor {

    private final Tasks tasks;
    private final Procedures procedures;
    private final Files files;
    private final Notes notes;

    NoteEditor(Tasks tasks, Procedures procedures, Files files, Notes notes) {
        this.tasks = tasks;
        this.procedures = procedures;
        this.files = files;
        this.notes = notes;
    }

    /** Writes the note, new or changed, or gives empty if there is no such task. */
    @Transactional
    Optional<Note> put(UUID task, String field, String text, Person person) {
        return tasks.find(task).map(found -> {
            String written = text == null ? "" : text.strip();
            if (written.isEmpty()) {
                throw new InvalidNoteException("The note is empty");
            }
            if (written.length() > Notes.LONGEST) {
                throw new InvalidNoteException("The note has more than " + Notes.LONGEST + " characters");
            }
            Note note = new Note(field, label(found, field), written);
            if (!notes.put(task, person, note)) {
                throw new NotAllowedException();
            }
            return note;
        });
    }

    /** Deletes the note, or gives false if there is no such task or note. */
    @Transactional
    boolean delete(UUID task, String field, Person person) {
        Optional<Tasks.Task> found = tasks.find(task);
        if (found.isEmpty()) {
            return false;
        }
        if (!found.get().isOpen() || !person.id().equals(found.get().takenBy())) {
            throw new NotAllowedException();
        }
        return notes.delete(task, person, field);
    }

    // The field's label as the request shows it: an answer's, or a file's name
    private String label(Tasks.Task task, String field) {
        if (field.startsWith("file:")) {
            return fileId(field)
                    .flatMap(files::find)
                    .filter(file -> file.procedure().equals(task.procedure()))
                    .map(Files.StoredFile::filename)
                    .orElseThrow(() -> new InvalidNoteException("The request has no such file"));
        }
        return procedures.request(task.procedure()).answers().stream()
                .filter(answer -> answer.field().equals(field))
                .map(RequestData.Answer::label)
                .findFirst()
                .orElseThrow(() -> new InvalidNoteException("The request has no such field"));
    }

    private static Optional<UUID> fileId(String field) {
        try {
            return Optional.of(UUID.fromString(field.substring("file:".length())));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }
}
