/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016  Khartec Ltd.
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

package com.khartec.waltz.model.change_initiative;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.khartec.waltz.model.*;
import com.khartec.waltz.model.application.LifecyclePhase;
import org.immutables.value.Value;

import java.util.Date;
import java.util.Optional;


@Value.Immutable
@JsonSerialize(as = ImmutableChangeInitiative.class)
@JsonDeserialize(as = ImmutableChangeInitiative.class)
public abstract class ChangeInitiative implements
        EntityKindProvider,
        ExternalIdProvider,
        ParentIdProvider,
        NameProvider,
        IdProvider,
        DescriptionProvider,
        ProvenanceProvider,
        OrganisationalUnitIdProvider,
        WaltzEntity {

    public abstract ChangeInitiativeKind changeInitiativeKind();
    public abstract LifecyclePhase lifecyclePhase();

    public abstract Optional<Date> lastUpdate();

    public abstract Date startDate();
    public abstract Date endDate();

    @Value.Default
    public EntityKind kind() { return EntityKind.CHANGE_INITIATIVE; }

    public EntityReference entityReference() {
        return ImmutableEntityReference.builder()
                .kind(EntityKind.CHANGE_INITIATIVE)
                .id(id().get())
                .name(name() + externalId().map(extId -> " (" + extId + ")").orElse(""))
                .description(description())
                .build();
    }
}
