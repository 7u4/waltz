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

/**
 * Creates a column def to render an entity link
 *
 * eg: usage: mkEntityLinkGridCell('Source', 'source', 'none')
 *
 * @param columnHeading  column display name
 * @param entityRefField  field name in grid data that stores the entity ref for which the link needs to be rendered
 * @param showIcon  whether to display the icon or not
 * @returns {{field: *, displayName: *, cellTemplate: string}}
 */
export function mkEnumGridCell(columnHeading,
                               entityRefField,
                               enumType,
                               showIcon = false,
                               showPopover = false) {
    return {
        field: entityRefField,
        displayName: columnHeading,
        cellTemplate: `
            <div class="ui-grid-cell-contents">
                <waltz-enum-value type="'${enumType}'"
                                  show-icon="${showIcon}"
                                  show-popover="${showPopover}"
                                  key="COL_FIELD">
                </waltz-enum-value>
            </div>`
    };
}


/**
 * Creates a column def to render an entity link
 *
 * eg: usage: mkEntityLinkGridCell('Source', 'source', 'none')
 *
 * @param columnHeading  column display name
 * @param entityRefField  field name in grid data that stores the entity ref for which the link needs to be rendered
 * @param iconPlacement  icon position, allowed values: left, right, none
 * @param tooltipPlacement  position of tooltip, allowed values are: left, right, bottom, top
 * @returns {{field: *, displayName: *, cellTemplate: string}}
 */
export function mkEntityLinkGridCell(columnHeading,
                                     entityRefField,
                                     iconPlacement = "left",
                                     tooltipPlacement = "top") {
    return {
        field: entityRefField + ".name",
        displayName: columnHeading,
        cellTemplate: `
            <div class="ui-grid-cell-contents">
                <waltz-entity-link entity-ref="row.entity.${entityRefField}"
                                   tooltip-placement="${tooltipPlacement}"
                                   icon-placement="${iconPlacement}">
                </waltz-entity-link>
            </div>`
    };
}


/**
 * Creates a column def to render an entity icon and label
 *
 * eg: usage: mkEntityLabelGridCell('Source', 'source', 'none')
 *
 * @param columnHeading  column display name
 * @param entityRefField  field name in grid data that stores the entity ref for which the link needs to be rendered
 * @param iconPlacement  icon position, allowed values: left, right, none
 * @param tooltipPlacement  position of tooltip, allowed values are: left, right, bottom, top
 * @returns {{field: *, displayName: *, cellTemplate: string}}
 */
export function mkEntityLabelGridCell(columnHeading,
                                     entityRefField,
                                     iconPlacement = "left",
                                     tooltipPlacement = "top") {
    return {
        field: entityRefField + ".name",
        displayName: columnHeading,
        cellTemplate: `
            <div class="ui-grid-cell-contents">
                <waltz-entity-icon-label entity-ref="row.entity.${entityRefField}"
                                         tooltip-placement="${tooltipPlacement}"
                                         icon-placement="${iconPlacement}">
                </waltz-entity-icon-label>
            </div>`
    };
}


/**
 * Creates a column def to render a link with an id parameter
 *
 * @param columnHeading  column display name
 * @param displayField  field name that stores the value to be displayed on the grid
 * @param linkIdField  field name that stores the link id field
 * @param linkNavViewName  navigation view name
 * @returns {{field: *, displayName: *, cellTemplate: string}}
 */
export function mkLinkGridCell(columnHeading,
                               displayField,
                               linkIdField,
                               linkNavViewName,
                               additionalProps = {}) {
    return Object.assign({}, additionalProps, {
        field: displayField,
        displayName: columnHeading,
        cellTemplate: `
            <div class="ui-grid-cell-contents">
                <a ui-sref="${linkNavViewName} ({ id: row.entity.${linkIdField} })"
                   ng-bind="COL_FIELD">
                </a>
            </div>`
    });
}

/**
 * Creates a column def to render date
 *
 *
 * @param columnHeading  column display name
 * @param entityRefField  field name in grid data that stores the entity ref for which the link needs to be rendered
 * @param showIcon  whether to display the icon or not
 * @returns {{field: *, displayName: *, cellTemplate: string}}
 */
export function mkDateGridCell(columnHeading,
                               dateField,
                               showIcon = false) {
    return {
        field: dateField,
        displayName: columnHeading,
        cellTemplate: `
            <div class="ui-grid-cell-contents">
                <waltz-from-now timestamp="COL_FIELD"></waltz-from-now>
            </div>`
    };
}


