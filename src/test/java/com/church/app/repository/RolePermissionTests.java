package com.church.app.repository;

import com.church.app.entity.Church;
import com.church.app.entity.ChurchCategory;
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
    @DisplayName("super admins get every operation on every resource")
    void superAdminsGetEverything() {
        int expected = Resource.values().length * Operation.values().length;
        assertEquals(expected, rolePermissionRepository.countByRoleId(roleId("SaaSSAdmin")));
        assertEquals(expected, rolePermissionRepository.countByRoleId(roleId("AppSA")));
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
    @DisplayName("church.category_id maps onto the ChurchCategory enum after V10")
    void churchCategoryNormalised() {
        List<Church> churches = churchRepository.findByDeletedFlagFalseOrderByChurchNameAsc();
        assertEquals(3, churches.size());
        // V7 wrote 'PARISH', which is not an enum value; V10 normalised it to STATION.
        long stations = churches.stream().filter(c -> c.getCategory() == ChurchCategory.STATION).count();
        long substations = churches.stream().filter(c -> c.getCategory() == ChurchCategory.SUBSTATION).count();
        assertEquals(2, stations);
        assertEquals(1, substations);
    }
}
