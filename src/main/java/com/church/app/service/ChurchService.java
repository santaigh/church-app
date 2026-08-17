package com.church.app.service;

import com.church.app.config.AppStorageProperties;
import com.church.app.dto.ChurchForm;
import com.church.app.entity.Anbiyam;
import com.church.app.entity.AuditEventType;
import com.church.app.entity.Church;
import com.church.app.entity.Family;
import com.church.app.entity.Member;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.entity.Role;
import com.church.app.exception.BusinessException;
import com.church.app.exception.ResourceNotFoundException;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.RoleRepository;
import com.church.app.security.AppUserPrincipal;
import com.church.app.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Creating, editing and removing a parish. Platform staff only.
 *
 * <p>A parish administering its own record is how a church quietly renames itself into
 * someone else's, so none of this is reachable from a parish account.
 */
@Service
public class ChurchService {

    private static final Logger log = LoggerFactory.getLogger(ChurchService.class);

    /** Roughly a page of A4 at print resolution -- far more than a crest needs. */
    private static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;

    private final ChurchRepository churchRepository;
    private final MemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final AnbiyamRepository anbiyamRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final AppStorageProperties storageProperties;
    private final AuditService auditService;

    public ChurchService(ChurchRepository churchRepository,
                         MemberRepository memberRepository,
                         FamilyRepository familyRepository,
                         AnbiyamRepository anbiyamRepository,
                         RoleRepository roleRepository,
                         PasswordService passwordService,
                         PhoneNumberNormalizer phoneNumberNormalizer,
                         AppStorageProperties storageProperties,
                         AuditService auditService) {
        this.churchRepository = churchRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.anbiyamRepository = anbiyamRepository;
        this.roleRepository = roleRepository;
        this.passwordService = passwordService;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.storageProperties = storageProperties;
        this.auditService = auditService;
    }

    public record SubstationRow(Long id, String name, String location, String address) {
    }

    public record RemovedChurch(Long id, String name, String town) {
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates a parish and, with it, the means to sign into it.
     *
     * <p>A member needs a family and an anbiyam, both NOT NULL, and creating either needs
     * somebody signed in to that parish. So a new church gets one placeholder anbiyam and
     * one placeholder family to hold its first administrator; the parish renames them
     * once it is in. Without that, a freshly created parish is unreachable by anyone.
     */
    @Transactional
    public Church create(ChurchForm form) {
        Church church = new Church();
        church.setUuid(UUID.randomUUID().toString());
        apply(form, church);
        church.setRecordStatus("ACTIVE");
        Church saved = churchRepository.save(church);

        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved, "Parish created");

        if (!form.getAdministrator().isEmpty()) {
            createFirstAdministrator(saved, form.getAdministrator());
        }
        return saved;
    }

    private void createFirstAdministrator(Church church, ChurchForm.FirstAdministrator details) {
        if (details.getFirstName() == null || details.getFirstName().isBlank()) {
            throw new BusinessException("The first administrator needs a name.");
        }
        String email = trimToNull(details.getEmail());
        String mobile = details.getMobile() == null || details.getMobile().isBlank()
                ? null
                : phoneNumberNormalizer.normalize(details.getMobile().trim());

        if (email == null && mobile == null) {
            // Both columns are the login identifier; with neither, the account exists and
            // can never be signed into, which is the situation this whole step exists to
            // avoid.
            throw new BusinessException(
                    "The first administrator needs an email or a mobile number to sign in with.");
        }
        if (email != null && memberRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("That email already belongs to another account.");
        }
        if (mobile != null && memberRepository.existsByMobile(mobile)) {
            throw new BusinessException("That mobile number already belongs to another account.");
        }

        Anbiyam anbiyam = new Anbiyam();
        anbiyam.setUuid(UUID.randomUUID().toString());
        anbiyam.setChurch(church);
        anbiyam.setAnbiyamName("Parish Office");
        anbiyam.setAreaDescription("Placeholder — rename or replace once the parish is set up");
        anbiyam.setActiveFlag(true);
        anbiyam.setRecordStatus("ACTIVE");
        Anbiyam savedAnbiyam = anbiyamRepository.save(anbiyam);

        Family family = new Family();
        family.setUuid(UUID.randomUUID().toString());
        family.setChurch(church);
        family.setAnbiyam(savedAnbiyam);
        // FAM-000 rather than FAM-001, so the parish's own first family keeps that code.
        family.setFamilyCode("FAM-000");
        family.setFamilyName("Parish Office");
        family.setMonthlyAmount(BigDecimal.ZERO);
        family.setRecordStatus("ACTIVE");
        Family savedFamily = familyRepository.save(family);

        Role appSA = roleRepository.findByRoleCode("AppSA")
                .orElseThrow(() -> new BusinessException("The AppSA role is missing."));

        Member admin = new Member();
        admin.setUuid(UUID.randomUUID().toString());
        admin.setChurch(church);
        admin.setFamily(savedFamily);
        admin.setAnbiyam(savedAnbiyam);
        admin.setRole(appSA);
        admin.setFirstName(details.getFirstName().trim());
        admin.setLastName(trimToNull(details.getLastName()));
        admin.setGender(details.getGender() == null || details.getGender().isBlank()
                ? "MALE" : details.getGender());
        admin.setEmail(email);
        admin.setMobile(mobile);
        admin.setMemberPassword(passwordService.encodedDefaultPassword());
        admin.setPasswordFlag(false);
        admin.setRecordStatus("ACTIVE");
        admin.setRemarks("First administrator, created with the parish");
        Member savedAdmin = memberRepository.save(admin);

        savedFamily.setHeadMemberId(savedAdmin.getId());
        familyRepository.save(savedFamily);

        auditService.event(AuditEventType.ROLE_ASSIGNED)
                .inChurch(church.getId())
                .on("Member", savedAdmin.getId(), savedAdmin.getDisplayName())
                .permission(Resource.USER, Operation.ADD)
                .describe("First administrator for " + church.getChurchName())
                .save();
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public Church update(Long id, ChurchForm form) {
        Church church = load(id);
        String before = church.getChurchName();
        apply(form, church);
        Church saved = churchRepository.save(church);
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, saved,
                before.equals(saved.getChurchName())
                        ? "Parish details updated"
                        : "Renamed from " + before);
        return saved;
    }

