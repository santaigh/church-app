package com.church.app.service;

import com.church.app.dto.PageView;
import com.church.app.entity.Family;
import com.church.app.repository.FamilyRepository;
import com.church.app.repository.MemberRepository;
import com.church.app.security.TenantContext;
import com.church.app.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The households of the parish in scope.
 *
 * <p>Read-only for now. A family is created as part of registering its members, and
 * editing one is a separate decision that has not been taken yet -- so this lists what is
 * there rather than pretending to manage it.
 */
@Service
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;

    public FamilyService(FamilyRepository familyRepository, MemberRepository memberRepository) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
    }

    public record FamilyRow(Long id,
                            String code,
                            String name,
                            String anbiyamName,
                            Long anbiyamId,
                            String headName,
                            long members,
                            BigDecimal monthlyAmount) {
    }

    /** The column searches a family list may be narrowed by. All optional. */
    public record FamilySearch(String name, String code, String anbiyam) {

        public boolean isActive() {
            return name != null || code != null || anbiyam != null;
        }
    }

    @Transactional(readOnly = true)
    public PageView<FamilyRow> list(FamilySearch search, int page, int size) {
        FamilySearch criteria = search == null ? new FamilySearch(null, null, null) : search;

        Page<Family> found = familyRepository.search(
                currentChurchId(),
                lower(criteria.name()),
                lower(criteria.code()),
                lower(criteria.anbiyam()),
                PageRequest.of(Math.max(page, 1) - 1, size));

        return PageView.of(found, found.getContent().stream()
                .map(family -> new FamilyRow(
                        family.getId(),
                        family.getFamilyCode(),
                        family.getFamilyName(),
                        family.getAnbiyam().getAnbiyamName(),
                        family.getAnbiyam().getId(),
                        headName(family.getHeadMemberId()),
                        memberRepository.countByFamilyIdAndDeletedFlagFalse(family.getId()),
                        family.getMonthlyAmount()))
                .toList());
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    /**
     * The head's name, or null where none is recorded.
     *
     * <p>A family can sit headless -- when the head dies or moves, the pointer is cleared
     * until another is named -- so this is a real case, not a data fault.
     */
    private String headName(Long headMemberId) {
        if (headMemberId == null) {
            return null;
        }
        return memberRepository.findById(headMemberId)
                .filter(member -> !member.isDeletedFlag())
                .map(member -> member.getDisplayName())
                .orElse(null);
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to read."));
    }
}
