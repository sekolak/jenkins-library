package io.cloudservices.terraform

class ServiceVars {
    String service_name
    Option<Integer> service_min_scale
    Option<Integer> service_max_scale
    Option<String> service_scheduling_strategy
    Option<String> alb_path
    Option<List<String>> alb_hosts
    Option<String> health_path
    Option<Integer> health_grace_period
    Option<Integer> deployment_minimum_percent
    Option<Integer> deployment_maximum_percent
    String container_name
    String container_definitions
    Option<String> container_volumes
    Option<String> network_mode
}
