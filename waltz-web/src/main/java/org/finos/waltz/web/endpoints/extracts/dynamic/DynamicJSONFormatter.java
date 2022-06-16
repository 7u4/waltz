package org.finos.waltz.web.endpoints.extracts.dynamic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.finos.waltz.model.EntityReference;
import org.finos.waltz.model.application.LifecyclePhase;
import org.finos.waltz.model.report_grid.ReportGrid;
import org.finos.waltz.model.report_grid.ReportGridColumnDefinition;
import org.finos.waltz.model.report_grid.ReportGridDefinition;
import org.finos.waltz.model.report_grid.ReportSubject;
import org.finos.waltz.web.endpoints.extracts.ColumnCommentary;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.finos.waltz.common.ListUtilities.*;
import static org.jooq.lambda.fi.util.function.CheckedConsumer.unchecked;

@Component
public class DynamicJSONFormatter implements DynamicFormatter {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicJSONFormatter.class);
    private final FormatterUtils formatterUtils;

    public DynamicJSONFormatter(FormatterUtils formatterUtils){
        this.formatterUtils = formatterUtils;
    }


    @Override
    public byte[] format(String id,
                         ReportGrid reportGrid,
                         List<Tuple2<ReportGridColumnDefinition, ColumnCommentary>> columnDefinitions,
                         List<Tuple2<ReportSubject, ArrayList<Object>>> reportRows)  throws IOException {
        try {
            LOG.info("Generating JSON data {}",id);
            return mkResponse(reportGrid,columnDefinitions,reportRows);
        } catch (IOException e) {
           LOG.warn("Encountered error when trying to generate JSON response.  Details:{}", e.getMessage());
           throw e;
        }
    }

    private byte[] mkResponse(ReportGrid reportGrid,
                              List<Tuple2<ReportGridColumnDefinition, ColumnCommentary>> columnDefinitions,
                              List<Tuple2<ReportSubject, ArrayList<Object>>> reportRows) throws IOException {

        ReportGridDefinition reportGridDefinition = reportGrid.definition();
        ImmutableReportGridSchema reportGridSchema =
                ImmutableReportGridSchema.builder()
                        .id(reportGridDefinition.externalId().orElseGet(()->""+reportGridDefinition.id()))
                        .apiTypes(new ApiTypes())
                        .name(reportGridDefinition.name())
                        .grid(transform(columnDefinitions,reportRows))
                        .build();

        return createMapper()
                .writeValueAsBytes(reportGridSchema);
    }


    private Grid transform(List<Tuple2<ReportGridColumnDefinition, ColumnCommentary>> columnDefinitions,
                           List<Tuple2<ReportSubject, ArrayList<Object>>> reportRows) {

        ArrayList<Row> data = new ArrayList<>();
        data.ensureCapacity(reportRows.size());
        for (Tuple2<ReportSubject, ArrayList<Object>> currentRow : reportRows) {
            ImmutableRow.Builder transformedRow = ImmutableRow.builder();

            List<Cell> transformedRowValues = new ArrayList<>();

            transformedRow.id(createKeyElement(currentRow.v1.entityReference()));
            List<String> columnHeadings = formatterUtils.mkColumnHeaders(columnDefinitions);
            int maxColumns = columnHeadings.size();

            for (int idx = 0; idx < maxColumns; idx++) {
                String formattedColumnName = formatterUtils.getShortColumnName(columnHeadings.get(idx));
                int prevCellAddedIdx= transformedRowValues.size() - 1;
                boolean isComment = (formattedColumnName.contains("comment"));
                Object currentCell = currentRow.v2.get(idx);
                if (currentCell != null) {
                    ImmutableCellValue cell = ImmutableCellValue.builder()
                            .name(formattedColumnName)
                            .value(currentCell.toString())
                            .build();

                    if (isComment && prevCellAddedIdx>-1 && transformedRowValues.get(prevCellAddedIdx) instanceof ImmutableCellValue) {
                        ImmutableCellValue previousColumnCell = (ImmutableCellValue)transformedRowValues.get(prevCellAddedIdx);
                        ImmutableCellValue withComment = ImmutableCellValue.copyOf(previousColumnCell)
                                .withComment(currentCell.toString());
                        transformedRowValues.set(prevCellAddedIdx,withComment);
                    }else{
                        transformedRowValues.add(cell);
                    }

                }
            }
            transformedRow.addAllCells(transformedRowValues);
            data.add(transformedRow.build());
        }

        return ImmutableGrid.builder()
                .addAllRows(data)
                .build();
    }


    private String format(Object o){
        return "";
    }

    private KeyCell createKeyElement(EntityReference keyAttrib ){
        return ImmutableKeyCell.builder()
                .key(keyAttrib)
                .build();
    }


    private String coalesceColumnName(String columnName, String displayName) {
        return displayName!=null&&displayName.trim().length()>0 ?
                displayName : columnName;
    }



    private List<Object> simplify(Tuple2<ReportSubject, ArrayList<Object>> row) {

        long appId = row.v1.entityReference().id();
        String appName = row.v1.entityReference().name().orElse("");
        Optional<String> assetCode = row.v1.entityReference().externalId();
        LifecyclePhase lifecyclePhase = row.v1.lifecyclePhase();

        List<Object> appInfo = asList(appId, appName, assetCode, lifecyclePhase.name());

        return map(concat(appInfo, row.v2), value -> {
            if (value == null) return null;
            if (value instanceof Optional) {
                return ((Optional<?>) value).orElse(null);
            } else {
                return value;
            }
        });
    }

    private ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    }

}