    @Transactional(readOnly = true)
    public ChurchForm formFor(Long id) {
        Church church = load(id);
        ChurchForm form = new ChurchForm();
        form.setId(church.getId());
        form.setChurchName(church.getChurchName());
        form.setDiocese(church.getDiocese());
        form.setLocation(church.getLocation());
        form.setAddressLine1(church.getAddressLine1());
        form.setAddressLine2(church.getAddressLine2());
        form.setCity(church.getCity());
        form.setDistrict(church.getDistrict());
        form.setState(church.getState());
        form.setCountry(church.getCountry());
        form.setPincode(church.getPincode());
        form.setPhone(church.getPhone());
        form.setEmail(church.getEmail());
        form.setEstablishedDate(church.getEstablishedDate());
        return form;
    }

    // ------------------------------------------------------------- substations

    @Transactional(readOnly = true)
    public List<SubstationRow> substationsOf(Long stationId) {
        return churchRepository
                .findByParentChurchIdAndDeletedFlagFalseOrderByChurchNameAsc(stationId).stream()
                .map(sub -> new SubstationRow(sub.getId(), sub.getChurchName(),
                        sub.getLocation(), address(sub)))
                .toList();
    }

    /** A chapel under a parish: a place of worship, holding nothing of its own. */
    @Transactional
    public void addSubstation(Long stationId, String name, String location, String address) {
        Church station = load(stationId);
        if (!station.isStation()) {
            throw new BusinessException("A substation cannot itself have substations.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("The substation needs a name.");
        }

        Church substation = new Church();
        substation.setUuid(UUID.randomUUID().toString());
        substation.setChurchName(name.trim());
        substation.setParentChurch(station);
        substation.setParentChurchName(station.getChurchName());
        substation.setLocation(trimToNull(location));
        substation.setAddressLine1(trimToNull(address));
        substation.setCity(station.getCity());
        substation.setDistrict(station.getDistrict());
        substation.setState(station.getState());
        substation.setCountry(station.getCountry());
        substation.setDiocese(station.getDiocese());
        substation.setRecordStatus("ACTIVE");
        Church saved = churchRepository.save(substation);

        audit(AuditEventType.RECORD_CREATED, Operation.ADD, saved,
                "Substation of " + station.getChurchName());
    }

    @Transactional
    public void removeSubstation(Long substationId) {
        Church substation = load(substationId);
        if (substation.isStation()) {
            throw new BusinessException("That is a parish, not a substation.");
        }
        substation.setDeletedFlag(true);
        churchRepository.save(substation);
        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, substation, "Substation removed");
    }

    // ------------------------------------------------------- remove and restore

