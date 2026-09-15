package com.vksiv.personal.apps.note;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vksiv.personal.apps.common.ApiExceptions.NotFoundException;
import com.vksiv.personal.apps.note.NoteDtos.NoteRequest;
import com.vksiv.personal.apps.note.NoteDtos.NoteResponse;

@Service
public class NoteService {

    private final NoteRepository notes;

    public NoteService(NoteRepository notes) {
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listFor(UUID userId) {
        return notes.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NoteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse get(UUID id, UUID userId) {
        return notes.findByIdAndUserId(id, userId)
                .map(NoteResponse::from)
                .orElseThrow(() -> new NotFoundException("No note " + id));
    }

    @Transactional
    public NoteResponse create(NoteRequest request, UUID userId) {
        Note note = notes.save(new Note(userId, request.title().trim(), request.body()));
        return NoteResponse.from(note);
    }

    @Transactional
    public NoteResponse update(UUID id, NoteRequest request, UUID userId) {
        Note note = notes.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("No note " + id));
        note.update(request.title().trim(), request.body());
        return NoteResponse.from(notes.save(note));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        if (notes.deleteByIdAndUserId(id, userId) == 0) {
            throw new NotFoundException("No note " + id);
        }
    }
}
