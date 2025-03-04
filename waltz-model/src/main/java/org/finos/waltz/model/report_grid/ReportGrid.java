package org.finos.waltz.model.report_grid;


import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableReportGrid.class)
@JsonDeserialize(as = ImmutableReportGrid.class)
public abstract class ReportGrid {

    public abstract ReportGridDefinition definition();
    public abstract ReportGridInstance instance();

}
