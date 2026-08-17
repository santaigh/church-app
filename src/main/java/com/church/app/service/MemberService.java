package com.church.app.service;

import com.church.app.dto.MemberExtForm;
import com.church.app.dto.MemberForm;
import com.church.app.dto.PageView;
import com.church.app.entity.Anbiyam;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Family;
import com.church.app.entity.FamilyRole;
import com.church.app.entity.Member;
import com.church.app.entity.MemberExt;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.entity.Role;
import com.church.app.entity.RoleLevel;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberExtRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.RoleRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import com.church.app.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Members of the parish in scope, and the extra detail hanging off them.
 *
 * <p>Adding a member creates an account: credentials live on {@code member} in this
 * schema, so a new row needs a role and a password. Both are assigned here -- the
 * configured default password with {@code password_flag = 0}, so the person is forced to
 * choose their own at first sign-in.
 */
@Service
public class MemberService {

    /** Used when no role is chosen -- an ordinary parishioner. */
    private static final String DEFAULT_ROLE = "AppUser";

    /** The only parish role an AppAdmin may not hand out. */
    private static final String SUPER_ADMIN_ROLE = "AppSA";

    private final MemberRepository memberRepository;
    private final MemberExtRepository memberExtRepository;
    private final FamilyRepository familyRepository;
    private final AnbiyamRepository anbiyamRepository;
    private final ChurchRepository churchRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final AuditService auditService;

    public MemberService(MemberRepository memberRepository,
                         MemberExtRepository memberExtRepository,
                         FamilyRepository familyRepository,
                         AnbiyamRepository anbiyamRepository,
                         ChurchRepository churchRepository,
                         RoleRepository roleRepository,
                         PasswordService passwordService,
                         PhoneNumberNormalizer phoneNumberNormalizer,
                         AuditService auditService) {
        this.memberRepository = memberRepository;
        this.memberExtRepository = memberExtRepository;
        this.familyRepository = familyRepository;
        this.anbiyamRepository = anbiyamRepository;
        this.churchRepository = churchRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.auditService = auditService;
    }

    /**
     * @param dateOfBirth carried so the screen can show an age. Age itself is never
     *                    stored or passed around -- it is derived where it is displayed,
     *                    because a stored age is wrong the next day
     */
    public record MemberRow(Long id,
                            String name,
                            Long familyId,
                            String familyName,
                            String familyCode,
                            Long anbiyamId,
                            String anbiyamName,
                            String familyRole,
                            LocalDate dateOfBirth,
                            String mobile) {
    }

    public record MemberDetail(Long id,
                               String name,
                               String gender,
                               LocalDate dateOfBirth,
                               String mobile,
                               String alternateMobile,
                               String email,
                               String familyName,
                               String familyCode,
                               String anbiyamName,
                               String familyRole,
                               String roleCode,
                               String remarks,
                               boolean hasExtraDetail) {
    }

    public record Option(Long id, String label) {
    }

    // ------------------------------------------------------------------- reads

    /**
     * Members of the parish, optionally narrowed to one family or one anbiyam.
     *
     * <p>Filtering here rather than in the browser: clicking a family on the list is
     * meant to answer "who is in this family", and that has to stay true once a parish
     * has more members than fit on a page.
     */
    /** The column searches a member list may be narrowed by. All optional. */
    public record MemberSearch(String name,
                               String family,
                               FamilyRole familyRole,
                               String anbiyam,
                               String mobile) {

        static MemberSearch empty() {
            return new MemberSearch(null, null, null, null, null);
        }

        public boolean isActive() {
            return name != null || family != null || familyRole != null
                    || anbiyam != null || mobile != null;
        }
    }

    /**
     * One page of members, searched in the database rather than in the browser.
     *
     * <p>The search covers every member of the parish and the page is cut from the
     * results. Searching only the rows already on screen would answer "no such person"
     * for someone on page 39.
     */
    @Transactional(readOnly = true)
    public PageView<MemberRow> list(Long familyId, Long anbiyamId, MemberSearch search,
                                    int page, int size) {
        MemberSearch criteria = search == null ? MemberSearch.empty() : search;

        Page<Member> found = memberRepository.search(
                currentChurchId(),
                familyId,
                anbiyamId,
                criteria.familyRole(),
                lower(criteria.name()),
                lower(criteria.family()),
                lower(criteria.anbiyam()),
                trimToNull(criteria.mobile()),
                PageRequest.of(Math.max(page, 1) - 1, size));

        return PageView.of(found, found.getContent().stream()
                .map(member -> new MemberRow(
                        member.getId(),
                        member.getDisplayName(),
                        member.getFamily().getId(),
                        member.getFamily().getFamilyName(),
                        member.getFamily().getFamilyCode(),
                        member.getAnbiyam().getId(),
                        member.getAnbiyam().getAnbiyamName(),
                        member.getFamilyRole() == null ? null : member.getFamilyRole().getLabel(),
                        member.getDateOfBirth(),
                        member.getMobile()))
                .toList());
    }

