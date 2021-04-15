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


export function store($http, BaseApiUrl) {

    const BASE = `${BaseApiUrl}/complexity`;

    const findByEntityReference = (ref) => {
        return $http
            .get(`${BASE}/entity/kind/${ref.kind}/id/${ref.id}`)
            .then(result => result.data);
    };

    const findBySelector = (targetKind, selectionOptions) => {
        return $http
            .post(`${BASE}/target-kind/${targetKind}`, selectionOptions)
            .then(r => r.data);
    };

    const findTotalsByTargetKindAndSelector = (targetKind, selectionOptions) => {
        return $http
            .post(`${BASE}/target-kind/${targetKind}/totals`, selectionOptions)
            .then(r => r.data);
    };

    const findByTopComplexitiesSummaryByTargetKindAndSelector = (complexityKindId, targetKind, selectionOptions) => {
        return $http
            .post(`${BASE}/complexity-kind/${complexityKindId}/target-kind/${targetKind}`, selectionOptions)
            .then(r => r.data);
    };


    return {
        findByEntityReference,
        findBySelector,
        findByTopComplexitiesSummaryByTargetKindAndSelector,
        findTotalsByTargetKindAndSelector
    };
}

store.$inject = [
    '$http',
    'BaseApiUrl',
];

export const serviceName = 'ComplexityStore';

export const ComplexityStore_API = {
    findByEntityReference: {
        serviceName,
        serviceFnName: 'findByEntityReference',
        description: 'executes findByEntityReference (ref) '
    },
    findBySelector: {
        serviceName,
        serviceFnName: 'findBySelector',
        description: 'executes findBySelector (targetKind, selectionOptions) '
    },
    findByTopComplexitiesSummaryByTargetKindAndSelector: {
        serviceName,
        serviceFnName: 'findByTopComplexitiesSummaryByTargetKindAndSelector',
        description: 'executes findByTopComplexitiesSummaryByTargetKindAndSelector (complexityKindId, targetKind, selectionOptions) '
    },
    findTotalsByTargetKindAndSelector: {
        serviceName,
        serviceFnName: 'findTotalsByTargetKindAndSelector',
        description: 'executes findTotalsByTargetKindAndSelector (targetKind, selectionOptions) '
    }
};


