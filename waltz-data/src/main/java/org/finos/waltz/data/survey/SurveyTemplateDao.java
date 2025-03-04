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

package org.finos.waltz.data.survey;


import org.finos.waltz.schema.tables.records.SurveyTemplateRecord;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.ReleaseLifecycleStatus;
import org.finos.waltz.model.survey.ImmutableSurveyTemplate;
import org.finos.waltz.model.survey.SurveyTemplate;
import org.finos.waltz.model.survey.SurveyTemplateChangeCommand;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.finos.waltz.schema.Tables.SURVEY_QUESTION;
import static org.finos.waltz.schema.tables.SurveyTemplate.SURVEY_TEMPLATE;
import static org.finos.waltz.common.Checks.checkNotNull;

@Repository
public class SurveyTemplateDao {

    public static final RecordMapper<Record, SurveyTemplate> TO_DOMAIN_MAPPER = r -> {
        SurveyTemplateRecord record = r.into(SURVEY_TEMPLATE);

        return ImmutableSurveyTemplate.builder()
                .id(record.getId())
                .name(record.getName())
                .description(record.getDescription())
                .targetEntityKind(EntityKind.valueOf(record.getTargetEntityKind()))
                .ownerId(record.getOwnerId())
                .createdAt(record.getCreatedAt().toLocalDateTime())
                .status(ReleaseLifecycleStatus.valueOf(record.getStatus()))
                .externalId(Optional.ofNullable(record.getExternalId()))
                .build();
    };


    public static final Function<SurveyTemplate, SurveyTemplateRecord> TO_RECORD_MAPPER = template -> {
        SurveyTemplateRecord record = new SurveyTemplateRecord();
        record.setName(template.name());
        record.setOwnerId(template.ownerId());
        record.setDescription(template.description());
        record.setTargetEntityKind(template.targetEntityKind().name());
        record.setCreatedAt(Timestamp.valueOf(template.createdAt()));
        record.setStatus(template.status().name());
        record.setExternalId(template.externalId().orElse(null));

        return record;
    };


    private final DSLContext dsl;


    @Autowired
    public SurveyTemplateDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl cannot be null");

        this.dsl = dsl;
    }


    public SurveyTemplate getById(long id) {
        return dsl.select()
                .from(SURVEY_TEMPLATE)
                .where(SURVEY_TEMPLATE.ID.eq(id))
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    /**
     * @param ownerId
     * @return Returns all 'ACTIVE' templates (owned by any user)
     * and all 'DRAFT' templates owned by the specified user
     */
    public List<SurveyTemplate> findAll(Long ownerId) {

        Condition canViewSurveyCondition = ownerId != null
                ? SURVEY_TEMPLATE.STATUS.eq(ReleaseLifecycleStatus.ACTIVE.name())
                .or(SURVEY_TEMPLATE.STATUS.eq(ReleaseLifecycleStatus.DRAFT.name()).and(SURVEY_TEMPLATE.OWNER_ID.eq(ownerId)))
                : SURVEY_TEMPLATE.STATUS.eq(ReleaseLifecycleStatus.ACTIVE.name());

        return dsl
                .select()
                .from(SURVEY_TEMPLATE)
                .where(canViewSurveyCondition)
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<SurveyTemplate> findAllActive() {
        return dsl.select()
                .from(SURVEY_TEMPLATE)
                .where(SURVEY_TEMPLATE.STATUS.eq(ReleaseLifecycleStatus.ACTIVE.name()))
                .fetch(TO_DOMAIN_MAPPER);
    }

    
    public long create(SurveyTemplate surveyTemplate) {
        checkNotNull(surveyTemplate, "surveyTemplate cannot be null");
        
        SurveyTemplateRecord record = TO_RECORD_MAPPER.apply(surveyTemplate);
        return dsl.insertInto(SURVEY_TEMPLATE)
                .set(record)
                .returning(SURVEY_TEMPLATE.ID)
                .fetchOne()
                .getId();
    }


    public int update(SurveyTemplateChangeCommand command) {
        checkNotNull(command, "command cannot be null");

        return dsl
                .update(SURVEY_TEMPLATE)
                .set(SURVEY_TEMPLATE.NAME, command.name())
                .set(SURVEY_TEMPLATE.DESCRIPTION, command.description())
                .set(SURVEY_TEMPLATE.EXTERNAL_ID, command.externalId().orElse(null))
                .set(SURVEY_TEMPLATE.TARGET_ENTITY_KIND, command.targetEntityKind().name())
                .where(SURVEY_TEMPLATE.ID.eq(command.id().get()))
                .execute();
    }


    public int updateStatus(long templateId, ReleaseLifecycleStatus newStatus) {
        checkNotNull(newStatus, "newStatus cannot be null");

        return dsl
                .update(SURVEY_TEMPLATE)
                .set(SURVEY_TEMPLATE.STATUS, newStatus.name())
                .where(SURVEY_TEMPLATE.STATUS.notEqual(newStatus.name())
                        .and(SURVEY_TEMPLATE.ID.eq(templateId)))
                .execute();
    }


    public boolean delete(long id) {
        return dsl
                .deleteFrom(SURVEY_TEMPLATE)
                .where(SURVEY_TEMPLATE.ID.eq(id))
                .and(SURVEY_TEMPLATE.STATUS.eq(ReleaseLifecycleStatus.DRAFT.name()))
                .execute() == 1;
    }


    public SurveyTemplate getByQuestionId(long questionId) {
        return dsl
                .select()
                .from(SURVEY_TEMPLATE)
                .innerJoin(SURVEY_QUESTION)
                .on(SURVEY_TEMPLATE.ID.eq(SURVEY_QUESTION.SURVEY_TEMPLATE_ID))
                .where(SURVEY_QUESTION.ID.eq(questionId))
                .fetchOne(TO_DOMAIN_MAPPER);
    }
}
