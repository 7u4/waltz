package org.finos.waltz.integration_test.inmem.story;

import org.finos.waltz.integration_test.inmem.BaseInMemoryIntegrationTest;
import org.finos.waltz.integration_test.inmem.helpers.AppHelper;
import org.finos.waltz.integration_test.inmem.helpers.ChangeLogHelper;
import org.finos.waltz.integration_test.inmem.helpers.NameHelper;
import org.finos.waltz.integration_test.inmem.helpers.RatingSchemeHelper;
import org.finos.waltz.model.EntityKind;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.Operation;
import org.finos.waltz.model.assessment_definition.AssessmentDefinition;
import org.finos.waltz.model.assessment_definition.AssessmentVisibility;
import org.finos.waltz.model.assessment_definition.ImmutableAssessmentDefinition;
import org.finos.waltz.model.assessment_rating.AssessmentRating;
import org.finos.waltz.model.assessment_rating.ImmutableRemoveAssessmentRatingCommand;
import org.finos.waltz.model.assessment_rating.ImmutableSaveAssessmentRatingCommand;
import org.finos.waltz.model.assessment_rating.SaveAssessmentRatingCommand;
import org.finos.waltz.model.rating.ImmutableRatingSchemeItem;
import org.finos.waltz.service.assessment_definition.AssessmentDefinitionService;
import org.finos.waltz.service.assessment_rating.AssessmentRatingService;
import org.finos.waltz.service.rating_scheme.RatingSchemeService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.finos.waltz.common.CollectionUtilities.find;
import static org.junit.Assert.*;

public class AssessmentsTest extends BaseInMemoryIntegrationTest {

    @Autowired
    private AppHelper appHelper;

    @Autowired
    private AssessmentDefinitionService definitionService;

    @Autowired
    private AssessmentRatingService ratingService;

    @Autowired
    private RatingSchemeService schemeService;

    @Autowired
    private RatingSchemeHelper schemeHelper;

    @Autowired
    private ChangeLogHelper changeLogHelper;

    @Test
    public void createUpdateAndRemoveSingleRating() {
        String user = NameHelper.mkUserId("user");
        String name = NameHelper.mkName("testAssessment");
        String role = NameHelper.mkName("testRole");
        SchemeDetail schemeDetail = createScheme();

        AssessmentDefinition def = ImmutableAssessmentDefinition.builder()
                .name(name)
                .description("desc")
                .isReadOnly(false)
                .permittedRole(role)
                .entityKind(EntityKind.APPLICATION)
                .lastUpdatedBy(user)
                .visibility(AssessmentVisibility.SECONDARY)
                .ratingSchemeId(schemeDetail.id)
                .build();

        long defId = definitionService.save(def);

        definitionService.save(ImmutableAssessmentDefinition
                .copyOf(def)
                .withId(defId)
                .withDescription("updated desc"));

        Collection<AssessmentDefinition> allDefs = definitionService.findAll();

        AssessmentDefinition found = find(
                    d -> d.id().equals(Optional.of(defId)),
                    allDefs)
                .orElseThrow(AssertionError::new);

        assertEquals(
                "updated desc",
                found.description());

        assertEquals(
                found,
                definitionService.getById(defId));

        EntityReference app1 = appHelper.createNewApp(NameHelper.mkName("app1"), ouIds.a);
        EntityReference app2 = appHelper.createNewApp(NameHelper.mkName("app2"), ouIds.b);

        SaveAssessmentRatingCommand cmd = ImmutableSaveAssessmentRatingCommand.builder()
                .assessmentDefinitionId(defId)
                .entityReference(app1)
                .ratingId(schemeDetail.y)
                .lastUpdatedBy(user)
                .build();

        ratingService.store(cmd, user);

        changeLogHelper.assertChangeLogContainsAtLeastOneMatchingOperation(
                app1,
                Operation.ADD);

        assertNotNull(find(
                r -> r.assessmentDefinitionId() == defId && r.ratingId() == schemeDetail.y,
                ratingService.findForEntity(app1)));
        assertTrue(ratingService.findForEntity(app2).isEmpty());

        ratingService.store(
                ImmutableSaveAssessmentRatingCommand
                    .copyOf(cmd)
                    .withRatingId(schemeDetail.n),
                user);

        changeLogHelper.assertChangeLogContainsAtLeastOneMatchingOperation(
                app1,
                Operation.UPDATE);

        assertNotNull(find(
                r -> r.assessmentDefinitionId() == defId && r.ratingId() == schemeDetail.n,
                ratingService.findForEntity(app1)));

        List<AssessmentRating> allRatingsAfterUpdate = ratingService.findByDefinitionId(defId);
        assertEquals(1, allRatingsAfterUpdate.size());
        assertTrue(
                find(
                    r -> r.entityReference().equals(app1) && r.ratingId() == schemeDetail.n,
                    allRatingsAfterUpdate)
                .isPresent());

        ratingService.remove(
                ImmutableRemoveAssessmentRatingCommand.builder()
                        .assessmentDefinitionId(defId)
                        .entityReference(app1)
                        .lastUpdatedBy(user)
                    .build(),
                user);

        changeLogHelper.assertChangeLogContainsAtLeastOneMatchingOperation(
                app1,
                Operation.REMOVE);

        assertTrue(ratingService.findForEntity(app1).isEmpty());

        List<AssessmentRating> allRatingsAfterRemoval = ratingService.findByDefinitionId(defId);
        assertTrue(allRatingsAfterRemoval.isEmpty());
    }


    private static class SchemeDetail {
        long id;
        long y;
        long n;
        long m;
    }


    private SchemeDetail createScheme() {
        long schemeId = schemeHelper.createEmptyRatingScheme(NameHelper.mkName("testScheme"));
        Long y = schemeService.saveRatingItem(
                schemeId,
                ImmutableRatingSchemeItem.builder()
                        .name("yes")
                        .description("ydesc")
                        .ratingSchemeId(schemeId)
                        .position(10)
                        .color("green")
                        .rating('Y')
                        .build());

        Long n = schemeService.saveRatingItem(
                schemeId,
                ImmutableRatingSchemeItem.builder()
                        .name("no")
                        .description("ndesc")
                        .ratingSchemeId(schemeId)
                        .position(20)
                        .color("red")
                        .rating('N')
                        .build());

        Long m = schemeService.saveRatingItem(
                schemeId,
                ImmutableRatingSchemeItem.builder()
                        .name("maybe")
                        .description("mdesc")
                        .ratingSchemeId(schemeId)
                        .position(30)
                        .color("yellow")
                        .rating('M')
                        .userSelectable(false)
                        .build());

        SchemeDetail detail = new SchemeDetail();
        detail.id = schemeId;
        detail.y = y;
        detail.n = n;
        detail.m = m;
        return detail;
    }

}
