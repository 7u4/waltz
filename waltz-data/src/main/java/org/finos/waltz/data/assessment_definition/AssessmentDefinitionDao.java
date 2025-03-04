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

package org.finos.waltz.data.assessment_definition;


import org.finos.waltz.schema.tables.records.AssessmentDefinitionRecord;
import org.finos.waltz.common.StringUtilities;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.assessment_definition.AssessmentDefinition;
import org.finos.waltz.model.assessment_definition.AssessmentVisibility;
import org.finos.waltz.model.assessment_definition.ImmutableAssessmentDefinition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.finos.waltz.common.StringUtilities.isEmpty;
import static org.finos.waltz.schema.tables.AssessmentDefinition.ASSESSMENT_DEFINITION;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.DateTimeUtilities.toLocalDateTime;
import static org.finos.waltz.common.StringUtilities.mkSafe;


@Repository
public class AssessmentDefinitionDao {

    private static final RecordMapper<? super Record, AssessmentDefinition> TO_DOMAIN = r -> {
        AssessmentDefinitionRecord record = r.into(ASSESSMENT_DEFINITION);
        return ImmutableAssessmentDefinition.builder()
                .id(record.getId())
                .name(record.getName())
                .externalId(Optional.ofNullable(record.getExternalId()))
                .ratingSchemeId(record.getRatingSchemeId())
                .entityKind(EntityKind.valueOf(record.getEntityKind()))
                .description(mkSafe(record.getDescription()))
                .permittedRole(Optional.ofNullable(record.getPermittedRole()))
                .lastUpdatedAt(toLocalDateTime(record.getLastUpdatedAt()))
                .lastUpdatedBy(record.getLastUpdatedBy())
                .isReadOnly(record.getIsReadonly())
                .provenance(record.getProvenance())
                .visibility(AssessmentVisibility.valueOf(record.getVisibility()))
                .build();
    };


    private final DSLContext dsl;


    @Autowired
    public AssessmentDefinitionDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");
        this.dsl = dsl;
    }


    public AssessmentDefinition getById(long id) {
        return dsl
                .select(ASSESSMENT_DEFINITION.fields())
                .from(ASSESSMENT_DEFINITION)
                .where(ASSESSMENT_DEFINITION.ID.eq(id))
                .fetchOne(TO_DOMAIN);
    }


    public List<AssessmentDefinition> findAll() {
        return dsl
                .select(ASSESSMENT_DEFINITION.fields())
                .from(ASSESSMENT_DEFINITION)
                .fetch(TO_DOMAIN);
    }


    public List<AssessmentDefinition> findByEntityKind(EntityKind kind) {
        return dsl
                .select(ASSESSMENT_DEFINITION.fields())
                .from(ASSESSMENT_DEFINITION)
                .where(ASSESSMENT_DEFINITION.ENTITY_KIND.eq(kind.name()))
                .fetch(TO_DOMAIN);
    }


    /**
     * Saves the given assessment definition.  Either updating or inserting.
     * Returns the identifier for the record.
     * @param def the definition to save
     * @return the identifier for the record
     */
    public Long save(AssessmentDefinition def) {
        AssessmentDefinitionRecord r = dsl.newRecord(ASSESSMENT_DEFINITION);

        String permittedRole = def.permittedRole()
                .map(role -> !isEmpty(role) ? role : null)
                .orElse(null);

        r.setName(def.name());
        r.setEntityKind(def.entityKind().name());
        r.setRatingSchemeId(def.ratingSchemeId());

        r.setExternalId(def.externalId().orElse(null));
        r.setDescription(def.description());
        r.setVisibility(def.visibility().name());

        r.setIsReadonly(def.isReadOnly());
        r.setPermittedRole(permittedRole);

        r.setLastUpdatedAt(Timestamp.valueOf(def.lastUpdatedAt()));
        r.setLastUpdatedBy(def.lastUpdatedBy());
        r.setProvenance(StringUtilities.ifEmpty(def.provenance(), "waltz"));

        def.id().ifPresent(r::setId);

        if (r.getId() == null) {
            r.insert();
            return r.getId();
        } else {
            r.changed(ASSESSMENT_DEFINITION.ID, false);
            r.update();
            return r.getId();
        }

    }

    public int remove(long definitionId) {
        return dsl
                .deleteFrom(ASSESSMENT_DEFINITION)
                .where(ASSESSMENT_DEFINITION.ID.eq(definitionId))
                .execute();
    }
}
