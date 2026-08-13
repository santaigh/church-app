package com.church.app.repository;

import com.church.app.entity.Role;
import com.church.app.entity.RoleLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(String roleCode);

    List<Role> findByRoleLevelAndDeletedFlagFalse(RoleLevel roleLevel);
}
