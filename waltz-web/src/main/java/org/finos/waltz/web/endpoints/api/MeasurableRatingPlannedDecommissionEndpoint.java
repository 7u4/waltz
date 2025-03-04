/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017, 2018, 2019 Waltz open source project
 * See README.md for more information
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific
 *
 */

package org.finos.waltz.web.endpoints.api;

import org.finos.waltz.service.measurable_rating_planned_decommission.MeasurableRatingPlannedDecommissionService;
import org.finos.waltz.service.user.UserRoleService;
import org.finos.waltz.web.DatumRoute;
import org.finos.waltz.web.ListRoute;
import org.finos.waltz.web.endpoints.Endpoint;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.command.DateFieldChange;
import org.finos.waltz.model.measurable_rating_planned_decommission.MeasurableRatingPlannedDecommission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.finos.waltz.web.WebUtilities.*;
import static org.finos.waltz.web.endpoints.EndpointUtilities.*;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.model.EntityKind.MEASURABLE;
import static org.finos.waltz.model.EntityKind.MEASURABLE_RATING_PLANNED_DECOMMISSION;
import static org.finos.waltz.model.EntityReference.mkRef;

@Service
public class MeasurableRatingPlannedDecommissionEndpoint implements Endpoint {

    private static final String BASE_URL = mkPath("api", "measurable-rating-planned-decommission");


    private final MeasurableRatingPlannedDecommissionService measurableRatingPlannedDecommissionService;
    private final UserRoleService userRoleService;


    @Autowired
    public MeasurableRatingPlannedDecommissionEndpoint(MeasurableRatingPlannedDecommissionService measurableRatingPlannedDecommissionService,
                                                       UserRoleService userRoleService) {
        checkNotNull(measurableRatingPlannedDecommissionService, "measurableRatingPlannedDecommissionService cannot be null");
        checkNotNull(userRoleService, "userRoleService cannot be null");

        this.measurableRatingPlannedDecommissionService = measurableRatingPlannedDecommissionService;
        this.userRoleService = userRoleService;
    }


    @Override
    public void register() {

        String findForEntityPath = mkPath(BASE_URL, "entity", ":kind", ":id");
        String findForReplacingEntityPath = mkPath(BASE_URL, "replacing-entity", ":kind", ":id");
        String savePath = mkPath(BASE_URL, "entity", ":kind", ":id", "MEASURABLE", ":measurableId");
        String removePath = mkPath(BASE_URL, "id", ":id");

        ListRoute<MeasurableRatingPlannedDecommission> findForEntityRoute = (request, response)
                -> measurableRatingPlannedDecommissionService.findForEntityRef(getEntityReference(request));

        ListRoute<MeasurableRatingPlannedDecommission> findForReplacingEntityRoute = (request, response)
                -> measurableRatingPlannedDecommissionService.findForReplacingEntityRef(getEntityReference(request));

        DatumRoute<MeasurableRatingPlannedDecommission> saveRoute = (request, response) -> {
            EntityReference entityRef = getEntityReference(request);
            long measurableId = getLong(request, "measurableId");

            requireRole(userRoleService, request, measurableRatingPlannedDecommissionService.getRequiredRatingEditRole(mkRef(MEASURABLE, measurableId)));

            return measurableRatingPlannedDecommissionService.save(
                    entityRef,
                    measurableId,
                    readBody(request, DateFieldChange.class),
                    getUsername(request));
        };

        DatumRoute<Boolean> removeRoute = (request, response) -> {
            long decommissionId = getId(request);
            EntityReference entityRef = mkRef(MEASURABLE_RATING_PLANNED_DECOMMISSION, decommissionId);

            requireRole(userRoleService, request,  measurableRatingPlannedDecommissionService.getRequiredRatingEditRole(entityRef));

            return measurableRatingPlannedDecommissionService.remove(decommissionId, getUsername(request));
        };

        getForList(findForEntityPath, findForEntityRoute);
        getForList(findForReplacingEntityPath, findForReplacingEntityRoute);
        postForDatum(savePath, saveRoute);
        deleteForDatum(removePath, removeRoute);

    }
}
