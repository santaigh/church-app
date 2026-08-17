package com.church.app.service;

import com.church.app.dto.ParishPriestForm;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Church;
import com.church.app.entity.ClergyRole;
import com.church.app.entity.Operation;
import com.church.app.entity.ParishPriest;
import com.church.app.entity.Resource;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.ParishPriestRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Appointments of clergy to the parish in scope.
 *
 * <p>An appointment history, not a roster: a priest who moves on leaves a closed row
 * behind him. {@code toDate == null} means currently serving, and it is the only
 * definition -- see {@link ParishPriest}.
 */
@Service
public class ParishPriestService {

    private final ParishPriestRepository parishPriestRepository;
    private final ChurchRepository churchRepository;
    private final AuditService auditService;

    public ParishPriestService(ParishPriestRepository parishPriestRepository,
                               ChurchRepository churchRepository,
                               AuditService auditService) {
        this.parishPriestRepository = parishPriestRepository;
        this.churchRepository = churchRepository;
        this.auditService = auditService;
    }

    /** One row of the history. */
    public record PostingRow(Long id,
                             String role,
                             ClergyRole roleValue,
                             String name,
                             String lastPlace,
                             LocalDate from,
                             LocalDate to,
                             boolean current) {
    }

    @Transactional(readOnly = true)
    public List<PostingRow> history() {
        return parishPriestRepository
                .findByChurchIdAndDeletedFlagFalseOrderByFromDateDesc(currentChurchId())
                .stream()
                .map(posting -> new PostingRow(
                        posting.getId(),
                        posting.getClergyRole().getLabel(),
                        posting.getClergyRole(),
                        posting.getPriestName(),
                        posting.getPriestLastPlace(),
                        posting.getFromDate().toLocalDate(),
                        posting.getToDate() == null ? null : posting.getToDate().toLocalDate(),
                        posting.isCurrentlyServing()))
                .toList();
    }

    /**
     * True when no parish priest is serving.
     *
     * <p>Not an error: real parishes have gaps between postings. The screen warns rather
     * than refusing, which is the decision recorded for this module.
     */
    @Transactional(readOnly = true)
    public boolean hasNoParishPriest() {
        return parishPriestRepository
                .findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        currentChurchId(), ClergyRole.PARISH_PRIEST)
                .isEmpty();
    }

    @Transactional(readOnly = true)
    public ParishPriestForm formFor(Long id) {
        ParishPriest posting = load(id);
        ParishPriestForm form = new ParishPriestForm();
        form.setId(posting.getId());
        form.setClergyRole(posting.getClergyRole());
        form.setPriestName(posting.getPriestName());
        form.setPriestLastPlace(posting.getPriestLastPlace());
        form.setFromDate(posting.getFromDate().toLocalDate());
        form.setToDate(posting.getToDate() == null ? null : posting.getToDate().toLocalDate());
        return form;
    }

    @Transactional
    public ParishPriest appoint(ParishPriestForm form) {
        Long churchId = currentChurchId();
        rejectBackwardsDates(form);

        ParishPriest posting = new ParishPriest();
        posting.setUuid(UUID.randomUUID().toString());
        posting.setChurch(churchReference(churchId));
        apply(form, posting);

        // A new parish priest closes the one before him, so a church can never show two.
        // Assistants and brothers must not close each other: a parish may have several.
        if (posting.getClergyRole() == ClergyRole.PARISH_PRIEST && posting.getToDate() == null) {
            closeOpenParishPriest(churchId, posting.getFromDate(), null);
        }

        ParishPriest saved = parishPriestRepository.save(posting);
        syncDenormalisedName(churchId);
        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved, null);
        return saved;
    }

    @Transactional
    public ParishPriest update(Long id, ParishPriestForm form) {
        ParishPriest posting = load(id);
        rejectBackwardsDates(form);

        String before = posting.getPriestName();
        apply(form, posting);

        if (posting.getClergyRole() == ClergyRole.PARISH_PRIEST && posting.getToDate() == null) {
            closeOpenParishPriest(posting.getChurch().getId(), posting.getFromDate(), id);
        }

        ParishPriest saved = parishPriestRepository.save(posting);
        syncDenormalisedName(posting.getChurch().getId());
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, saved, before);
        return saved;
    }

    /** Soft delete: a posting that happened stays on the record. */
    @Transactional
    public void delete(Long id) {
        ParishPriest posting = load(id);
        posting.setDeletedFlag(true);
        posting.setLastUpdatedDate(LocalDateTime.now());
        parishPriestRepository.save(posting);
        syncDenormalisedName(posting.getChurch().getId());
        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, posting, null);
    }

    // ---------------------------------------------------------------- internals

    private void apply(ParishPriestForm form, ParishPriest posting) {
        posting.setClergyRole(form.getClergyRole());
        posting.setPriestName(form.getPriestName().trim());
        posting.setPriestLastPlace(trimToNull(form.getPriestLastPlace()));
        posting.setFromDate(form.getFromDate().atStartOfDay());
        posting.setToDate(form.getToDate() == null ? null : form.getToDate().atStartOfDay());
    }

    private void rejectBackwardsDates(ParishPriestForm form) {
        if (form.getToDate() != null && form.getToDate().isBefore(form.getFromDate())) {
            throw new BusinessException("The end date cannot be before the start date.");
        }
    }

    /**
     * Closes whichever parish priest is currently open, at the incoming start date.
     *
     * @param exceptId the posting being edited, which must not close itself
     */
    private void closeOpenParishPriest(Long churchId, LocalDateTime from, Long exceptId) {
        Optional<ParishPriest> open = parishPriestRepository
                .findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        churchId, ClergyRole.PARISH_PRIEST);

        open.filter(current -> !current.getId().equals(exceptId))
                .ifPresent(current -> {
                    current.setToDate(from);
                    parishPriestRepository.save(current);
                });
    }

    /**
     * Keeps {@code church.parish_priest} matching the open posting.
     *
     * <p>That column is a denormalised convenience other screens read. Left alone it goes
     * stale, and then two places disagree about who the priest is.
     */
    private void syncDenormalisedName(Long churchId) {
        String current = parishPriestRepository
                .findFirstByChurchIdAndClergyRoleAndToDateIsNullAndDeletedFlagFalse(
                        churchId, ClergyRole.PARISH_PRIEST)
                .map(ParishPriest::getPriestName)
                .orElse(null);

        churchRepository.findById(churchId).ifPresent(church -> {
            church.setParishPriest(current);
            churchRepository.save(church);
        });
    }

    private ParishPriest load(Long id) {
        return parishPriestRepository.findById(id)
                .filter(posting -> !posting.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Parish priest", id));
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to read or write."));
    }

    private Church churchReference(Long churchId) {
        return churchRepository.findById(churchId)
                .orElseThrow(() -> new ResourceNotFoundException("Church", churchId));
    }

    private void audit(AuditEventType eventType, Operation operation, ParishPriest posting, String before) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(eventType)
                .on("ParishPriest", posting.getId(), posting.getPriestName())
                .inChurch(posting.getChurch().getId())
                .permission(Resource.PARISH_PRIEST, operation);

        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        if (before != null && !before.equals(posting.getPriestName())) {
            entry.changed(before, posting.getPriestName());
        }
        entry.save();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
