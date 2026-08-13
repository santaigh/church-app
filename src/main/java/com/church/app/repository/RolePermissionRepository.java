package com.church.app.repository;

import com.church.app.entity.Operation;
import com.church.app.entity.Resource;
import com.church.app.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /** Everything a role may do. Read once at authentication to build the user's authorities. */
    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByRoleIdAndResource(Long roleId, Resource resource);

    boolean existsByRoleIdAndResourceAndOperation(Long roleId, Resource resource, Operation operation);

    /** Revoking a permission removes the row; there is no soft delete on this table. */
    void deleteByRoleIdAndResourceAndOperation(Long roleId, Resource resource, Operation operation);

    long countByRoleId(Long roleId);
}
