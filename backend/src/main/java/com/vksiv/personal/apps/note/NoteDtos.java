package com.vksiv.personal.apps.note;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class NoteDtos {

    private NoteDtos() {
    }

    public record NoteRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10_000) String body) {
    }

    public record NoteResponse(UUID id, String title, String body, Instant createdAt, Instant updatedAt) {
        static NoteResponse from(Note note) {
            return new NoteResponse(
                    note.getId(), note.getTitle(), note.getBody(), note.getCreatedAt(), note.getUpdatedAt());
        }
    }
}
