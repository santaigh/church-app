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
import java.util.Map;
import java.util.stream.Collectors;

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

        // Two lookups for the whole page rather than two per row: the counts come from
        // one GROUP BY, and the heads from one findAllById. At six hundred families the
        // per-row version was a hundred round trips a page.
        Map<Long, Long> memberCounts = memberRepository
                .countByFamilyForChurch(currentChurchId()).stream()
                .collect(Collectors.toMap(MemberRepository.GroupCount::getGroupId,
                        MemberRepository.GroupCount::getTotal));

        Map<Long, String> heads = namesOf(found.getContent().stream()
                .map(Family::getHeadMemberId)
                .filter(java.util.Objects::nonNull)
                .toList());

        return PageView.of(found, found.getContent().stream()
                .map(family -> new FamilyRow(
                        family.getId(),
                        family.getFamilyCode(),
                        family.getFamilyName(),
                        family.getAnbiyam().getAnbiyamName(),
                        family.getAnbiyam().getId(),
                        heads.get(family.getHeadMemberId()),
                        memberCounts.getOrDefault(family.getId(), 0L),
                        family.getMonthlyAmount()))
                .toList());
    }

    /** Resolves a page's worth of member ids to names in one query. */
    private Map<Long, String> namesOf(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(memberIds).stream()
                .filter(member -> !member.isDeletedFlag())
                .collect(Collectors.toMap(member -> member.getId(), member -> member.getDisplayName()));
    }

    private static String lower(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private Long currentChurchId() {
        return TenantContext.currentChurchId().orElseThrow(() -> new BusinessException(
                "No parish is selected, so there is nothing to read."));
    }
}
