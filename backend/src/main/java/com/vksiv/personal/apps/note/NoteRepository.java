package com.vksiv.personal.apps.note;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Scoped by user so one account can never read another's note by guessing an id. */
    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
