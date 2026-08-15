package com.church.app.service;

import com.church.app.entity.Church;
import com.church.app.repository.AnbiyamRepository;
import com.church.app.repository.ChurchRepository;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.repository.ParishPriestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The parish directory a platform user browses before entering one church.
 *
 * <p>Only stations are listed. A substation is a place of worship under a station -- it
 * holds no members, families, anbiyams or priest of its own -- so there is nothing to
 * administer inside one; they appear beneath their station as detail, not as entries.
 *
 * <p>Every count is asked for by church id explicitly rather than through {@code count()},
 * which would answer for whatever tenant scope the request happens to be in.
 */
@Service
public class ChurchDirectoryService {

    private final ChurchRepository churchRepository;
    private final MemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final AnbiyamRepository anbiyamRepository;
    private final ParishPriestRepository parishPriestRepository;

    public ChurchDirectoryService(ChurchRepository churchRepository,
                                  MemberRepository memberRepository,
                                  FamilyRepository familyRepository,
                                  AnbiyamRepository anbiyamRepository,
                                  ParishPriestRepository parishPriestRepository) {
        this.churchRepository = churchRepository;
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.anbiyamRepository = anbiyamRepository;
        this.parishPriestRepository = parishPriestRepository;
    }

    public record SubstationView(Long id, String name, String location, String address) {
    }

    /** A row in the parish list: the town is shown because two parishes may share a name. */
    public record StationView(Long id, String name, String town, int substationCount) {
    }

    /** One member of clergy serving here now. */
    public record ClergyView(String name, String role, LocalDate since) {
    }

    public record ChurchDetail(Long id,
                               String name,
                               String town,
                               String diocese,
                               String address,
                               String phone,
                               String email,
                               LocalDate establishedDate,
                               String parishPriest,
                               long members,
                               long families,
                               long anbiyams,
                               List<SubstationView> substations,
                               List<ClergyView> clergy) {
    }

    @Transactional(readOnly = true)
    public List<StationView> stations() {
        return churchRepository.findByParentChurchIsNullAndDeletedFlagFalseOrderByChurchNameAsc()
                .stream()
                .map(church -> new StationView(
                        church.getId(),
                        church.getChurchName(),
                        town(church),
                        substationsOf(church.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ChurchDetail> detail(Long churchId) {
        return churchRepository.findById(churchId)
                .filter(church -> !church.isDeletedFlag())
                // A substation is never an entry point, so it is never a detail view either.
                .filter(Church::isStation)
                .map(church -> new ChurchDetail(
                        church.getId(),
                        church.getChurchName(),
                        town(church),
                        church.getDiocese(),
                        address(church),
                        church.getPhone(),
                        church.getEmail(),
                        church.getEstablishedDate(),
                        church.getParishPriest(),
                        memberRepository.countByChurchIdAndDeletedFlagFalse(churchId),
                        familyRepository.countByChurchIdAndDeletedFlagFalse(churchId),
                        anbiyamRepository.countByChurchIdAndDeletedFlagFalse(churchId),
                        substationsOf(churchId),
                        clergyOf(churchId)));
    }

    /** True when the id names a station that may be entered. */
    @Transactional(readOnly = true)
    public boolean isEnterable(Long churchId) {
        return churchId != null && detail(churchId).isPresent();
    }

    /** Priest, assistants and brothers serving now -- an open posting is a current one. */
    private List<ClergyView> clergyOf(Long churchId) {
        return parishPriestRepository
                .findByChurchIdAndToDateIsNullAndDeletedFlagFalseOrderByClergyRoleAscFromDateAsc(churchId)
                .stream()
                .map(posting -> new ClergyView(
                        posting.getPriestName(),
                        posting.getClergyRole().getLabel(),
                        posting.getFromDate().toLocalDate()))
                .toList();
    }

    private List<SubstationView> substationsOf(Long stationId) {
        return churchRepository.findByParentChurchIdAndDeletedFlagFalseOrderByChurchNameAsc(stationId)
                .stream()
                .map(sub -> new SubstationView(
                        sub.getId(), sub.getChurchName(), sub.getLocation(), address(sub)))
                .toList();
    }

    private static String town(Church church) {
        return church.getCity() != null ? church.getCity() : church.getLocation();
    }

    private static String address(Church church) {
        StringBuilder address = new StringBuilder();
        append(address, church.getAddressLine1());
        append(address, church.getAddressLine2());
        append(address, church.getCity());
        append(address, church.getPincode());
        return address.toString();
    }

    private static void append(StringBuilder target, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(", ");
        }
        target.append(part);
    }
}
