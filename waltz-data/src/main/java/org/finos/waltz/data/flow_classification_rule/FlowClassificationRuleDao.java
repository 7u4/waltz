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

package org.finos.waltz.data.flow_classification_rule;

import org.finos.waltz.schema.tables.Application;
import org.finos.waltz.schema.tables.EntityHierarchy;
import org.finos.waltz.schema.tables.records.FlowClassificationRuleRecord;
import org.finos.waltz.data.InlineSelectFieldFactory;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.ImmutableEntityReference;
import org.finos.waltz.model.flow_classification_rule.*;
import org.finos.waltz.model.rating.AuthoritativenessRatingValue;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.finos.waltz.schema.Tables.FLOW_CLASSIFICATION;
import static org.finos.waltz.schema.Tables.FLOW_CLASSIFICATION_RULE;
import static org.finos.waltz.schema.tables.Application.APPLICATION;
import static org.finos.waltz.schema.tables.EntityHierarchy.ENTITY_HIERARCHY;
import static org.finos.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static org.finos.waltz.schema.tables.LogicalFlowDecorator.LOGICAL_FLOW_DECORATOR;
import static org.finos.waltz.schema.tables.OrganisationalUnit.ORGANISATIONAL_UNIT;
import static java.util.stream.Collectors.collectingAndThen;
import static org.finos.waltz.common.Checks.checkNotNull;
import static org.finos.waltz.common.Checks.checkTrue;
import static org.finos.waltz.common.DateTimeUtilities.nowUtcTimestamp;
import static org.finos.waltz.common.DateTimeUtilities.toLocalDateTime;
import static org.finos.waltz.common.ListUtilities.newArrayList;
import static org.finos.waltz.common.MapUtilities.groupBy;
import static org.finos.waltz.common.SetUtilities.union;
import static org.finos.waltz.data.application.ApplicationDao.IS_ACTIVE;
import static org.finos.waltz.model.EntityLifecycleStatus.REMOVED;
import static org.finos.waltz.model.EntityReference.mkRef;


@Repository
public class FlowClassificationRuleDao {

    public final static Application CONSUMER_APP = APPLICATION.as("consumer");
    public final static Application SUPPLIER_APP = APPLICATION.as("supplier");
    public static final org.finos.waltz.schema.tables.DataType parent_dt = org.finos.waltz.schema.tables.DataType.DATA_TYPE.as("parent_dt");
    public static final org.finos.waltz.schema.tables.DataType child_dt = org.finos.waltz.schema.tables.DataType.DATA_TYPE.as("child_dt");
    public static final EntityHierarchy eh = ENTITY_HIERARCHY.as("eh");
    public static final EntityHierarchy level = ENTITY_HIERARCHY.as("level");

    private final static AggregateFunction<Integer> COUNT_FIELD = DSL.count(LOGICAL_FLOW);

    private final static EntityHierarchy ehOrgUnit = ENTITY_HIERARCHY.as("ehOrgUnit");
    private final static EntityHierarchy ehDataType = ENTITY_HIERARCHY.as("ehDataType");
    private final static org.finos.waltz.schema.tables.DataType declaredDataType = org.finos.waltz.schema.tables.DataType.DATA_TYPE.as("declaredDataType");
    private final static org.finos.waltz.schema.tables.DataType impliedDataType = org.finos.waltz.schema.tables.DataType.DATA_TYPE.as("impliedDataType");

    private final static Field<Long> targetOrgUnitId = ehOrgUnit.ID.as("targetOrgUnitId");
    private final static Field<Integer> declaredOrgUnitLevel = ehOrgUnit.LEVEL.as("declaredOrgUnitLevel");
    private final static Field<Long> declaredDataTypeId = ehDataType.ID.as("declaredDataTypeId");
    private final static Field<Integer> declaredDataTypeLevel = ehDataType.LEVEL.as("declaredDataTypeLevel");

    private static final Field<String> PARENT_NAME_FIELD = InlineSelectFieldFactory.mkNameField(
            FLOW_CLASSIFICATION_RULE.PARENT_ID,
            FLOW_CLASSIFICATION_RULE.PARENT_KIND,
            newArrayList(EntityKind.ORG_UNIT, EntityKind.APPLICATION, EntityKind.ACTOR));

