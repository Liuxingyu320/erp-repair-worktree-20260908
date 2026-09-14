package com.erp.system.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.exception.ServiceException;
import com.erp.system.api.domain.SysRole;
import com.erp.system.api.domain.SysUser;
import com.erp.system.domain.SysUserRole;
import com.erp.system.mapper.*;
import com.erp.system.service.ISysUserService;
import com.erp.system.service.support.UserSessionInvalidationService;

class DisabledRoleAssignmentTest
{
    final Map<Long, SysRole> roles = new HashMap<>();
    final Map<Long, Set<Long>> assigned = new HashMap<>();
    final List<String> events = new ArrayList<>();
    final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    final SysUserRoleMapper relations = mock(SysUserRoleMapper.class);
    final SysUserMapper userMapper = mock(SysUserMapper.class);
    final SysConfigMapper config = mock(SysConfigMapper.class);
    final UserSessionInvalidationService invalidation = mock(UserSessionInvalidationService.class);
    final SysUserServiceImpl users = new SysUserServiceImpl() {
        @Override public void checkUserAllowed(SysUser user) { }
        @Override public void checkUserDataScope(Long userId) { }
    };
    final SysRoleServiceImpl roleService = new SysRoleServiceImpl() {
        @Override public void checkRoleAllowed(SysRole role) { }
        @Override public void checkRoleDataScope(Long... roleIds) { }
    };

