package app.arete.arete_api.controller;

import app.arete.arete_api.api.ProgramPartApi;
import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramPartRequestDto;
import app.arete.arete_api.api.model.ProgramPartResponseDto;
import app.arete.arete_api.api.model.ReorderProgramPartsRequestDto;
import app.arete.arete_api.service.ProgramPartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ProgramPartController implements ProgramPartApi {

    private final ProgramPartService programPartService;

    @Override
    public ResponseEntity<ProgramPartResponseDto> createProgramPart(
            final Long programId,
            final LocalDate date,
            final ProgramPartRequestDto programPartRequestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(programPartService.createUserProgramPart(programId, date, programPartRequestDto));
    }

    @Override
    public ResponseEntity<ProgramPartResponseDto> updateProgramPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final ProgramPartRequestDto programPartRequestDto) {
        return ResponseEntity.ok(
                programPartService.updateUserProgramPart(programId, date, partId, programPartRequestDto));
    }

    @Override
    public ResponseEntity<Void> deleteProgramPart(final Long programId, final LocalDate date, final Long partId) {
        programPartService.deleteUserProgramPart(programId, date, partId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProgramDayResponseDto> reorderProgramParts(
            final Long programId,
            final LocalDate date,
            final ReorderProgramPartsRequestDto reorderProgramPartsRequestDto) {
        return ResponseEntity.ok(
                programPartService.reorderUserProgramParts(programId, date, reorderProgramPartsRequestDto));
    }
}
