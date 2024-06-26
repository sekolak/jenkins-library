package io.cloudservices

abstract class Component {
    ComponentBase base

    abstract ServiceVars getTerraformVars()
}