    private static String lower(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    /** The label for whatever the list is currently narrowed to, if anything. */
    @Transactional(readOnly = true)
    public String filterLabel(Long familyId, Long anbiyamId) {
        if (familyId != null) {
            return familyRepository.findById(familyId)
                    .map(family -> family.getFamilyName() + " (" + family.getFamilyCode() + ")")
                    .orElse(null);
        }
        if (anbiyamId != null) {
            return anbiyamRepository.findById(anbiyamId)
                    .map(Anbiyam::getAnbiyamName)
                    .orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public MemberDetail detail(Long id) {
        Member member = load(id);
        return new MemberDetail(
                member.getId(),
                member.getDisplayName(),
                member.getGender(),
                member.getDateOfBirth(),
                member.getMobile(),
                member.getAlternateMobile(),
                member.getEmail(),
                member.getFamily().getFamilyName(),
                member.getFamily().getFamilyCode(),
                member.getAnbiyam().getAnbiyamName(),
                member.getFamilyRole() == null ? null : member.getFamilyRole().getLabel(),
                member.getRole().getRoleCode(),
                member.getRemarks(),
                memberExtRepository.findByMemberIdAndDeletedFlagFalse(id).isPresent());
    }

    /** The extra detail, or empty when none was ever recorded for this member. */
    @Transactional(readOnly = true)
    public Optional<MemberExt> extraDetail(Long memberId) {
        // load() first: it is tenant-aware, so this cannot be used to read another
        // parish's member_ext row by guessing an id.
        load(memberId);
        return memberExtRepository.findByMemberIdAndDeletedFlagFalse(memberId);
    }

    /** Blank when the member has no extra record yet -- most do not. */
    @Transactional(readOnly = true)
    public MemberExtForm extraDetailForm(Long memberId) {
        MemberExtForm form = new MemberExtForm();
        extraDetail(memberId).ifPresent(ext -> {
            form.setBloodGroup(ext.getBloodGroup());
            form.setMaritalStatus(ext.getMaritalStatus());
            form.setAddressLine1(ext.getAddressLine1());
            form.setAddressLine2(ext.getAddressLine2());
            form.setCity(ext.getCity());
            form.setDistrict(ext.getDistrict());
            form.setState(ext.getState());
            form.setPincode(ext.getPincode());
            form.setOccupation(ext.getOccupation());
            form.setEducation(ext.getEducation());
            form.setNativePlace(ext.getNativePlace());
            form.setBaptismDate(ext.getBaptismDate());
            form.setBaptismPlace(ext.getBaptismPlace());
            form.setHolyCommunionDate(ext.getHolyCommunionDate());
            form.setHolyCommunionPlace(ext.getHolyCommunionPlace());
            form.setConfirmationDate(ext.getConfirmationDate());
            form.setConfirmationPlace(ext.getConfirmationPlace());
            form.setMarriageDate(ext.getMarriageDate());
            form.setMarriagePlace(ext.getMarriagePlace());
        });
        return form;
    }

    /**
     * Saves the extra detail, creating the row the first time.
     *
     * <p>{@code member_ext} is written for most members only when someone first fills the
     * form in, so this has to insert as readily as it updates. The church and family come
     * from the member, never from the form.
     */
    @Transactional
    public void saveExtraDetail(Long memberId, MemberExtForm form) {
        Member member = load(memberId);

        MemberExt ext = memberExtRepository.findByMemberIdAndDeletedFlagFalse(memberId)
                .orElseGet(() -> {
                    MemberExt fresh = new MemberExt();
                    fresh.setUuid(UUID.randomUUID().toString());
                    fresh.setMemberId(memberId);
                    fresh.setChurchId(member.getChurch().getId());
                    fresh.setFamilyId(member.getFamily().getId());
                    fresh.setRecordStatus("ACTIVE");
                    return fresh;
                });

        ext.setBloodGroup(trimToNull(form.getBloodGroup()));
        ext.setMaritalStatus(trimToNull(form.getMaritalStatus()));
        ext.setAddressLine1(trimToNull(form.getAddressLine1()));
        ext.setAddressLine2(trimToNull(form.getAddressLine2()));
        ext.setCity(trimToNull(form.getCity()));
        ext.setDistrict(trimToNull(form.getDistrict()));
        ext.setState(trimToNull(form.getState()));
        ext.setPincode(trimToNull(form.getPincode()));
        ext.setOccupation(trimToNull(form.getOccupation()));
        ext.setEducation(trimToNull(form.getEducation()));
        ext.setNativePlace(trimToNull(form.getNativePlace()));
        ext.setBaptismDate(form.getBaptismDate());
        ext.setBaptismPlace(trimToNull(form.getBaptismPlace()));
        ext.setHolyCommunionDate(form.getHolyCommunionDate());
        ext.setHolyCommunionPlace(trimToNull(form.getHolyCommunionPlace()));
        ext.setConfirmationDate(form.getConfirmationDate());
        ext.setConfirmationPlace(trimToNull(form.getConfirmationPlace()));
        ext.setMarriageDate(form.getMarriageDate());
        ext.setMarriagePlace(trimToNull(form.getMarriagePlace()));

        memberExtRepository.save(ext);
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, member, null);
    }

    @Transactional(readOnly = true)
    public MemberForm formFor(Long id) {
        Member member = load(id);
        MemberForm form = new MemberForm();
        form.setId(member.getId());
        form.setFirstName(member.getFirstName());
        form.setMiddleName(member.getMiddleName());
        form.setLastName(member.getLastName());
        form.setGender(member.getGender());
        form.setDateOfBirth(member.getDateOfBirth());
        form.setMobile(member.getMobile());
        form.setAlternateMobile(member.getAlternateMobile());
        form.setEmail(member.getEmail());
        form.setFamilyId(member.getFamily().getId());
        form.setAnbiyamId(member.getAnbiyam().getId());
        form.setFamilyRole(member.getFamilyRole());
        form.setRemarks(member.getRemarks());
        form.setRoleId(member.getRole().getId());
        return form;
    }

    @Transactional(readOnly = true)
    public List<Option> families() {
        return familyRepository.findByChurchIdAndDeletedFlagFalse(currentChurchId()).stream()
                .map(family -> new Option(family.getId(),
                        family.getFamilyName() + " (" + family.getFamilyCode() + ")"))
                .sorted(java.util.Comparator.comparing(Option::label))
                .toList();
    }

    /**
     * The parish roles the signed-in account may hand out.
     *
     * <p>Platform staff and AppSA may grant anything; an AppAdmin may grant every parish
     * role except AppSA. Otherwise an AppAdmin could create a super-admin account and
     * sign in as it, which is promotion by two clicks.
     */
    @Transactional(readOnly = true)
    public List<Option> assignableRoles() {
        return roleRepository.findByRoleLevelAndDeletedFlagFalse(RoleLevel.APP).stream()
                .filter(role -> mayGrant(role.getRoleCode()))
                .map(role -> new Option(role.getId(), role.getRoleName() + " (" + role.getRoleCode() + ")"))
                .toList();
    }

    private boolean mayGrant(String roleCode) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal == null || principal.isPlatformUser()) {
            return true;
        }
        if (SUPER_ADMIN_ROLE.equals(principal.getRoleCode())) {
            return true;
        }
        return !SUPER_ADMIN_ROLE.equals(roleCode);
    }

