package io.cloudservices

import groovy.json.JsonOutput
import io.cloudservices.terraform.ServiceVars
import io.cloudservices.util.Option

import static io.cloudservices.util.Option.Some

final class Service extends Component {

    private final String timestamp = new Date().format("MMddyyHHmm", TimeZone.getTimeZone("EST"))

    String template // JSON string
    List<TemplateParameter> templateParameter
    boolean hasReverseProxy
    String healthPath
    String albPath
    List<String> albHosts
    List<PortMapping> portMappings
    List<VolumeMapping> volumeMappings
    Map taskAttributes
    Integer min
    Integer max
    Integer deployMin
    Integer deployMax
    String schedulingStrategy
    String networkMode
    String ecrPolicyTemplate
    String ecrLifecyclePolicyTemplate
    Integer healthGracePeriod
    String volumesTemplate // JSON string
    String sumoUrl
    Map<String, String> taskSecrets
    boolean telemetryEnabled
    boolean telemetryCustomMetricsEnabled
    String snykPath

    Service(Map args) {
        base = new ComponentBase("service", args)
        template = args.template
        templateParameter = args.templateParameter?.collect { new TemplateParameter(it as Map) }
        hasReverseProxy = args.reverseProxy != null ? args.reverseProxy : false
        healthPath = args.healthPath
        albPath = args.albPath
        albHosts = args.albHosts?.collect { it as String }
        portMappings = args.portMappings?.collect { new PortMapping(it as Map) }
        volumeMappings = args.volumeMappings?.collect { new VolumeMapping(it as Map) }
        taskAttributes = args.taskAttributes as Map
        min = Integer.cast args.min
        max = Integer.cast args.max
        schedulingStrategy = args.schedulingStrategy
        networkMode = args.networkMode
        deployMin = Integer.cast args.deployMin
        deployMax = Integer.cast args.deployMax
        ecrPolicyTemplate = args.ecrPolicyTemplate
        ecrLifecyclePolicyTemplate = args.ecrLifecyclePolicyTemplate
        healthGracePeriod = Integer.cast args.healthGracePeriod
        volumesTemplate = args.volumes as String
        sumoUrl = args.sumoUrl ?: ""
        taskSecrets = args.taskSecrets?.with { it as Map<String, String> } ?: [:]
        telemetryEnabled = args.telemetryEnabled != null ? args.telemetryEnabled : false
        telemetryCustomMetricsEnabled = args.telemetryCustomMetricsEnabled != null ? args.telemetryCustomMetricsEnabled : false
        snykPath = args.snykPath
    }

    private Service(String environment, Service service) {
        base = new ComponentBase(environment, service.@base)

        template = service.@template
        templateParameter = service.@templateParameter
        hasReverseProxy = service.@hasReverseProxy
        healthPath = service.@healthPath
        albPath = service.@albPath
        albHosts = service.@albHosts
        portMappings = service.@portMappings
        volumeMappings = service.@volumeMappings
        taskAttributes = service.@taskAttributes
        min = service.@min
        max = service.@max
        schedulingStrategy = service.@schedulingStrategy
        networkMode = service.@networkMode
        deployMin = service.@deployMin
        deployMax = service.@deployMax
        ecrPolicyTemplate = service.@ecrPolicyTemplate
        ecrLifecyclePolicyTemplate = service.@ecrLifecyclePolicyTemplate
        healthGracePeriod = service.@healthGracePeriod
        volumesTemplate = service.@volumesTemplate
        sumoUrl = service.@sumoUrl
        taskSecrets = service.@taskSecrets
        telemetryEnabled = service.@telemetryEnabled
        telemetryCustomMetricsEnabled = service.@telemetryCustomMetricsEnabled
        snykPath = service.@snykPath
    }

    Service onEnv(String environment) {
        new Service(environment, this)
    }

    String getLowerCaseName() {
        base.name.toLowerCase().replace(".", "-")
    }

    String getFinalName() {
        base.resourcesTruePrefix + base.name.toLowerCase().replace(".", "-")
    }

    String getFinalClusterName() {
        "tf-" + base.environment + "-" + base.cluster.toLowerCase().replace(".", "-")
    }

    String getGitHubRepository() {
        "https://git.cloudservices.tools/cloudservices/${base.name}"
    }

    String getEcrRepository() {
        base.cleanName.toLowerCase()
    }

    String getTag() {
        base.environment ? base.environment.toLowerCase() + "-" + timestamp : timestamp
    }

    String getTask() {
        lowerCaseName
                .replace("service", "task")
                .replace("tool", "task")
                .replace("worker", "task")
    }

    String getContainerName() {
        hasReverseProxy ? "${task}-reserveproxy" : task
    }

    String getFamily() {
        "task-${lowerCaseName}"
    }

