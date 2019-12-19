/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.service.attestation;

import com.khartec.waltz.data.GenericSelector;
import com.khartec.waltz.data.GenericSelectorFactory;
import com.khartec.waltz.data.attestation.AttestationInstanceDao;
import com.khartec.waltz.data.person.PersonDao;
import com.khartec.waltz.model.*;
import com.khartec.waltz.model.attestation.AttestEntityCommand;
import com.khartec.waltz.model.attestation.AttestationInstance;
import com.khartec.waltz.model.attestation.AttestationRun;
import com.khartec.waltz.model.changelog.ImmutableChangeLog;
import com.khartec.waltz.model.person.Person;
import com.khartec.waltz.service.changelog.ChangeLogService;
import org.jooq.Record1;
import org.jooq.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.khartec.waltz.common.Checks.checkNotEmpty;
import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.CollectionUtilities.first;
import static com.khartec.waltz.common.CollectionUtilities.notEmpty;
import static com.khartec.waltz.common.DateTimeUtilities.nowUtc;


@Service
public class AttestationInstanceService {

    private static final Logger LOG = LoggerFactory.getLogger(AttestationInstanceService.class);

    private final AttestationInstanceDao attestationInstanceDao;
    private final AttestationRunService attestationRunService;
    private final PersonDao personDao;
    private final ChangeLogService changeLogService;

    private final GenericSelectorFactory genericSelectorFactory = new GenericSelectorFactory();


    public AttestationInstanceService(AttestationInstanceDao attestationInstanceDao,
                                      AttestationRunService attestationRunService,
                                      PersonDao personDao, ChangeLogService changeLogService) {
        checkNotNull(attestationInstanceDao, "attestationInstanceDao cannot be null");
        checkNotNull(attestationRunService, "attestationRunService cannot be null");
        checkNotNull(personDao, "personDao cannot be null");
        checkNotNull(changeLogService, "changeLogService cannot be null");

        this.attestationInstanceDao = attestationInstanceDao;
        this.attestationRunService = attestationRunService;
        this.personDao = personDao;
        this.changeLogService = changeLogService;
    }


    public List<AttestationInstance> findByRecipient(String userId, boolean unattestedOnly) {
        checkNotNull(userId, "userId cannot be null");

        return attestationInstanceDao.findByRecipient(userId, unattestedOnly);
    }


    public List<AttestationInstance> findHistoricalForPendingByUserId(String userId) {
        checkNotNull(userId, "userId cannot be null");

        return attestationInstanceDao.findHistoricalForPendingByUserId(userId);
    }


    public List<AttestationInstance> findByEntityReference(EntityReference ref) {
        checkNotNull(ref, "ref cannot be null");

        return attestationInstanceDao.findByEntityReference(ref);
    }


    public boolean attestInstance(long instanceId, String attestedBy) {
        checkNotEmpty(attestedBy, "attestedBy must be provided");

        boolean success = attestationInstanceDao.attestInstance(instanceId, attestedBy, nowUtc());
        if(success) {
            AttestationInstance instance = attestationInstanceDao.getById(instanceId);
            AttestationRun run = attestationRunService.getById(instance.attestationRunId());
            logChange(attestedBy, instance, run.attestedEntityKind());
        }
        return success;
    }


    public List<AttestationInstance> findByRunId(long runId) {
        return attestationInstanceDao.findByRunId(runId);
    }


    public List<Person> findPersonsByInstanceId(long id) {
        return personDao.findPersonsByAttestationInstanceId(id);
    }


    public List<AttestationInstance> findByIdSelector(IdSelectionOptions options) {
        Select<Record1<Long>> selector = mkIdSelector(options);
        return attestationInstanceDao.findByIdSelector(selector);
    }


    public int cleanupOrphans() {
        return attestationInstanceDao.cleanupOrphans();
    }


    private Select<Record1<Long>> mkIdSelector(IdSelectionOptions selectionOptions) {
        GenericSelector genericSelector = genericSelectorFactory.applyForKind(EntityKind.ATTESTATION, selectionOptions);
        return genericSelector.selector();
    }

    private void logChange (String username, AttestationInstance instance, EntityKind attestedKind) {

        changeLogService.write(ImmutableChangeLog.builder()
                .message(String.format("Attestation of %s", attestedKind))
                .parentReference(instance.parentEntity())
                .userId(username)
                .severity(Severity.INFORMATION)
                .childKind(attestedKind)
                .operation(Operation.ADD)
                .build());
    }


    public boolean attestForEntity(String username, AttestEntityCommand createCommand) {

        List<AttestationInstance> instancesForEntityForUser = attestationInstanceDao
                .findForEntityByRecipient(
                        createCommand,
                        username,
                        true);

        if (notEmpty(instancesForEntityForUser)){
            instancesForEntityForUser
                    .forEach(attestation -> {
                        try {
                            Long instanceId = getInstanceId(attestation);
                            attestInstance(instanceId, username);
                        } catch (Exception e) {
                            LOG.error("Failed to attest instance", e);
                        }
                    });

            return true;
        } else {
            IdCommandResponse idCommandResponse = attestationRunService.createRunForEntity(username, createCommand);
            Long runId = getRunId(idCommandResponse);

            Long instanceId = getInstanceId(first(findByRunId(runId)));
            return attestInstance(instanceId, username);
        }
    }

    private Long getRunId(IdCommandResponse idCommandResponse) throws IllegalStateException {
        return idCommandResponse.id()
                        .orElseThrow(() -> new IllegalStateException("Unable to get identifier for this run"));
    }

    private Long getInstanceId(AttestationInstance attestation) throws IllegalStateException {
        return attestation
                .id()
                .orElseThrow(() -> new IllegalStateException("Unable to get identifier for this instance"));
    }
}
