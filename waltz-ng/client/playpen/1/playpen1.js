/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017 Waltz open source project
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


import template from "./playpen1.html";
import {initialiseData} from "../../common";
import App from './App.svelte';

const initData = {


};

function controller($element, $q, serviceBroker) {

    const vm = initialiseData(this, initData);

    vm.$onInit = () => {
        setTimeout(() => {
            const targ = document.querySelector("#svelte-app");
            console.log({targ})
            const app = new App({
                target: targ,
                props: {
                    name: 'world',
                    serviceBroker
                }
            });
        }, 10)

    };

    vm.myVars = {};

    let x = 0;
    vm.changeVars = () => {
        vm.myVars = { msg: "Hello " + (x ++ ) };
    };
}

controller.$inject = ["$element", "$q", "ServiceBroker"];

const view = {
    template,
    controller,
    controllerAs: "$ctrl",
    bindToController: true,
    scope: {}
};

export default view;