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
import angular from "angular";

import {registerComponents, registerStore} from "../common/module-utils";
import * as EntitySearchStore from "./services/entity-search-store";
import EntityLinkList from "./components/entity-link-list/entity-link-list";
import EntitySummaryPanel from "./components/entity-summary-panel/entity-summary-panel";

import entityHierarchyNavigator from "./components/entity-hierarchy-navigator/entity-hierarchy-navigator";
import entityInvolvementEditor from "./components/entity-involvement-editor/entity-involvement-editor";
import entitySelector from "./components/entity-selector/entity-selector";
import relatedEntityEditor from "./components/related-entity-editor/related-entity-editor";

import Routes from "./routes";


export default () => {
    const module = angular.module("waltz.entity", []);

    module.config(Routes);

    registerStore(module, EntitySearchStore);

    module
        .component("waltzEntityHierarchyNavigator", entityHierarchyNavigator)
        .component("waltzEntityInvolvementEditor", entityInvolvementEditor)
        .component("waltzEntitySelector", entitySelector)
        .component("waltzRelatedEntityEditor", relatedEntityEditor);

    registerComponents(
        module,
        [ EntitySummaryPanel, EntityLinkList ]);

    return module.name;
};
