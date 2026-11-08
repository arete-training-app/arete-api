package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.ProgramDayResponseDto;
import app.arete.arete_api.api.model.ProgramPartRequestDto;
import app.arete.arete_api.api.model.ProgramPartResponseDto;
import app.arete.arete_api.api.model.ReorderProgramPartsRequestDto;
import app.arete.arete_api.config.properties.ProgramProperties;
import app.arete.arete_api.exception.ConflictException;
import app.arete.arete_api.mapper.ProgramDayMapper;
import app.arete.arete_api.mapper.ProgramPartMapper;
import app.arete.arete_api.model.entity.ProgramDay;
import app.arete.arete_api.model.entity.ProgramPart;
import app.arete.arete_api.repository.ProgramDayRepository;
import app.arete.arete_api.service.ProgramDayService;
import app.arete.arete_api.service.ProgramPartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPartServiceImpl implements ProgramPartService {

    private final ProgramDayRepository programDayRepository;
    private final ProgramDayService programDayService;
    private final ProgramPartMapper programPartMapper;
    private final ProgramDayMapper programDayMapper;
    private final ProgramProperties programProperties;

    @Override
    @Transactional
    public ProgramPartResponseDto createUserProgramPart(
            final Long programId,
            final LocalDate date,
            final ProgramPartRequestDto programPartRequestDto) {
        final ProgramDay programDay = programDayService.getOrCreateUserProgramDayEntity(programId, date);
        if (programDay.getParts().size() >= programProperties.maxPartsPerDay()) {
            throw new ConflictException(
                    "A day can have at most %d program parts".formatted(programProperties.maxPartsPerDay()));
        }
        programDay.addPart(programPartMapper.toProgramPart(programPartRequestDto));
        final ProgramDay savedProgramDay = programDayRepository.saveAndFlush(programDay);
        final ProgramPart createdPart = getLastPart(savedProgramDay);
        log.info("Program {}: part {} '{}' added to {}", programId, createdPart.getId(), createdPart.getTitle(), date);
        return programPartMapper.toProgramPartResponseDto(createdPart);
    }

    @Override
    @Transactional
    public ProgramPartResponseDto updateUserProgramPart(
            final Long programId,
            final LocalDate date,
            final Long partId,
            final ProgramPartRequestDto programPartRequestDto) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        final ProgramPart programPart = programDay.updatePart(
                partId,
                programPartRequestDto.getTitle(),
                programPartRequestDto.getDescription(),
                programPartRequestDto.getDurationMinutes());
        programDayRepository.saveAndFlush(programDay);
        log.info("Program {}: part {} on {} updated: '{}'", programId, partId, date, programPart.getTitle());
        return programPartMapper.toProgramPartResponseDto(programPart);
    }

    @Override
    @Transactional
    public void deleteUserProgramPart(final Long programId, final LocalDate date, final Long partId) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        programDay.removePart(partId);
        if (programDay.getParts().isEmpty()) {
            programDayRepository.delete(programDay);
            log.info("Program {}: part {} deleted; it was the last part, so {} is now a rest day",
                    programId, partId, date);
            return;
        }
        programDayRepository.save(programDay);
        log.info("Program {}: part {} on {} deleted", programId, partId, date);
    }

    @Override
    @Transactional
    public ProgramDayResponseDto reorderUserProgramParts(
            final Long programId,
            final LocalDate date,
            final ReorderProgramPartsRequestDto reorderProgramPartsRequestDto) {
        final ProgramDay programDay = programDayService.getUserProgramDayEntity(programId, date);
        programDay.reorderParts(reorderProgramPartsRequestDto.getPartIds());
        final ProgramDay savedProgramDay = programDayRepository.saveAndFlush(programDay);
        log.info("Program {}: parts on {} reordered to {}", programId, date, reorderProgramPartsRequestDto.getPartIds());
        return programDayMapper.toProgramDayResponseDto(savedProgramDay);
    }

    private static ProgramPart getLastPart(final ProgramDay programDay) {
        return programDay.getParts().stream()
                .max(Comparator.comparingInt(ProgramPart::getOrderIndex))
                .orElseThrow();
    }
}