    private static final Condition flowNotRemoved = LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())
            .and(LOGICAL_FLOW.IS_REMOVED.isFalse());
    private static final Condition supplierNotRemoved =  SUPPLIER_APP.IS_REMOVED.isFalse();
    private static final Condition consumerNotRemoved =  CONSUMER_APP.IS_REMOVED.isFalse();

    private final DSLContext dsl;

    private static final RecordMapper<Record, FlowClassificationRule> TO_DOMAIN_MAPPER = r -> {
        FlowClassificationRuleRecord record = r.into(FlowClassificationRuleRecord.class);

        EntityReference parentRef = ImmutableEntityReference.builder()
                .id(record.getParentId())
                .kind(EntityKind.valueOf(record.getParentKind()))
                .name(r.get(PARENT_NAME_FIELD))
                .build();

        EntityReference orgUnitRef = ImmutableEntityReference.builder()
                .kind(EntityKind.ORG_UNIT)
                .id(r.getValue(ORGANISATIONAL_UNIT.ID))
                .name(r.getValue(ORGANISATIONAL_UNIT.NAME))
                .build();

        EntityReference appRef = ImmutableEntityReference.builder()
                .kind(EntityKind.APPLICATION)
                .id(r.getValue(SUPPLIER_APP.ID))
                .name(r.getValue(SUPPLIER_APP.NAME))
                .build();

        return ImmutableFlowClassificationRule.builder()
                .id(record.getId())
                .parentReference(parentRef)
                .appOrgUnitReference(orgUnitRef)
                .applicationReference(appRef)
                .dataTypeId(record.getDataTypeId())
                .classificationId(record.getFlowClassificationId())
                .description(record.getDescription())
                .provenance(record.getProvenance())
                .lastUpdatedAt(toLocalDateTime(record.getLastUpdatedAt()))
                .lastUpdatedBy(record.getLastUpdatedBy())
                .externalId(Optional.ofNullable(record.getExternalId()))
                .isReadonly(record.getIsReadonly())
                .build();
    };


    private static final RecordMapper<Record, FlowClassificationRuleVantagePoint> TO_VANTAGE_MAPPER = r -> ImmutableFlowClassificationRuleVantagePoint
            .builder()
            .vantagePoint(mkRef(EntityKind.ORG_UNIT, r.get(targetOrgUnitId)))
            .vantagePointRank(r.get(declaredOrgUnitLevel))
            .applicationId(r.get(FLOW_CLASSIFICATION_RULE.APPLICATION_ID))
            .classificationCode(r.get(FLOW_CLASSIFICATION.CODE))
            .dataType(mkRef(EntityKind.DATA_TYPE, r.get(declaredDataTypeId)))
            .dataTypeRank(r.get(declaredDataTypeLevel))
            .ruleId(r.get(FLOW_CLASSIFICATION_RULE.ID))
            .build();



    @Autowired
    public FlowClassificationRuleDao(DSLContext dsl) {
        checkNotNull(dsl, "dsl must not be null");
        this.dsl = dsl;
    }


    public List<FlowClassificationRule> findAll() {
        return baseSelect()
                .fetch(TO_DOMAIN_MAPPER);
    }


    public FlowClassificationRule getById(long id) {
        return baseSelect()
                .where(FLOW_CLASSIFICATION_RULE.ID.eq(id))
                .fetchOne(TO_DOMAIN_MAPPER);
    }


    public List<FlowClassificationRule> findByEntityKind(EntityKind kind) {
        checkNotNull(kind, "kind must not be null");
        
        return baseSelect()
                .where(FLOW_CLASSIFICATION_RULE.PARENT_KIND.eq(kind.name()))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<FlowClassificationRule> findByEntityReference(EntityReference ref) {
        checkNotNull(ref, "ref must not be null");

        return baseSelect()
                .where(FLOW_CLASSIFICATION_RULE.PARENT_KIND.eq(ref.kind().name()))
                .and(FLOW_CLASSIFICATION_RULE.PARENT_ID.eq(ref.id()))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public List<FlowClassificationRule> findByApplicationId(long applicationId) {
        checkTrue(applicationId > -1, "applicationId must be +ve");
        
        return baseSelect()
                .where(FLOW_CLASSIFICATION_RULE.APPLICATION_ID.eq(applicationId))
                .fetch(TO_DOMAIN_MAPPER);
    }


    public int update(FlowClassificationRuleUpdateCommand command) {
        checkNotNull(command, "command cannot be null");
        checkTrue(command.id().isPresent(), "id must be +ve");

        UpdateSetMoreStep<FlowClassificationRuleRecord> upd = dsl
                .update(FLOW_CLASSIFICATION_RULE)
                .set(FLOW_CLASSIFICATION_RULE.FLOW_CLASSIFICATION_ID, command.classificationId())
                .set(FLOW_CLASSIFICATION_RULE.DESCRIPTION, command.description());

        return upd
                .where(FLOW_CLASSIFICATION_RULE.ID.eq(command.id().get()))
                .execute();
    }


    public long insert(FlowClassificationRuleCreateCommand command, String username) {
        checkNotNull(command, "command cannot be null");

        return dsl
                .insertInto(FLOW_CLASSIFICATION_RULE)
                .set(FLOW_CLASSIFICATION_RULE.PARENT_KIND, command.parentReference().kind().name())
                .set(FLOW_CLASSIFICATION_RULE.PARENT_ID, command.parentReference().id())
                .set(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID, command.dataTypeId())
                .set(FLOW_CLASSIFICATION_RULE.APPLICATION_ID, command.applicationId())
                .set(FLOW_CLASSIFICATION_RULE.FLOW_CLASSIFICATION_ID, command.classificationId())
                .set(FLOW_CLASSIFICATION_RULE.DESCRIPTION, command.description())
                .set(FLOW_CLASSIFICATION_RULE.PROVENANCE, "waltz")
                .set(FLOW_CLASSIFICATION_RULE.LAST_UPDATED_AT, nowUtcTimestamp())
                .set(FLOW_CLASSIFICATION_RULE.LAST_UPDATED_BY, username)
                .returning(FLOW_CLASSIFICATION_RULE.ID)
                .fetchOne()
                .getId();
    }


    public int remove(long id) {
        return dsl
                .delete(FLOW_CLASSIFICATION_RULE)
                .where(FLOW_CLASSIFICATION_RULE.ID.eq(id))
                .execute();
    }


    public Set<EntityReference> cleanupOrphans() {
        Select<Record1<Long>> orgUnitIds = DSL
                .select(ORGANISATIONAL_UNIT.ID)
                .from(ORGANISATIONAL_UNIT);

        Select<Record1<Long>> appIds = DSL
                .select(APPLICATION.ID)
                .from(APPLICATION)
                .where(IS_ACTIVE);

        Condition unknownOrgUnit = FLOW_CLASSIFICATION_RULE.PARENT_ID.notIn(orgUnitIds)
                .and(FLOW_CLASSIFICATION_RULE.PARENT_KIND.eq(EntityKind.ORG_UNIT.name()));

        Condition appIsInactive = FLOW_CLASSIFICATION_RULE.APPLICATION_ID.notIn(appIds);

        Set<EntityReference> appsInRulesWithoutOrgUnit = dsl
                .select(FLOW_CLASSIFICATION_RULE.APPLICATION_ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .where(unknownOrgUnit)
                .fetchSet(r -> mkRef(EntityKind.APPLICATION, r.get(FLOW_CLASSIFICATION_RULE.ID)));

        Set<EntityReference> orgUnitsInRulesWithoutApp = dsl
                .select(FLOW_CLASSIFICATION_RULE.PARENT_ID, FLOW_CLASSIFICATION_RULE.PARENT_KIND)
                .from(FLOW_CLASSIFICATION_RULE)
                .where(appIsInactive)
                .fetchSet(r -> mkRef(
                        EntityKind.valueOf(r.get(FLOW_CLASSIFICATION_RULE.PARENT_KIND)),
                        r.get(FLOW_CLASSIFICATION_RULE.PARENT_ID)));

        Set<EntityReference> bereaved = union(appsInRulesWithoutOrgUnit, orgUnitsInRulesWithoutApp);

        int deleted = dsl
                .deleteFrom(FLOW_CLASSIFICATION_RULE)
                .where(unknownOrgUnit)
                .or(appIsInactive)
                .execute();

        return bereaved;
    }


    public int clearRatingsForPointToPointFlows(FlowClassificationRule rule) {

        // this may wipe any lower level explicit datatype mappings but these will be restored by the nightly job
        SelectConditionStep<Record1<Long>> decoratorsToMarkAsNoOpinion = dsl
                .select(LOGICAL_FLOW_DECORATOR.ID)
                .from(LOGICAL_FLOW)
                .innerJoin(LOGICAL_FLOW_DECORATOR).on(LOGICAL_FLOW.ID.eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID))
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name()))
                .innerJoin(ENTITY_HIERARCHY).on(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.eq(ENTITY_HIERARCHY.ID))
                .and(ENTITY_HIERARCHY.KIND.eq(EntityKind.DATA_TYPE.name()))
                .innerJoin(org.finos.waltz.schema.tables.DataType.DATA_TYPE).on(ENTITY_HIERARCHY.ANCESTOR_ID.eq(org.finos.waltz.schema.tables.DataType.DATA_TYPE.ID))
                .innerJoin(FLOW_CLASSIFICATION).on(LOGICAL_FLOW_DECORATOR.RATING.eq(FLOW_CLASSIFICATION.CODE))
                .where(dsl.renderInlined(org.finos.waltz.schema.tables.DataType.DATA_TYPE.ID.eq(rule.dataTypeId())
                        .and(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(rule.applicationReference().id())
                                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name())
                                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(rule.parentReference().kind().name())
                                                .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(rule.parentReference().id()))))
                                .and(FLOW_CLASSIFICATION.ID.eq(rule.classificationId())))));

        return dsl
                .update(LOGICAL_FLOW_DECORATOR)
                .set(LOGICAL_FLOW_DECORATOR.RATING, AuthoritativenessRatingValue.NO_OPINION.value())
                .setNull(LOGICAL_FLOW_DECORATOR.FLOW_CLASSIFICATION_RULE_ID)
                .where(LOGICAL_FLOW_DECORATOR.ID.in(decoratorsToMarkAsNoOpinion))
                .execute();
    }


    public List<FlowClassificationRuleVantagePoint> findExpandedFlowClassificationRuleVantagePoints(Set<Long> orgIds) {
        SelectSeekStep3<Record7<Long, Integer, Long, Integer, Long, String, Long>, Integer, Integer, Long> select = dsl
                .select(targetOrgUnitId,
                        declaredOrgUnitLevel,
                        declaredDataTypeId,
                        declaredDataTypeLevel,
                        FLOW_CLASSIFICATION_RULE.APPLICATION_ID,
                        FLOW_CLASSIFICATION.CODE,
                        FLOW_CLASSIFICATION_RULE.ID)
                .from(ehOrgUnit)
                .innerJoin(FLOW_CLASSIFICATION_RULE)
                    .on(ehOrgUnit.ANCESTOR_ID.eq(FLOW_CLASSIFICATION_RULE.PARENT_ID).and(ehOrgUnit.KIND.eq(EntityKind.ORG_UNIT.name())))
                .innerJoin(declaredDataType)
                    .on(declaredDataType.ID.eq(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID))
                .innerJoin(ehDataType)
                    .on(ehDataType.ANCESTOR_ID.eq(declaredDataType.ID).and(ehDataType.KIND.eq(EntityKind.DATA_TYPE.name())))
                .innerJoin(impliedDataType)
                    .on(impliedDataType.ID.eq(ehDataType.ID).and(ehDataType.KIND.eq(EntityKind.DATA_TYPE.name())))
                .innerJoin(FLOW_CLASSIFICATION).on(FLOW_CLASSIFICATION_RULE.FLOW_CLASSIFICATION_ID.eq(FLOW_CLASSIFICATION.ID))
                .where(ehOrgUnit.ID.in(orgIds))
                .orderBy(ehOrgUnit.LEVEL.desc(), ehDataType.LEVEL.desc(), ehOrgUnit.ID);

        return select
                .fetch(TO_VANTAGE_MAPPER);
    }


    public List<FlowClassificationRuleVantagePoint> findFlowClassificationRuleVantagePoints() {
        SelectSeekStep4<Record7<Long, Integer, Long, Integer, Long, String, Long>, Integer, Integer, Long, Long> select = dsl
                .select(targetOrgUnitId,
                        declaredOrgUnitLevel,
                        declaredDataTypeId,
                        declaredDataTypeLevel,
                        FLOW_CLASSIFICATION_RULE.APPLICATION_ID,
                        FLOW_CLASSIFICATION.CODE,
                        FLOW_CLASSIFICATION_RULE.ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .innerJoin(ehOrgUnit)
                .on(ehOrgUnit.ANCESTOR_ID.eq(FLOW_CLASSIFICATION_RULE.PARENT_ID)
                        .and(ehOrgUnit.KIND.eq(EntityKind.ORG_UNIT.name()))
                        .and(ehOrgUnit.ID.eq(ehOrgUnit.ANCESTOR_ID)))
                .innerJoin(org.finos.waltz.schema.tables.DataType.DATA_TYPE)
                .on(org.finos.waltz.schema.tables.DataType.DATA_TYPE.ID.eq(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID))
                .innerJoin(ehDataType)
                .on(ehDataType.ANCESTOR_ID.eq(org.finos.waltz.schema.tables.DataType.DATA_TYPE.ID)
                        .and(ehDataType.KIND.eq(EntityKind.DATA_TYPE.name()))
                        .and(ehDataType.ID.eq(ehDataType.ANCESTOR_ID)))
                .innerJoin(FLOW_CLASSIFICATION).on(FLOW_CLASSIFICATION_RULE.FLOW_CLASSIFICATION_ID.eq(FLOW_CLASSIFICATION.ID))
                .orderBy(
                        ehOrgUnit.LEVEL.desc(),
                        ehDataType.LEVEL.desc(),
                        ehOrgUnit.ID,
                        ehDataType.ID
                );
        return select.fetch(TO_VANTAGE_MAPPER);
    }


    public Map<EntityReference, Collection<EntityReference>> calculateConsumersForDataTypeIdSelector(
            Select<Record1<Long>> dataTypeIdSelector) {

        Condition appJoin = APPLICATION.ID.eq(LOGICAL_FLOW.TARGET_ENTITY_ID)
                .and(APPLICATION.ORGANISATIONAL_UNIT_ID.eq(ENTITY_HIERARCHY.ID));

        Condition hierarchyJoin = ENTITY_HIERARCHY.ANCESTOR_ID.eq(FLOW_CLASSIFICATION_RULE.PARENT_ID)
                .and(ENTITY_HIERARCHY.KIND.eq(EntityKind.ORG_UNIT.name()));

        Condition flowClassificationRuleJoin = FLOW_CLASSIFICATION_RULE.APPLICATION_ID.eq(LOGICAL_FLOW.SOURCE_ENTITY_ID)
                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()));

        Condition condition = LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.in(dataTypeIdSelector)
                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name()))
                .and(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID.in(dataTypeIdSelector))
                .and(flowNotRemoved);

        Field<Long> classificationRuleIdField = FLOW_CLASSIFICATION_RULE.ID.as("classification_rule_id");
        Field<Long> applicationIdField = APPLICATION.ID.as("application_id");
        Field<String> applicationNameField = APPLICATION.NAME.as("application_name");

        SelectSeekStep2<Record3<Long, Long, String>, Long, String> qry = dsl
                .select(classificationRuleIdField,
                        applicationIdField,
                        applicationNameField)
                .from(LOGICAL_FLOW)
                .innerJoin(LOGICAL_FLOW_DECORATOR).on(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(LOGICAL_FLOW.ID))
                .innerJoin(FLOW_CLASSIFICATION_RULE).on(flowClassificationRuleJoin)
                .innerJoin(ENTITY_HIERARCHY).on(hierarchyJoin)
                .innerJoin(APPLICATION).on(appJoin)
                .where(condition)
                .orderBy(FLOW_CLASSIFICATION_RULE.ID, APPLICATION.NAME);

        Result<Record3<Long, Long, String>> records = qry
                .fetch();

        return groupBy(
                r -> mkRef(
                        EntityKind.FLOW_CLASSIFICATION_RULE,
                        r.getValue(classificationRuleIdField)),
                r -> mkRef(
                        EntityKind.APPLICATION,
                        r.getValue(applicationIdField),
                        r.getValue(applicationNameField)),
                records);
    }


    public List<DiscouragedSource> findDiscouragedSourcesBySelector(Condition customSelectionCriteria) {

        Condition decorationIsAboutDataTypes = LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name());

        Condition badFlow = LOGICAL_FLOW_DECORATOR.RATING.in(
                AuthoritativenessRatingValue.DISCOURAGED.value(),
                AuthoritativenessRatingValue.NO_OPINION.value());

        Condition commonSelectionCriteria = flowNotRemoved
                .and(consumerNotRemoved)
                .and(supplierNotRemoved)
                .and(decorationIsAboutDataTypes)
                .and(badFlow);

        return dsl
                .select(SUPPLIER_APP.ID, SUPPLIER_APP.NAME)
                .select(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID)
                .select(DSL.count(LOGICAL_FLOW))
                .from(SUPPLIER_APP)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(SUPPLIER_APP.ID)
                        .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                .innerJoin(LOGICAL_FLOW_DECORATOR)
                .on(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID.eq(LOGICAL_FLOW.ID))
                .innerJoin(CONSUMER_APP)
                .on(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(CONSUMER_APP.ID)
                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                .where(customSelectionCriteria).and(commonSelectionCriteria)
                .groupBy(SUPPLIER_APP.ID, SUPPLIER_APP.NAME, LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID)
                .fetch()
                .map(r -> ImmutableDiscouragedSource.builder()
                            .sourceReference(mkRef(
                                    EntityKind.APPLICATION,
                                    r.get(SUPPLIER_APP.ID),
                                    r.get(SUPPLIER_APP.NAME)))
                            .dataTypeId(r.get(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID))
                            .count(r.get(COUNT_FIELD))
                            .build());
    }


    public Set<FlowClassificationRule> findClassificationRules(Condition customSelectionCriteria) {

        SelectConditionStep<Record1<Long>> ruleSelectorBasedOnCustomSelectionForTargetApps = DSL
                .select(FLOW_CLASSIFICATION_RULE.ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .innerJoin(LOGICAL_FLOW)
                .on(LOGICAL_FLOW.SOURCE_ENTITY_ID.eq(FLOW_CLASSIFICATION_RULE.APPLICATION_ID)
                        .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name()))
                        .and(LOGICAL_FLOW.ENTITY_LIFECYCLE_STATUS.ne(REMOVED.name())
                                .and(LOGICAL_FLOW.IS_REMOVED.isFalse())))
                .innerJoin(CONSUMER_APP).on(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(CONSUMER_APP.ID)
                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(EntityKind.APPLICATION.name())))
                .where(customSelectionCriteria);

        Condition criteria = FLOW_CLASSIFICATION_RULE.ID.in(ruleSelectorBasedOnCustomSelectionForTargetApps);

        return baseSelect()
                .where(criteria)
                .fetchSet(TO_DOMAIN_MAPPER);
    }


    // -- HELPERS --

    private SelectOnConditionStep<Record> baseSelect() {
        return dsl
                .select(PARENT_NAME_FIELD)
                .select(ORGANISATIONAL_UNIT.ID, ORGANISATIONAL_UNIT.NAME)
                .select(FLOW_CLASSIFICATION_RULE.fields())
                .select(SUPPLIER_APP.NAME, SUPPLIER_APP.ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .innerJoin(SUPPLIER_APP)
                .on(SUPPLIER_APP.ID.eq(FLOW_CLASSIFICATION_RULE.APPLICATION_ID))
                .innerJoin(ORGANISATIONAL_UNIT)
                .on(ORGANISATIONAL_UNIT.ID.eq(SUPPLIER_APP.ORGANISATIONAL_UNIT_ID));
    }


    public int updatePointToPointFlowClassificationRules() {

        Condition logicalFlowTargetIsAuthSourceParent = FLOW_CLASSIFICATION_RULE.APPLICATION_ID.eq(LOGICAL_FLOW.SOURCE_ENTITY_ID)
                .and(LOGICAL_FLOW.SOURCE_ENTITY_KIND.eq(EntityKind.APPLICATION.name())
                        .and(LOGICAL_FLOW.TARGET_ENTITY_KIND.eq(FLOW_CLASSIFICATION_RULE.PARENT_KIND)
                                .and(LOGICAL_FLOW.TARGET_ENTITY_ID.eq(FLOW_CLASSIFICATION_RULE.PARENT_ID))));

        int[] updatedActorDecoratorRatings = dsl
                .select(LOGICAL_FLOW_DECORATOR.ID,
                        child_dt.ID,
                        FLOW_CLASSIFICATION.CODE,
                        FLOW_CLASSIFICATION_RULE.ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .innerJoin(parent_dt).on(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID.eq(parent_dt.ID))
                .innerJoin(ENTITY_HIERARCHY).on(parent_dt.ID.eq(ENTITY_HIERARCHY.ANCESTOR_ID)
                        .and(ENTITY_HIERARCHY.KIND.eq(EntityKind.DATA_TYPE.name())))
                .innerJoin(child_dt).on(ENTITY_HIERARCHY.ID.eq(child_dt.ID))
                .innerJoin(LOGICAL_FLOW).on(logicalFlowTargetIsAuthSourceParent)
                .innerJoin(LOGICAL_FLOW_DECORATOR).on(LOGICAL_FLOW.ID.eq(LOGICAL_FLOW_DECORATOR.LOGICAL_FLOW_ID)
                        .and(child_dt.ID.eq(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID)
                                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name()))))
                .innerJoin(level).on(level.ID.eq(parent_dt.ID)
                        .and(level.ID.eq(level.ANCESTOR_ID)
                        .and(level.KIND.eq(EntityKind.DATA_TYPE.name()))))
                .innerJoin(FLOW_CLASSIFICATION).on(FLOW_CLASSIFICATION_RULE.FLOW_CLASSIFICATION_ID.eq(FLOW_CLASSIFICATION.ID))
                .where(FLOW_CLASSIFICATION.CODE.ne(LOGICAL_FLOW_DECORATOR.RATING))
                .orderBy(level.LEVEL)
                .fetch()
                .stream()
                .map(r -> dsl
                        .update(LOGICAL_FLOW_DECORATOR)
                        .set(LOGICAL_FLOW_DECORATOR.RATING, r.get(FLOW_CLASSIFICATION.CODE))
                        .set(LOGICAL_FLOW_DECORATOR.FLOW_CLASSIFICATION_RULE_ID, r.get(FLOW_CLASSIFICATION_RULE.ID))
                        .where(LOGICAL_FLOW_DECORATOR.ID.eq(r.get(LOGICAL_FLOW_DECORATOR.ID))
                                .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_ID.eq(r.get(child_dt.ID))
                                        .and(LOGICAL_FLOW_DECORATOR.DECORATOR_ENTITY_KIND.eq(EntityKind.DATA_TYPE.name())))))
                .collect(collectingAndThen(Collectors.toSet(), r -> dsl.batch(r).execute()));

        return IntStream.of(updatedActorDecoratorRatings).sum();
    }


    public Set<FlowClassificationRule> findCompanionAppRules(long ruleId) {
        SelectConditionStep<Record1<Long>> sourceAppId = DSL
                .select(FLOW_CLASSIFICATION_RULE.APPLICATION_ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .where(FLOW_CLASSIFICATION_RULE.ID.eq(ruleId));

        return baseSelect()
                .where(SUPPLIER_APP.ID.eq(sourceAppId)
                        .and(FLOW_CLASSIFICATION_RULE.ID.ne(ruleId)))
                .fetchSet(TO_DOMAIN_MAPPER);
    }


    public Set<FlowClassificationRule> findCompanionDataTypeRules(long ruleId) {
        SelectConditionStep<Record1<Long>> dataTypeIds = DSL
                .select(ENTITY_HIERARCHY.ANCESTOR_ID)
                .from(FLOW_CLASSIFICATION_RULE)
                .innerJoin(ENTITY_HIERARCHY)
                .on(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID.eq(ENTITY_HIERARCHY.ID)
                        .and(ENTITY_HIERARCHY.KIND.eq(EntityKind.DATA_TYPE.name())))
                .where(FLOW_CLASSIFICATION_RULE.ID.eq(ruleId));

        return baseSelect()
                .where(FLOW_CLASSIFICATION_RULE.DATA_TYPE_ID.in(dataTypeIds)
                        .and(FLOW_CLASSIFICATION_RULE.ID.ne(ruleId)))
                .fetchSet(TO_DOMAIN_MAPPER);
    }
}