    /**
     * Removes a parish without touching anything inside it.
     *
     * <p>Members, families, anbiyam, dues and receipts all stay exactly where they are.
     * They simply become unreachable, because the parish can no longer be entered — which
     * is why {@link #restore} exists rather than this being a one-way door.
     */
    @Transactional
    public void remove(Long id, String typedName) {
        Church church = load(id);

        if (!church.getChurchName().equalsIgnoreCase(typedName == null ? "" : typedName.trim())) {
            // Typed rather than clicked. This hides a parish's entire register behind one
            // action, and it will be done on an ordinary afternoon.
            throw new BusinessException(
                    "Type the parish name exactly to confirm removal.");
        }

        church.setDeletedFlag(true);
        churchRepository.save(church);

        long members = memberRepository.countByChurchIdAndDeletedFlagFalse(id);
        audit(AuditEventType.RECORD_DELETED, Operation.DELETE, church,
                "Parish removed. %d members, and their families, dues and receipts, are retained"
                        .formatted(members));
    }

    @Transactional
    public void restore(Long id) {
        Church church = churchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Church", id));
        church.setDeletedFlag(false);
        churchRepository.save(church);
        audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, church, "Parish restored");
    }

    @Transactional(readOnly = true)
    public List<RemovedChurch> removed() {
        return churchRepository.findAll().stream()
                .filter(Church::isDeletedFlag)
                .filter(Church::isStation)
                .map(church -> new RemovedChurch(church.getId(), church.getChurchName(),
                        church.getCity()))
                .toList();
    }

    // -------------------------------------------------------------- logo upload

    /**
     * Stores a parish crest as {@code <church_id>.png}.
     *
     * <p>Converted rather than merely accepted: parish staff upload whatever their phone
     * produced, and a JPEG saved under a .png name is a file some browsers refuse. Reading
     * it and writing PNG also means anything ImageIO cannot decode is rejected here rather
     * than displayed as a broken image later.
     */
    @Transactional
    public void uploadLogo(Long churchId, MultipartFile file) {
        Church church = load(churchId);

        if (file == null || file.isEmpty()) {
            return;
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessException("The logo must be smaller than 2 MB.");
        }

        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new BusinessException("That file is not an image the application can read.");
            }

            Path directory = Paths.get(storageProperties.getLogoDir()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            // The id comes from the entity, never from the request, so no user-supplied
            // text reaches the filename.
            Path target = directory.resolve(church.getId() + ".png");
            ImageIO.write(image, "png", target.toFile());

            audit(AuditEventType.RECORD_UPDATED, Operation.EDIT, church, "Logo replaced");
        } catch (IOException e) {
            log.error("Could not store the logo for church {}", churchId, e);
            throw new BusinessException("The logo could not be saved.");
        }
    }

    // ---------------------------------------------------------------- internals

    private void apply(ChurchForm form, Church church) {
        church.setChurchName(form.getChurchName().trim());
        church.setDiocese(trimToNull(form.getDiocese()));
        church.setLocation(trimToNull(form.getLocation()));
        church.setAddressLine1(trimToNull(form.getAddressLine1()));
        church.setAddressLine2(trimToNull(form.getAddressLine2()));
        church.setCity(form.getCity().trim());
        church.setDistrict(trimToNull(form.getDistrict()));
        church.setState(trimToNull(form.getState()));
        church.setCountry(trimToNull(form.getCountry()));
        church.setPincode(trimToNull(form.getPincode()));
        church.setPhone(trimToNull(form.getPhone()));
        church.setEmail(trimToNull(form.getEmail()));
        church.setEstablishedDate(form.getEstablishedDate());
    }

    private Church load(Long id) {
        return churchRepository.findById(id)
                .filter(church -> !church.isDeletedFlag())
                .orElseThrow(() -> new ResourceNotFoundException("Church", id));
    }

    private static String address(Church church) {
        StringBuilder text = new StringBuilder();
        for (String part : new String[]{church.getAddressLine1(), church.getCity(), church.getPincode()}) {
            if (part != null && !part.isBlank()) {
                if (!text.isEmpty()) {
                    text.append(", ");
                }
                text.append(part);
            }
        }
        return text.toString();
    }

    private void audit(AuditEventType eventType, Operation operation, Church church, String description) {
        AppUserPrincipal principal = CurrentUser.principalOrNull();
        AuditService.Entry entry = auditService.event(eventType)
                .on("Church", church.getId(), church.getChurchName())
                .inChurch(church.getId())
                .permission(Resource.CHURCH, operation)
                .describe(description);

        if (principal != null && principal.isPlatformUser()) {
            entry.actorSaasUser(principal.getUserId(), principal.getDisplayName());
        } else if (principal != null) {
            entry.actorMember(principal.getUserId(), principal.getDisplayName(), principal.getChurchId());
        }
        entry.save();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