    @Transactional(readOnly = true)
    public List<Option> anbiyams() {
        return anbiyamRepository
                .findByChurchIdAndDeletedFlagFalseOrderByAnbiyamNameAsc(currentChurchId()).stream()
                .map(anbiyam -> new Option(anbiyam.getId(), anbiyam.getAnbiyamName()))
                .toList();
    }

    // ------------------------------------------------------------------ writes

    @Transactional
    public Member create(MemberForm form) {
        Long churchId = currentChurchId();
        Family family = family(form.getFamilyId());
        Anbiyam anbiyam = anbiyam(form.getAnbiyamId());

        Member member = new Member();
        member.setUuid(UUID.randomUUID().toString());
        member.setChurch(churchRepository.findById(churchId)
                .orElseThrow(() -> new ResourceNotFoundException("Church", churchId)));
        member.setRole(roleFor(form.getRoleId(), null));
        // Credentials live on member in this schema, so a new member is a new account.
        member.setMemberPassword(passwordService.encodedDefaultPassword());
        member.setPasswordFlag(false);
        member.setRecordStatus("ACTIVE");

        apply(form, member, family, anbiyam);
        Member saved = memberRepository.save(member);

        applyHeadRule(saved, family);
        saveExtraIfProvided(saved, form.getExtra());
        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved, null);
        auditRoleAssignment(saved, null);
        return saved;
    }

    @Transactional
    public Member update(Long id, MemberForm form) {
        Member member = load(id);
        Family family = family(form.getFamilyId());
        Anbiyam anbiyam = anbiyam(form.getAnbiyamId());

        String before = member.getDisplayName();
        String roleBefore = member.getRole().getRoleCode();
        member.setRole(roleFor(form.getRoleId(), member));
        apply(form, member, family, anbiyam);
        Member saved = memberRepository.save(member);

        applyHeadRule(saved, family);
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, saved, before);
        if (!roleBefore.equals(saved.getRole().getRoleCode())) {
            auditRoleAssignment(saved, roleBefore);
        }
        return saved;
    }

    /** Soft delete: payments, dues and family history all still point at this row. */
    @Transactional
    public void delete(Long id) {
        Member member = load(id);

        AppUserPrincipal principal = CurrentUser.principalOrNull();
        if (principal != null && !principal.isPlatformUser()
                && member.getId().equals(principal.getUserId())) {
            throw new BusinessException("You cannot remove your own account.");
        }

        member.setDeletedFlag(true);
        member.setLastUpdatedDate(LocalDateTime.now());
        memberRepository.save(member);

        // A deleted member must not stay on record as the head of a family.
        Family family = member.getFamily();
        if (member.getId().equals(family.getHeadMemberId())) {
            family.setHeadMemberId(null);
            familyRepository.save(family);
        }

        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, member, null);
    }

    // --------------------------------------------------------------- internals

    private void apply(MemberForm form, Member member, Family family, Anbiyam anbiyam) {
        member.setFirstName(form.getFirstName().trim());
        member.setMiddleName(trimToNull(form.getMiddleName()));
        member.setLastName(trimToNull(form.getLastName()));
        member.setGender(form.getGender());
        member.setDateOfBirth(form.getDateOfBirth());
        member.setMobile(normalise(form.getMobile()));
        member.setAlternateMobile(normalise(form.getAlternateMobile()));
        member.setEmail(trimToNull(form.getEmail()));
        member.setFamily(family);
        member.setAnbiyam(anbiyam);
        member.setFamilyRole(form.getFamilyRole());
        member.setRemarks(trimToNull(form.getRemarks()));
    }

    /**
     * One head per family, recorded in two places that must agree.
     *
     * <p>{@code family.head_member_id} is the source of truth and the member's own role
     * follows it, so naming a new head demotes the previous one and moves the pointer in
     * the same action. Left to drift, one screen would say one thing and another the
     * opposite -- the failure this schema has already produced three times.
     */
    private void applyHeadRule(Member member, Family family) {
        if (member.getFamilyRole() != FamilyRole.HEAD) {
            // Standing down: the family loses its head until another is named.
            if (member.getId().equals(family.getHeadMemberId())) {
                family.setHeadMemberId(null);
                familyRepository.save(family);
            }
            return;
        }

        memberRepository.findByChurchIdAndDeletedFlagFalse(member.getChurch().getId()).stream()
                .filter(other -> other.getFamily().getId().equals(family.getId()))
                .filter(other -> !other.getId().equals(member.getId()))
                .filter(other -> other.getFamilyRole() == FamilyRole.HEAD)
                .forEach(previous -> {
                    previous.setFamilyRole(null);
                    memberRepository.save(previous);
                });

        family.setHeadMemberId(member.getId());
        familyRepository.save(family);
    }

    private Member load(Long id) {
        return memberRepository.findById(id)
                .filter(member -> !member.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Member", id));
    }

    /** Tenant-aware: a family id from another parish simply does not resolve. */
    private Family family(Long familyId) {
        return familyRepository.findById(familyId)
                .filter(family -> !family.isDeletedFlag())
                .orElseThrow(() -> new BusinessException("That family is not in this parish."));
    }

    private Anbiyam anbiyam(Long anbiyamId) {
        return anbiyamRepository.findById(anbiyamId)
                .filter(anbiyam -> !anbiyam.isDeletedFlag())
                .orElseThrow(() -> new BusinessException("That anbiyam is not in this parish."));
    }

    /**
     * Resolves the chosen role, refusing the two ways this field can be abused.
     *
     * @param existing the member being edited, or null when creating
     */
    private Role roleFor(Long roleId, Member existing) {
        if (roleId == null) {
            return existing != null ? existing.getRole() : defaultRole();
        }

        Role role = roleRepository.findById(roleId)
                .filter(candidate -> candidate.getRoleLevel() == RoleLevel.APP)
                .orElseThrow(() -> new BusinessException("That is not a parish role."));

        AppUserPrincipal principal = CurrentUser.principalOrNull();
        boolean unchanged = existing != null && existing.getRole().getId().equals(roleId);

        // Editing your own role: otherwise the rule below is one save away from useless.
        if (!unchanged && principal != null && !principal.isPlatformUser()
                && existing != null && existing.getId().equals(principal.getUserId())) {
            throw new BusinessException("You cannot change your own role.");
        }

        // The dropdown already omits what this account may not grant; a posted id is not
        // the dropdown.
        if (!unchanged && !mayGrant(role.getRoleCode())) {
            throw new BusinessException(
                    "You may not grant the " + role.getRoleCode() + " role.");
        }
        return role;
    }

    private Role defaultRole() {
        return roleRepository.findByRoleCode(DEFAULT_ROLE)
                .orElseThrow(() -> new BusinessException("The " + DEFAULT_ROLE + " role is missing."));
    }

    /** Writes nothing when every field was left blank -- most members have no extra row. */
    private void saveExtraIfProvided(Member member, MemberExtForm extra) {
        if (extra == null || isBlank(extra)) {
            return;
        }
        saveExtraDetail(member.getId(), extra);
    }

    private static boolean isBlank(MemberExtForm extra) {
        return trimToNull(extra.getBloodGroup()) == null
                && trimToNull(extra.getMaritalStatus()) == null
                && trimToNull(extra.getAddressLine1()) == null
                && trimToNull(extra.getAddressLine2()) == null
                && trimToNull(extra.getCity()) == null
                && trimToNull(extra.getDistrict()) == null
                && trimToNull(extra.getState()) == null
                && trimToNull(extra.getPincode()) == null
                && trimToNull(extra.getOccupation()) == null
                && trimToNull(extra.getEducation()) == null
                && trimToNull(extra.getNativePlace()) == null
                && extra.getBaptismDate() == null && trimToNull(extra.getBaptismPlace()) == null
                && extra.getHolyCommunionDate() == null && trimToNull(extra.getHolyCommunionPlace()) == null
                && extra.getConfirmationDate() == null && trimToNull(extra.getConfirmationPlace()) == null
                && extra.getMarriageDate() == null && trimToNull(extra.getMarriagePlace()) == null;
    }

    /** Its own audit event: granting a role is granting a way in. */
    private void auditRoleAssignment(Member member, String previousRole) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(AuditEventType.ROLE_ASSIGNED)
                .on("Member", member.getId(), member.getDisplayName())
                .inChurch(member.getChurch().getId())
                .describe(member.getDisplayName() + " now holds " + member.getRole().getRoleCode());

        if (previousRole != null) {
            entry.changed(previousRole, member.getRole().getRoleCode());
        }
        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        entry.save();
    }

    private String normalise(String number) {
        String trimmed = trimToNull(number);
        if (trimmed == null) {
            return null;
        }
        return phoneNumberNormalizer.looksLikePhoneNumber(trimmed)
                ? phoneNumberNormalizer.normalize(trimmed)
                : trimmed;
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to read or write."));
    }

    private void audit(AuditEventType eventType, Operation operation, Member member, String before) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(eventType)
                .on("Member", member.getId(), member.getDisplayName())
                .inChurch(member.getChurch().getId())
                .permission(Resource.MEMBER, operation);

        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        if (before != null && !before.equals(member.getDisplayName())) {
            entry.changed(before, member.getDisplayName());
        }
        entry.save();
    }

    /**
     * The declaration order of {@link FamilyRole} is the display order. Members with no
     * role recorded sort last rather than first -- a blank is not a position.
     */
    private static int roleOrder(Member member) {
        return member.getFamilyRole() == null
                ? FamilyRole.values().length
                : member.getFamilyRole().ordinal();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
