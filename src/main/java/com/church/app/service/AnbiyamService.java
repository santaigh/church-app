package com.church.app.service;

import com.church.app.dto.AnbiyamForm;
import com.church.app.entity.Anbiyam;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Church;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The anbiyam of the parish the request belongs to.
 *
 * <p>This is the first module that writes, so it sets the pattern the rest copy: the
 * church is stamped from the tenant scope rather than taken from the form, deletes are
 * soft, and every change is audited.
 */
@Service
public class AnbiyamService {

    private final AnbiyamRepository anbiyamRepository;
    private final ChurchRepository churchRepository;
    private final MemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final AuditService auditService;

    public AnbiyamService(AnbiyamRepository anbiyamRepository,
                          ChurchRepository churchRepository,
                          MemberRepository memberRepository,
                          FamilyRepository familyRepository,
                          AuditService auditService) {
        this.anbiyamRepository = anbiyamRepository;
        this.churchRepository = churchRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.auditService = auditService;
    }

    /**
     * One row of the list: the anbiyam, its animator, and what belongs to it.
     *
     * @param animatorId shown next to the name because parish records refer to members by
     *                   number, and two parishioners may share a name
     */
    public record AnbiyamRow(Long id,
                             String name,
                             String area,
                             Long animatorId,
                             String animator,
                             boolean active,
                             long families,
                             long members) {
    }

    /** A member of this parish, for the animator dropdown. */
    public record MemberOption(Long id, String name) {
    }

    @Transactional(readOnly = true)
    public List<AnbiyamRow> list() {
        return anbiyamRepository
                .findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(currentChurchId())
                .stream()
                .map(anbiyam -> new AnbiyamRow(
                        anbiyam.getId(),
                        anbiyam.getAnbiyamName(),
                        anbiyam.getAreaDescription(),
                        anbiyam.getHeadMemberId(),
                        animatorName(anbiyam.getHeadMemberId()),
                        anbiyam.isActiveFlag(),
                        familyRepository.countByAnbiyamIdAndDeletedFlagFalse(anbiyam.getId()),
                        memberRepository.countByAnbiyamIdAndDeletedFlagFalse(anbiyam.getId())))
                .toList();
    }

    /**
     * Who may lead this anbiyam: its own members, and nobody else.
     *
     * <p>Empty for an anbiyam that does not exist yet -- nothing can belong to it until
     * it is saved -- so the Add screen offers only "Not assigned". The head is mapped
     * afterwards, once families and members have been added.
     */
    @Transactional(readOnly = true)
    public List<MemberOption> animatorOptions(Long anbiyamId) {
        if (anbiyamId == null) {
            return List.of();
        }
        return memberRepository.findByAnbiyamIdAndDeletedFlagFalseOrderByFirstNameAsc(anbiyamId)
                .stream()
                .map(member -> new MemberOption(member.getId(), member.getDisplayName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnbiyamForm formFor(Long id) {
        Anbiyam anbiyam = load(id);
        AnbiyamForm form = new AnbiyamForm();
        form.setId(anbiyam.getId());
        form.setAnbiyamName(anbiyam.getAnbiyamName());
        form.setAreaDescription(anbiyam.getAreaDescription());
        form.setHeadMemberId(anbiyam.getHeadMemberId());
        form.setActiveFlag(anbiyam.isActiveFlag());
        return form;
    }

    @Transactional
    public Anbiyam create(AnbiyamForm form) {
        Long churchId = currentChurchId();
        rejectDuplicateName(form.getAnbiyamName(), null);

        Anbiyam anbiyam = new Anbiyam();
        anbiyam.setUuid(UUID.randomUUID().toString());
        // Stamped from the tenant scope. The form has no church field at all, so there is
        // nothing a browser could tamper with.
        anbiyam.setChurch(churchReference(churchId));
        // Null id: the anbiyam does not exist yet, so no member can belong to it.
        apply(form, anbiyam, null);
        anbiyam.setRecordStatus("ACTIVE");

        Anbiyam saved = anbiyamRepository.save(anbiyam);
        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved, null);
        return saved;
    }

    @Transactional
    public Anbiyam update(Long id, AnbiyamForm form) {
        Anbiyam anbiyam = load(id);
        rejectDuplicateName(form.getAnbiyamName(), id);

        String before = anbiyam.getAnbiyamName();
        apply(form, anbiyam, id);

        Anbiyam saved = anbiyamRepository.save(anbiyam);
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, saved, before);
        return saved;
    }

    /**
     * Soft delete: the row stops appearing but its history stays, because families and
     * members still point at it.
     */
    @Transactional
    public void delete(Long id) {
        Anbiyam anbiyam = load(id);

        // family.anbiyam_id is NOT NULL, so removing an anbiyam that still has families
        // would leave rows pointing at something invisible.
        long families = familyRepository.countByAnbiyamIdAndDeletedFlagFalse(id);
        long members = memberRepository.countByAnbiyamIdAndDeletedFlagFalse(id);
        if (families > 0 || members > 0) {
            throw new BusinessException(
                    "This anbiyam still has %d famil%s and %d member(s). Move them first."
                            .formatted(families, families == 1 ? "y" : "ies", members));
        }

        anbiyam.setDeletedFlag(true);
        anbiyam.setLastUpdatedDate(LocalDateTime.now());
        anbiyamRepository.save(anbiyam);
        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, anbiyam, null);
    }

