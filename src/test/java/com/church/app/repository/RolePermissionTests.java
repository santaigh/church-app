package com.church.app.repository;

import com.church.app.entity.Church;
import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.entity.Role;
import com.church.app.entity.RolePermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the seeded permission matrix says what it was meant to say.
 *
 * <p>Read-only; nothing is written.
 */
@SpringBootTest
@Transactional
class RolePermissionTests {

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ChurchRepository churchRepository;

    private Long roleId(String code) {
        return roleRepository.findByRoleCode(code).map(Role::getId).orElseThrow();
    }

    private Set<Operation> operationsFor(String roleCode, Resource resource) {
        return rolePermissionRepository.findByRoleIdAndResource(roleId(roleCode), resource)
                .stream().map(RolePermission::getOperation).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("super admins get every operation on every resource V11 generated")
    void superAdminsGetEverything() {
        // PARISH_PRIEST is the one exception and is counted separately below: V20 seeds
        // it narrowly on purpose, because appointing clergy is a diocese-level act.
        int broad = (Resource.values().length - 1) * Operation.values().length;

        assertEquals(broad + 4, rolePermissionRepository.countByRoleId(roleId("SaaSSAdmin")),
                "every resource but PARISH_PRIEST, plus VIEW/ADD/EDIT/DELETE on that one");
        assertEquals(broad + 1, rolePermissionRepository.countByRoleId(roleId("AppSA")),
                "every resource but PARISH_PRIEST, plus VIEW only on that one");
    }

    @Test
    @DisplayName("appointing clergy is reserved to the platform, and parishes only look")
    void clergyAppointmentIsDioceseLevel() {
        for (String parishRole : List.of("AppSA", "AppAdmin", "AppUser")) {
            assertEquals(Set.of(Operation.VIEW), operationsFor(parishRole, Resource.PARISH_PRIEST),
                    parishRole + " may see its clergy but never change them");
        }

        assertTrue(operationsFor("SaaSSAdmin", Resource.PARISH_PRIEST)
                .containsAll(Set.of(Operation.VIEW, Operation.ADD, Operation.EDIT, Operation.DELETE)));

        Set<Operation> saasAdmin = operationsFor("SaaSAdmin", Resource.PARISH_PRIEST);
        assertTrue(saasAdmin.containsAll(Set.of(Operation.VIEW, Operation.ADD, Operation.EDIT)));
        assertFalse(saasAdmin.contains(Operation.DELETE), "DELETE stays with the super admin");
    }

    @Test
    @DisplayName("admins get everything except DELETE")
    void adminsCannotDelete() {
        for (String code : List.of("SaaSAdmin", "AppAdmin")) {
            Set<Operation> ops = operationsFor(code, Resource.MEMBER);
            assertEquals(5, ops.size(), code + " should have 5 operations on MEMBER");
            assertFalse(ops.contains(Operation.DELETE), code + " must not have DELETE");
            assertTrue(ops.contains(Operation.EDIT));
            assertTrue(ops.contains(Operation.EXPORT));

            assertFalse(rolePermissionRepository.existsByRoleIdAndResourceAndOperation(
                    roleId(code), Resource.CHURCH, Operation.DELETE));
        }
    }

    @Test
    @DisplayName("SaaSUser is read-only but may export; AppUser may not export")
    void readOnlyRolesDifferOnExport() {
        Set<Operation> saasUser = operationsFor("SaaSUser", Resource.MEMBER);
        assertEquals(Set.of(Operation.VIEW, Operation.EXPORT), saasUser);

        // A read-only parish account must not be able to extract a member list
        // complete with phone numbers and addresses.
        Set<Operation> appUser = operationsFor("AppUser", Resource.MEMBER);
        assertEquals(Set.of(Operation.VIEW), appUser);
    }

    @Test
    @DisplayName("SaaS and App roles share an operation set; only data scope differs")
    void saasAndAppRolesMirrorEachOther() {
        assertEquals(operationsFor("SaaSSAdmin", Resource.PAYMENT), operationsFor("AppSA", Resource.PAYMENT));
        assertEquals(operationsFor("SaaSAdmin", Resource.PAYMENT), operationsFor("AppAdmin", Resource.PAYMENT));
    }

    @Test
    @DisplayName("every resource in the enum is covered for every role")
    void everyResourceIsCovered() {
        for (Resource resource : Resource.values()) {
            assertFalse(operationsFor("AppSA", resource).isEmpty(),
                    "AppSA has no permissions on " + resource);
            assertFalse(operationsFor("AppUser", resource).isEmpty(),
                    "AppUser has no permissions on " + resource);
        }
    }

    @Test
    @DisplayName("authority strings are well formed")
    void authorityStringsAreWellFormed() {
        RolePermission permission = rolePermissionRepository
                .findByRoleIdAndResource(roleId("AppUser"), Resource.DASHBOARD).get(0);
        assertEquals("PERM_DASHBOARD_VIEW", permission.toAuthority());
    }

    @Test
    @DisplayName("a substation is one with a parent; everything else is a station")
    void stationsAndSubstations() {
        List<Church> churches = churchRepository.findByDeletedFlagFalseOrderByChurchNameAsc();
        assertEquals(4, churches.size());

        // V17 dropped category_id, so the parent link is the only definition left --
        // which is the point: the two used to disagree with each other.
        assertEquals(3, churches.stream().filter(Church::isStation).count());

        List<Church> substations = churches.stream().filter(c -> !c.isStation()).toList();
        assertEquals(1, substations.size());
        assertEquals("St. Anthony's Chapel", substations.get(0).getChurchName());
        assertEquals("St. Mary's Cathedral", substations.get(0).getParentChurch().getChurchName());
    }
}
