package io.github.defiro.engine.internal;

import io.github.defiro.identity.Person;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The reviewer's notes on the fields of a request (UC19): the document of their review step, as
 * the team diagram has it. There is one per task, with the task's id, kept as {author, notes:
 * {field: {label, text}}}: a draft while the reviewer works, sent once they decide. A note is on
 * an answer (its field) or on a file («file:» and its id).
 *
 * <p>Only who took the open task writes in it, and each write is one statement that checks that,
 * as Tasks does: two writes at once never interleave a read and a write.
 */
@Repository
class Notes {

    static final int LONGEST = 1000;
    static final String DRAFT = "draft";
    static final String SENT = "sent";
    private static final String FILE = "file:";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    Notes(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** A note on a field of the request. */
    record Note(String field, String label, String text) {}

    /** A note someone sent, with who they were: «Rui Matos, Chefe de Divisão». */
    record NoteFrom(String field, String label, String text, String author) {}

    /** The key of a note on a file. */
    static String fileField(UUID file) {
        return FILE + file;
    }

    /**
     * Writes the note, new or changed, if the person has the open task. Gives false if they do
     * not: someone else has it, nobody took it, or it was decided.
     */
    boolean put(UUID task, Person person, Note note) {
        return jdbc.sql("""
                                insert into engine.document (id, procedure_id, step_id, data, status)
                                select t.id, t.procedure_id, t.step_id,
                                       jsonb_build_object('author', cast(:author as jsonb),
                                                          'notes', jsonb_build_object(:field, cast(:note as jsonb))),
                                       :draft
                                from engine.task t
                                where t.id = :task and t.status = :open and t.assigned_user_id = :person
                                on conflict (id) do update
                                set data = jsonb_set(engine.document.data, array['notes', :field], cast(:note as jsonb)),
                                    updated_at = now()
                                where engine.document.status = :draft""")
                        .param("author", json.writeValueAsString(author(person)))
                        .param("field", note.field())
                        .param("note", json.writeValueAsString(Map.of("label", note.label(), "text", note.text())))
                        .param("draft", DRAFT)
                        .param("task", task)
                        .param("open", Tasks.OPEN)
                        .param("person", person.id())
                        .update()
                == 1;
    }

    /** Deletes the note, if the person has the open task and the note is there. */
    boolean delete(UUID task, Person person, String field) {
        return jdbc.sql("""
                                update engine.document d set data = d.data #- array['notes', :field], updated_at = now()
                                from engine.task t
                                where d.id = :task and t.id = d.id and t.status = :open and t.assigned_user_id = :person
                                  and d.status = :draft and jsonb_exists(d.data -> 'notes', :field)""")
                        .param("field", field)
                        .param("task", task)
                        .param("open", Tasks.OPEN)
                        .param("person", person.id())
                        .param("draft", DRAFT)
                        .update()
                == 1;
    }

    /** The notes of the task still being written, by whoever has it. */
    List<Note> draftOf(UUID task) {
        return notes(task, DRAFT).stream()
                .map(note -> new Note(note.field(), note.label(), note.text()))
                .toList();
    }

    /** The notes sent with the decision on the task, with their author. */
    List<NoteFrom> sentWith(UUID task) {
        return notes(task, SENT);
    }

    /** The reviewer decided: the notes go with the decision. Gives how many there are. */
    int send(UUID task) {
        jdbc.sql("update engine.document set status = :sent where id = :task and status = :draft")
                .param("sent", SENT)
                .param("task", task)
                .param("draft", DRAFT)
                .update();
        return notes(task, SENT).size();
    }

    /** The decision was undone: the notes are a draft of the reviewer again. */
    void backToDraft(UUID task) {
        jdbc.sql("update engine.document set status = :draft where id = :task and status = :sent")
                .param("draft", DRAFT)
                .param("task", task)
                .param("sent", SENT)
                .update();
    }

    /** The reviewer released the task: notes never sent are discarded. */
    void discard(UUID task) {
        jdbc.sql("delete from engine.document where id = :task and status = :draft")
                .param("task", task)
                .param("draft", DRAFT)
                .update();
    }

    // The document as it is kept
    record Stored(Author author, Map<String, Map<String, String>> notes) {}

    record Author(String id, String name, String jobTitle) {}

    private List<NoteFrom> notes(UUID task, String status) {
        return jdbc.sql("select data from engine.document where id = :task and status = :status")
                .param("task", task)
                .param("status", status)
                .query(String.class)
                .optional()
                .map(data -> json.readValue(data, Stored.class))
                .map(stored -> {
                    String from =
                            stored.author().name() + ", " + stored.author().jobTitle();
                    List<NoteFrom> notes = new ArrayList<>();
                    stored.notes()
                            .forEach((field, note) ->
                                    notes.add(new NoteFrom(field, note.get("label"), note.get("text"), from)));
                    return notes;
                })
                .orElse(List.of());
    }

    private static Author author(Person person) {
        return new Author(person.id(), person.name(), person.jobTitle());
    }
}