    String getTaskDefinition() {
        String taskDefinition = template
                .replace("_TASK_", task)
                .replace("_REGISTRY_", Globals.TOOLS_AWS_ACCOUNT_ID + ".dkr.ecr.${base.regions[0]}.amazonaws.com")
                .replace("_REPOSITORY_", ecrRepository)
                .replace("_TAG_", tag)
                .replace("_ENV_PLATFORM_", base.environment)
                .replace("_ENV_DNS_SUFFIX_", base.envDnsSuffix)
                .replace("_RESOURCES_PREFIX_", base.resourcesPrefix)
                .replace("_RESOURCES_TRUE_PREFIX_", base.resourcesTruePrefix)
                .replace("_SUMOLOGIC_URL_", sumoUrl)
                .replace("_SECRETS_", JsonOutput.toJson(getTaskSecrets()))
                .replace("_PORT_MAPPINGS_", JsonOutput.toJson(getPortMappings() ?: []))
                .replace("_MOUNT_POINTS_", JsonOutput.toJson(getMountPoints()))
                .replace("_ENVIRONMENT_", JsonOutput.toJson(getEnvironmentVariables()))
                .replace("_ADDITIONAL_ATTRIBUTES_", getAdditionalAttributes())

        for (TemplateParameter param in templateParameter) {
            taskDefinition = taskDefinition.replace(param.token, param.value)
        }

        // Fallback CPU_RESERVATION to 0 for default during migration
        taskDefinition = taskDefinition.replace('_CPU_RESERVATION_', '0')

        taskDefinition
    }

    String getAdditionalAttributes() {
        if (!taskAttributes) {
            return ''
        }
        def attributes = JsonOutput.toJson(taskAttributes)
        ',' + attributes.substring(1, attributes.size() - 1)
    }

    List getEnvironmentVariables() {
        def allVars = [
                [name: 'ENV_PLATFORM', value: base.environment],
                [name: 'CLOUDSERVICES_TELEMETRY_ENABLED', value: Boolean.toString(telemetryEnabled)],
                [name: 'MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_ENABLED', value: Boolean.toString(telemetryCustomMetricsEnabled)]
        ]

        def customVars = base.getEnvironmentVariables()
        customVars.each {
            allVars += [name: it.key, value: it.value]
        }

        allVars
    }

    List getTaskSecrets() {
        def allSecrets = []

        def prefix = "arn:aws:secretsmanager:${Aws.getAccountInfo(base.environment, base.regions[0])}:secret:"
        if (base.hasDatabase && base.databaseInitialize) {
            allSecrets += [name: "DB_ENDPOINT", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/db-endpoint"]
            allSecrets += [name: "DB_PASSWORD", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/db-service-password"]
        }
        if (base.prestoParameters != null) {
            allSecrets += [name: "PRESTO_ENDPOINT", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/presto-main-endpoint"]
            allSecrets += [name: "PRESTO_PASSWORD", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/presto-main-service-password"]
        }
        if (base.elasticacheParameters != null) {
            def cacheEnvPrefix = base.elasticacheParameters.engine.toUpperCase()
            allSecrets += [name: "${cacheEnvPrefix}_HOST", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/cache-connexion:host::"]
            allSecrets += [name: "${cacheEnvPrefix}_PORT", valueFrom: "${prefix}/${base.environment}/${lowerCaseName}/cache-connexion:port::"]
        }

        taskSecrets.each {
            // Defaulting to component secret prefix
            def secretName = (it.value.startsWith('/') ? it.value : "/${base.environment}/${lowerCaseName}/${it.value}")
            // Defaulting to last version
            def fullSecretName = secretName.count(':') == 1 ? secretName + "::" : secretName

            allSecrets += [name: it.key, valueFrom: "${prefix}${fullSecretName}"]
        }

        allSecrets
    }

    List getPortMappings() {
        def allPortMappings = portMappings != null ? portMappings : [
                [containerPort: 9443, hostPort: 0]
        ]
        if (telemetryEnabled) {
            allPortMappings += [containerPort: 9444, hostPort: 0]
        }

        allPortMappings
    }

    List<Map<String, String>> getMountPoints() {
        (volumeMappings ?: []).collect({ [sourceVolume: it.name, containerPath: it.containerPath] })
    }

    String computeVolumes() {
        if (volumesTemplate) {
            return volumesTemplate
        }
        if (volumeMappings) {
            JsonOutput.toJson(volumeMappings.collect({ [name: it.name, host: [sourcePath: it.hostPath]] }))
        } else {
            null
        }
    }

    String getEcrPolicy() {
        ecrPolicyTemplate
                .replace("_PROD_AWS_ACCOUNT_ID_", Globals.PROD_AWS_ACCOUNT_ID)
    }

    String getEcrLifecyclePolicy() {
        ecrLifecyclePolicyTemplate
                .replace("_PROD_AWS_ACCOUNT_ID_", Globals.PROD_AWS_ACCOUNT_ID)
    }

    @Override
    ServiceVars getTerraformVars() {
        computeServiceVariables(null)
    }

    private ServiceVars computeServiceVariables(ServiceVars variables) {
        variables = variables ?: new ServiceVars()
        base.computeComponentVariables(variables)

        variables.service_name = base.cleanName
        variables.service_min_scale = Option.of min
        variables.service_max_scale = Option.of max
        variables.service_scheduling_strategy = Option.of schedulingStrategy
        variables.alb_path = Option.of albPath
        variables.alb_hosts = Option.of albHosts
        variables.health_path = Option.of healthPath
        variables.health_grace_period = healthGracePeriod?.with { Some(it as Integer) }
        variables.deployment_minimum_percent = Option.of deployMin
        variables.deployment_maximum_percent = Option.of deployMax
        variables.container_name = containerName
        variables.container_definitions = taskDefinition
        variables.container_volumes = Option.of computeVolumes()
        variables.network_mode = Option.of networkMode

        variables
    }

}
