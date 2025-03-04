package org.finos.waltz.service.permission;

import org.finos.waltz.data.permission.PermissionGroupDao;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.attestation.UserAttestationPermission;
import org.finos.waltz.model.permission_group.CheckPermissionCommand;
import org.finos.waltz.model.permission_group.Permission;
import org.finos.waltz.model.permission_group.RequiredInvolvementsResult;
import org.finos.waltz.model.person.Person;
import org.finos.waltz.service.person.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

import static java.util.Objects.isNull;
import static org.finos.waltz.common.SetUtilities.filter;

@Service
public class PermissionGroupService {
    private static final Logger LOG = LoggerFactory.getLogger(PermissionGroupService.class);

    private final PersonService personService;
    private final PermissionGroupDao permissionGroupDao;


    @Autowired
    public PermissionGroupService(PersonService personService,
                                  PermissionGroupDao permissionGroupDao) {
        this.personService = personService;
        this.permissionGroupDao = permissionGroupDao;
    }


    public Set<Permission> findPermissionsForParentReference(EntityReference parentEntityRef,
                                                             String username) {

        Person person = personService.getPersonByUserId(username);

        if (isNull(person)) {
            return Collections.emptySet();
        }

        Set<Permission> permissions = permissionGroupDao.findPermissionsForParentEntityReference(parentEntityRef);
        Set<Long> involvements = permissionGroupDao.findExistingInvolvementKindIdsForUser(parentEntityRef, username);

        return filter(
                permissions,
                p -> p.requiredInvolvementsResult().isAllowed(involvements));
    }


    public boolean hasPermission(CheckPermissionCommand permissionCommand) {

        RequiredInvolvementsResult required = permissionGroupDao.getRequiredInvolvements(permissionCommand);

        if (required.areAllUsersAllowed()) {
            return true;
        }

        if (required.requiredInvolvementKindIds().isEmpty()) {
            // no involvements (incl. defaults) for this requested permission, therefore can safely say 'no'
            return false;
        }

        Set<Long> existingInvolvements = permissionGroupDao.findExistingInvolvementKindIdsForUser(
                permissionCommand.parentEntityRef(),
                permissionCommand.user());

        return required.isAllowed(existingInvolvements);
    }


    public Set<UserAttestationPermission> findSupportedMeasurableCategoryAttestations(EntityReference ref, String userId) {
        return permissionGroupDao.findSupportedMeasurableCategoryAttestations(ref, userId);
    }
}
