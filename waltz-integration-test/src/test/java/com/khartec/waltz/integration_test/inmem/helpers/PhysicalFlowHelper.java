package com.khartec.waltz.integration_test.inmem.helpers;

import org.finos.waltz.model.Criticality;
import org.finos.waltz.model.physical_flow.*;
import org.finos.waltz.model.physical_specification.PhysicalSpecification;
import com.khartec.waltz.service.physical_flow.PhysicalFlowService;
import com.khartec.waltz.service.physical_specification.PhysicalSpecificationService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.khartec.waltz.integration_test.inmem.helpers.NameHelper.mkName;
import static org.finos.waltz.schema.Tables.PHYSICAL_FLOW;

@Service
public class PhysicalFlowHelper {

    private final PhysicalFlowService physicalFlowService;
    private final PhysicalSpecificationService physicalSpecificationService;
    private final DSLContext dsl;

    @Autowired
    public PhysicalFlowHelper(PhysicalFlowService physicalFlowService,
                              PhysicalSpecificationService physicalSpecificationService,
                              DSLContext dslContext) {
        this.physicalFlowService = physicalFlowService;
        this.physicalSpecificationService = physicalSpecificationService;
        this.dsl = dslContext;
    }

    public PhysicalFlowCreateCommandResponse createPhysicalFlow(Long flowId, Long specId, String name) {

        PhysicalSpecification spec = physicalSpecificationService.getById(specId);

        ImmutableFlowAttributes flowAttributes = ImmutableFlowAttributes.builder()
                .transport(TransportKindValue.UNKNOWN)
                .description("")
                .basisOffset(1)
                .criticality(Criticality.MEDIUM)
                .frequency(FrequencyKind.DAILY)
                .build();

        ImmutablePhysicalFlowCreateCommand createCmd = ImmutablePhysicalFlowCreateCommand.builder()
                .logicalFlowId(flowId)
                .specification(spec)
                .flowAttributes(flowAttributes)
                .build();

        return physicalFlowService.create(createCmd, mkName(name));
    }


    public PhysicalFlowDeleteCommandResponse deletePhysicalFlow(Long flowId){
        return physicalFlowService.delete(
                ImmutablePhysicalFlowDeleteCommand.builder()
                        .flowId(flowId)
                        .build(),
                mkName("deletingFlow"));
    }

    public void markFlowAsReadOnly(long id) {
    dsl
            .update(PHYSICAL_FLOW)
            .set(PHYSICAL_FLOW.IS_READONLY, true)
            .where(PHYSICAL_FLOW.ID.eq(id))
            .execute();
    }

    public void updateExternalIdOnFlowDirectly(long id, String extId) {
        dsl
                .update(PHYSICAL_FLOW)
                .set(PHYSICAL_FLOW.EXTERNAL_ID, extId)
                .where(PHYSICAL_FLOW.ID.eq(id))
                .execute();
    }
}
