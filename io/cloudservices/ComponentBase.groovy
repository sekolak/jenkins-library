package io.cloudservices

class ComponentBase {
    String type
    Map args
    String environment
    String name
    String resourcesTruePrefix
    String cluster
    String cleanName
    List<String> regions
    boolean hasDatabase
    boolean databaseInitialize
    Map<String, String> prestoParameters
    Map<String, String> elasticacheParameters

    ComponentBase(String type, Map args) {
        this.type = type
        this.args = args
        this.environment = args.environment
        this.name = args.name
        this.resourcesTruePrefix = args.resourcesTruePrefix
        this.cluster = args.cluster
        this.cleanName = args.cleanName
        this.regions = args.regions ?: []
        this.hasDatabase = args.hasDatabase ?: false
        this.databaseInitialize = args.databaseInitialize ?: false
        this.prestoParameters = args.prestoParameters ?: [:]
        this.elasticacheParameters = args.elasticacheParameters ?: [:]
    }

    List<Map<String, String>> getEnvironmentVariables() {
        // Implémentation des variables d'environnement spécifiques
        return []
    }

    void computeComponentVariables(ServiceVars variables) {
        // Implémentation pour calculer les variables spécifiques du composant
    }
}