    @BeforeEach void setUp()
    {
        role(20L, "0"); role(30L, "1"); role(40L, "0");
        assigned.put(100L, new LinkedHashSet<>(List.of(20L, 30L)));
        assigned.put(200L, new LinkedHashSet<>());
        when(config.lockSignHrState()).thenAnswer(call -> { events.add("global"); return 1L; });
        when(roleMapper.selectRoleByIdForUpdate(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); events.add("role:" + id); return roles.get(id);
        });
        when(relations.lockUserForRoleAssignment(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); events.add("user:" + id); return assigned.containsKey(id) ? id : null;
        });
        when(relations.selectRoleIdsByUserId(anyLong())).thenAnswer(call -> {
            Long id = call.getArgument(0); events.add("relations:" + id); return new ArrayList<>(assigned.get(id));
        });
        when(relations.deleteUserRoleInfo(any())).thenAnswer(call -> {
            SysUserRole row = call.getArgument(0); events.add("delete:" + row.getRoleId());
            return assigned.get(row.getUserId()).remove(row.getRoleId()) ? 1 : 0;
        });
        when(relations.batchUserRole(anyList())).thenAnswer(call -> {
            List<SysUserRole> rows = call.getArgument(0);
            for (SysUserRole row : rows)
            {
                events.add("insert:" + row.getUserId() + ":" + row.getRoleId());
                assertThat(assigned.get(row.getUserId()).add(row.getRoleId())).isTrue();
            }
            return rows.size();
        });
        when(userMapper.insertUser(any())).thenAnswer(call -> {
            SysUser user = call.getArgument(0); events.add("create:user");
            user.setUserId(300L); assigned.put(300L, new LinkedHashSet<>()); return 1;
        });
        when(userMapper.updateUser(any())).thenReturn(1);
        for (Object service : List.of(users, roleService))
        {
            ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
            ReflectionTestUtils.setField(service, "userRoleMapper", relations);
            ReflectionTestUtils.setField(service, "configMapper", config);
            ReflectionTestUtils.setField(service, "userSessionInvalidationService", invalidation);
        }
        ReflectionTestUtils.setField(users, "userMapper", userMapper);
        ReflectionTestUtils.setField(users, "profileMapper", mock(SysUserProfileMapper.class));
        ReflectionTestUtils.setField(users, "userPostMapper", mock(SysUserPostMapper.class));
        ReflectionTestUtils.setField(roleService, "userService", mock(ISysUserService.class));
    }

    @Test void profileSaveRetainsDisabledBindingWithoutDeleteOrInsert()
    {
        assertThat(users.updateUser(user(100L, 30L, 20L))).isEqualTo(1);
        assertThat(assigned.get(100L)).containsExactly(20L, 30L); noRelationWrites();
    }

    @Test void omittedProfileRolesPreserveAllBindings()
    {
        SysUser user = user(100L); user.setRoleIds(null); users.updateUser(user);
        assertThat(assigned.get(100L)).containsExactly(20L, 30L); noRelationWrites();
    }

    @Test void explicitEmptyProfileSelectionRemovesBothEnabledAndDisabled()
    {
        users.updateUser(user(100L)); assertThat(assigned.get(100L)).isEmpty();
        verify(relations, never()).deleteUserRoleByUserId(anyLong());
    }

    @Test void profileCannotAddDisabledRoleBeforeDeletingOriginal()
    {
        assigned.get(200L).add(40L);
        assertThatThrownBy(() -> users.updateUser(user(200L, 30L))).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(200L)).containsExactly(40L); noRelationWrites();
        verify(userMapper, never()).updateUser(any());
    }

    @Test void authorizationRetainsDisabledAndAddsEnabledOnly()
    {
        users.insertUserAuth(100L, new Long[] { 30L, 40L });
        assertThat(assigned.get(100L)).containsExactly(30L, 40L);
        assertThat(events).containsSubsequence("global", "role:30", "role:40", "user:100", "relations:100", "delete:20", "insert:100:40");
        verify(relations, never()).deleteUserRoleByUserId(anyLong());
    }

    @Test void authorizationRejectsWholeMixedSelectionBeforeAnyWrite()
    {
        assigned.get(200L).add(20L);
        assertThatThrownBy(() -> users.insertUserAuth(200L, new Long[] { 40L, 30L })).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(200L)).containsExactly(20L); noRelationWrites();
    }

    @Test void authorizationAllowsRemovingDisabledBinding()
    {
        users.insertUserAuth(100L, new Long[] { 20L });
        assertThat(assigned.get(100L)).containsExactly(20L);
    }

    @Test void removedDisabledBindingCannotBeAddedAgain()
    {
        users.insertUserAuth(100L, new Long[] { 20L });
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 20L, 30L })).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(100L)).containsExactly(20L);
    }

    @Test void missingAuthorizationSelectionCannotClearRoles()
    {
        assertThatThrownBy(() -> users.insertUserAuth(100L, null)).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(100L)).containsExactly(20L, 30L); noRelationWrites();
    }

    @Test void duplicateAndOutOfOrderRoleIdsAreCanonicalizedBeforeLocks()
    {
        users.insertUserAuth(200L, new Long[] { 40L, 20L, 40L });
        assertThat(events).containsExactly("global", "role:20", "role:40", "user:200", "relations:200", "insert:200:20", "insert:200:40");
    }

    @Test void missingRoleDoesNotDeleteExistingBindings()
    {
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 99L })).isInstanceOf(ServiceException.class);
        noRelationWrites();
    }

    @Test void deletedRoleDoesNotDeleteExistingBindings()
    {
        roles.get(30L).setDelFlag("2");
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 30L })).isInstanceOf(ServiceException.class);
        noRelationWrites();
    }

    @Test void invalidRoleIdDoesNotDeleteExistingBindings()
    {
        assertThatThrownBy(() -> users.insertUserAuth(100L, new Long[] { 20L, null })).isInstanceOf(ServiceException.class);
        noRelationWrites();
    }

    @Test void missingUserCannotGetAnOrphanBinding()
    {
        assertThatThrownBy(() -> users.insertUserAuth(999L, new Long[] { 20L })).isInstanceOf(ServiceException.class);
        noRelationWrites();
    }

    @Test void newUserCannotReceiveDisabledRoleAndIsNotInserted()
    {
        assertThatThrownBy(() -> users.insertUser(user(null, 20L, 30L))).isInstanceOf(ServiceException.class);
        verify(userMapper, never()).insertUser(any()); noRelationWrites();
    }

    @Test void newUserLocksEnabledRolesBeforeUserInsert()
    {
        users.insertUser(user(null, 40L, 20L, 40L));
        assertThat(events).containsExactly("global", "role:20", "role:40", "create:user", "insert:300:20", "insert:300:40");
    }

    @Test void disabledRoleBulkCannotAddOneNewMemberAmongExisting()
    {
        assertThatThrownBy(() -> roleService.insertAuthUsers(30L, new Long[] { 100L, 200L })).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(100L)).contains(30L); assertThat(assigned.get(200L)).isEmpty(); noRelationWrites();
    }

    @Test void disabledRoleExistingMemberReselectionIsSuccessfulNoOp()
    {
        assertThat(roleService.insertAuthUsers(30L, new Long[] { 100L, 100L })).isEqualTo(1);
        noRelationWrites(); verifyNoInteractions(invalidation);
    }

    @Test void enabledRoleBulkAddsOnlyMissingAndLocksUsersInOrder()
    {
        assertThat(roleService.insertAuthUsers(20L, new Long[] { 200L, 100L, 200L })).isEqualTo(1);
        assertThat(events).containsExactly("global", "role:20", "user:100", "user:200", "relations:100", "relations:200", "insert:200:20");
    }

    @Test void bulkMissingUserCausesNoPartialWrite()
    {
        assertThatThrownBy(() -> roleService.insertAuthUsers(20L, new Long[] { 200L, 999L })).isInstanceOf(ServiceException.class);
        assertThat(assigned.get(200L)).isEmpty(); noRelationWrites();
    }

    @Test void disabledRoleCanBeUnboundThroughRolePage()
    {
        SysUserRole relation = new SysUserRole(); relation.setRoleId(30L); relation.setUserId(100L);
        assertThat(roleService.deleteAuthUser(relation)).isEqualTo(1);
        assertThat(assigned.get(100L)).containsExactly(20L);
    }

    private void noRelationWrites()
    {
        verify(relations, never()).batchUserRole(anyList());
        verify(relations, never()).deleteUserRoleInfo(any());
        verify(relations, never()).deleteUserRoleByUserId(anyLong());
    }
    private void role(Long id, String status)
    {
        SysRole role = new SysRole(id); role.setStatus(status); role.setDelFlag("0"); roles.put(id, role);
    }
    private static SysUser user(Long id, Long... roles)
    {
        SysUser user = new SysUser(id); user.setRoleIds(roles); user.setStatus("0"); return user;
    }
}
