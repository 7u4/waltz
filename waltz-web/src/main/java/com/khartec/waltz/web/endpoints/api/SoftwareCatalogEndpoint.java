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

package com.khartec.waltz.web.endpoints.api;

import com.khartec.waltz.model.IdSelectionOptions;
import com.khartec.waltz.model.software_catalog.SoftwareCatalog;
import com.khartec.waltz.model.software_catalog.SoftwareSummaryStatistics;
import com.khartec.waltz.service.software_catalog.SoftwareCatalogService;
import com.khartec.waltz.web.DatumRoute;
import com.khartec.waltz.web.endpoints.Endpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spark.Request;
import spark.Response;

import java.io.IOException;

import static com.khartec.waltz.web.WebUtilities.*;
import static com.khartec.waltz.web.endpoints.EndpointUtilities.getForDatum;
import static com.khartec.waltz.web.endpoints.EndpointUtilities.postForDatum;

@Service
public class SoftwareCatalogEndpoint implements Endpoint {

    private static final String BASE_URL = mkPath("api", "software-catalog");

    private final SoftwareCatalogService service;

    @Autowired
    public SoftwareCatalogEndpoint(SoftwareCatalogService service) {
        this.service = service;
    }


    @Override
    public void register() {

        String makeCatalogForAppIdsPath = mkPath(BASE_URL, "apps");
        String calculateStatsForAppIdSelectorPath = mkPath(BASE_URL, "stats");


        DatumRoute<SoftwareCatalog> makeCatalogForAppIdsRoute = (request, response) ->
                service.makeCatalogForAppIds(readIdsFromBody(request));

        DatumRoute<SoftwareSummaryStatistics> calculateStatsForAppIdSelectorRoute = (request, response)
                -> service.calculateStatisticsForAppIdSelector(readIdSelectionOptionsFromBody(request));


        getForDatum(mkPath(BASE_URL, "package-id", ":id"), this::getByPackageIdRoute);
        getForDatum(mkPath(BASE_URL, "licence-id", ":id"), this::getByLicenceIdRoute);
        postForDatum(mkPath(BASE_URL, "selector"), this::findBySelectorRoute);
        postForDatum(makeCatalogForAppIdsPath, makeCatalogForAppIdsRoute);
        postForDatum(calculateStatsForAppIdSelectorPath, calculateStatsForAppIdSelectorRoute);

    }


    private SoftwareCatalog getByPackageIdRoute(Request request, Response response) {
        long id = getId(request);
        return service.getByPackageId(id);
    }


    private SoftwareCatalog getByLicenceIdRoute(Request request, Response response) {
        long id = getId(request);
        return service.getByLicenceId(id);
    }


    private SoftwareCatalog findBySelectorRoute(Request request, Response response) throws IOException {
        IdSelectionOptions options = readIdSelectionOptionsFromBody(request);
        return service.findBySelector(options);
    }

}