    // ---------------------------------------------------------------- internals

    /**
     * Loads within the tenant scope. {@code findById} is routed through
     * {@code TenantAwareRepositoryImpl}, so an id belonging to another parish comes back
     * empty rather than loading -- a primary-key lookup would otherwise slip past the
     * Hibernate filter entirely.
     */
    private Anbiyam load(Long id) {
        return anbiyamRepository.findById(id)
                .filter(anbiyam -> !anbiyam.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Anbiyam", id));
    }

    private void apply(AnbiyamForm form, Anbiyam anbiyam, Long anbiyamId) {
        anbiyam.setAnbiyamName(form.getAnbiyamName().trim());
        anbiyam.setAreaDescription(trimToNull(form.getAreaDescription()));
        anbiyam.setHeadMemberId(validAnimator(form.getHeadMemberId(), anbiyamId));
        anbiyam.setActiveFlag(form.isActiveFlag());
    }

    /**
     * An animator must be a member of <em>this</em> anbiyam.
     *
     * <p>The dropdown only offers them, but a posted id proves nothing. Two separate
     * refusals hide behind this: a member of another parish, which the tenant-aware
     * {@code findById} will not even load, and a member of this parish who belongs to a
     * different anbiyam.
     *
     * @param anbiyamId null while creating -- nothing can belong to an anbiyam that does
     *                  not exist yet, so any animator posted at that point is refused
     */
    private Long validAnimator(Long memberId, Long anbiyamId) {
        if (memberId == null) {
            return null;
        }
        if (anbiyamId == null) {
            throw new BusinessException(
                    "An animator can only be chosen once the anbiyam has members. "
                            + "Save it first, then map the head.");
        }
        return memberRepository.findById(memberId)
                .filter(member -> !member.isDeletedFlag())
                .filter(member -> member.getAnbiyam() != null
                        && anbiyamId.equals(member.getAnbiyam().getId()))
                .map(member -> memberId)
                .orElseThrow(() -> new BusinessException(
                        "That member does not belong to this anbiyam."));
    }

    private String animatorName(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository.findById(memberId)
                .map(member -> member.getDisplayName())
                .orElse(null);
    }

    private void rejectDuplicateName(String name, Long allowedId) {
        boolean clash = anbiyamRepository
                .findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(currentChurchId())
                .stream()
                .anyMatch(existing -> existing.getAnbiyamName().equalsIgnoreCase(name.trim())
                        && !existing.getId().equals(allowedId));
        if (clash) {
            throw new BusinessException("An anbiyam with this name already exists in this parish.");
        }
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to read or write."));
    }

    private Church churchReference(Long churchId) {
        return churchRepository.findById(churchId)
                .orElseThrow(() -> new ResourceNotFoundException("Church", churchId));
    }

    private void audit(AuditEventType eventType, Operation operation, Anbiyam anbiyam, String before) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(eventType)
                .on("Anbiyam", anbiyam.getId(), anbiyam.getAnbiyamName())
                .inChurch(anbiyam.getChurch().getId())
                .permission(Resource.ANBIYAM, operation);

        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(),
                    principal.getChurchId());
        }
        if (before != null && !before.equals(anbiyam.getAnbiyamName())) {
            entry.changed(before, anbiyam.getAnbiyamName());
        }
        entry.save();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
