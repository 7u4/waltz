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

import _ from "lodash";
import {CORE_API} from "../../../common/services/core-api-utils";
import {mkSelectionOptions} from "../../../common/selector-utils";
import {entityLifecycleStatus} from "../../../common/services/enums/entity-lifecycle-status";

import template from "./logical-flows-boingy-graph.html";
import {buildHierarchies, findNode, flattenChildren} from "../../../common/hierarchy-utils";

const bindings = {
    filters: "<",
    parentEntityRef: "<"
};


const defaultFilterOptions = {
    type: "ALL",
    scope: "ALL"
};


const defaultOptions = {
    graphTweakers: {
        node : {
            enter: (selection) => console.log("default graphTweaker.node.entry, selection: ", selection),
        },
        link : {
            enter: (selection) => selection.attr("stroke", "red")
        }
    }
};


const initialState = {
    applications: [],
    flows: [],
    decorators: [],
    usedDataTypes: [],
    filterOptions: defaultFilterOptions,
    options: defaultOptions,
    visibility: {
        boingyEverShown: false,
        ignoreLimits: false,
        summaries: false,
        loadingFlows: false,
        loadingStats: false
    }
};


function calculateEntities(flows = []) {
    return _.chain(flows)
        .flatMap(f => [f.source, f.target])
        .uniqBy("id")
        .value();
}


function mkScopeFilterFn(appIds = [], scope = "INTRA") {
    return (f) => {
        switch (scope) {
            case "INTRA":
                return _.includes(appIds, f.target.id) && _.includes(appIds, f.source.id);
            case "ALL":
                return true;
            case "INBOUND":
                return _.includes(appIds, f.target.id);
            case "OUTBOUND":
                return _.includes(appIds, f.source.id);
        }
    };
}


function mkTypeFilterFn(decorators = []) {
    const flowIds = _.chain(decorators)
        .map("dataFlowId")
        .uniq()
        .value();
    return f => _.includes(flowIds, f.id);
}


function buildFlowFilter(filterOptions = defaultFilterOptions,
                         appIds = [],
                         flowDecorators = []) {

    const typeFilterFn = mkTypeFilterFn(flowDecorators);
    const scopeFilterFn = mkScopeFilterFn(appIds, filterOptions.scope);
    return f => typeFilterFn(f) && scopeFilterFn(f);
}


function buildDecoratorFilter(options = defaultFilterOptions) {
    const datatypeIds = _.map(options.type, id => (id === "ALL") ? "ALL" : Number(id));
    return d => {
        const isDataType = d.decoratorEntity.kind === "DATA_TYPE";
        const matchesDataType = _.includes(datatypeIds, "ALL") || _.includes(datatypeIds, d.decoratorEntity.id);
        return isDataType && matchesDataType;
    };
}


function calculateFlowData(allFlows = [],
                           applications = [],
                           allDecorators = [],
                           filterOptions = defaultFilterOptions) {
    // note order is important.  We need to find decorators first
    const decoratorFilterFn = buildDecoratorFilter(filterOptions);
    const decorators = _.filter(allDecorators, decoratorFilterFn);

    const appIds = _.map(applications, d => d.id);
    const flowFilterFn = buildFlowFilter(filterOptions, appIds, decorators);
    const flows = _.filter(allFlows, flowFilterFn);

    const entities = calculateEntities(flows);

    return {flows, entities, decorators};
}


function getDataTypeIds(allDataTypes = [], decorators = []) {
    const dataTypesById = _.keyBy(allDataTypes, "id");
    return _.chain(decorators)
        .filter(dc => dc.decoratorEntity.kind === "DATA_TYPE")
        .map("decoratorEntity.id")
        .uniq()
        .map(dtId => dataTypesById[dtId])
        .orderBy("name")
        .value();
}


function prepareGraphTweakers(logicalFlowUtilityService,
                              applications = [],
                              decorators = [])
{
    const appIds = _.map(applications, "id");
    return logicalFlowUtilityService.buildGraphTweakers(appIds, decorators);
}


function controller($scope,
                    $q,
                    serviceBroker,
                    logicalFlowUtilityService) {

    const vm = _.defaultsDeep(this, initialState);

    const loadDetail = () => {
        vm.visibility.loadingFlows = true;

        const flowPromise = serviceBroker
            .loadViewData(
                CORE_API.LogicalFlowStore.findBySelector,
                [ vm.selector ])
            .then(r => vm.flows = r.data);

        const decoratorPromise = serviceBroker
            .loadViewData(
                CORE_API.LogicalFlowDecoratorStore.findBySelector,
                [ vm.selector ])
            .then(r => {
                vm.decorators = r.data;
                vm.usedDataTypes = getDataTypeIds(vm.allDataTypes, vm.decorators);
            });

        const appsPromise = serviceBroker
            .loadViewData(
                CORE_API.ApplicationStore.findBySelector,
                [ vm.selector ])
            .then(r => vm.applications = r.data);

        return $q
            .all([flowPromise, decoratorPromise, appsPromise])
            .then(() => {
                vm.filterChanged();
                vm.visibility.loadingFlows = false;
            });
    };

    vm.filterChanged = (filterOptions = vm.filterOptions) => {

        const dataTypes = buildHierarchies(vm.allDataTypes, true);
        const node = findNode(dataTypes, Number(filterOptions.type));

        const children = (node)
            ? _.map(flattenChildren(node),d => d.id)
            : [];

        vm.filterOptions = filterOptions;

        vm.filterOptions.type = _.concat(filterOptions.type, children);

        if (! (vm.flows && vm.decorators)) return;

        vm.filteredFlowData = calculateFlowData(
            vm.flows,
            vm.applications,
            vm.decorators,
            vm.filterOptions);

        vm.graphTweakers = prepareGraphTweakers(
            logicalFlowUtilityService,
            vm.applications,
            vm.filteredFlowData.decorators);
    };


    vm.$onChanges = () => {
        if (vm.parentEntityRef) {
            vm.selector = mkSelectionOptions(
                vm.parentEntityRef,
                undefined,
                [entityLifecycleStatus.ACTIVE.key],
                vm.filters);
            loadDetail();
        }
    };

    vm.$onInit = () => {
        serviceBroker
            .loadAppData(CORE_API.DataTypeStore.findAll)
            .then(r => vm.allDataTypes = r.data);
    };

}


controller.$inject = [
    "$scope",
    "$q",
    "ServiceBroker",
    "LogicalFlowUtilityService"
];


const component = {
    controller,
    bindings,
    template
};


export default {
    component,
    id: "waltzLogicalFlowsBoingyGraph"
};
