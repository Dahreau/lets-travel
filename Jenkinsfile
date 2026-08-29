def ALL_SERVICES = ['api-gateway', 'auth-service', 'user-service', 'travel-service', 'payment-service']
def ALL_TARGETS = ALL_SERVICES + ['frontend']

def resolveTargets(String raw, List allTargets) {
    if (!raw?.trim()) {
        return allTargets
    }
    def requested = raw.split(',').collect { it.trim() }.findAll { it }
    def invalid = requested - allTargets
    if (invalid) {
        error("TARGET_SERVICES invalide : ${invalid.join(', ')} (valeurs possibles : ${allTargets.join(', ')})")
    }
    return requested
}

def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify -DforkCount=1 -DreuseForks=false"
}

// standalone=true : ce service n'a pas ete construit par Build & Test dans ce run
// (skip global ou hors TARGET_SERVICES) - on (re)compile et (re)teste avant sonar:sonar.
def sonarService(svc, boolean standalone) {
    def goal = standalone
        ? 'clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar'
        : 'org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar'
    withSonarQubeEnv('sonarqube') {
        sh "cd backend/${svc} && ./mvnw -B ${goal} -DforkCount=1 -DreuseForks=false -Dsonar.projectKey=lets-travel-${svc} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
    }
}

def buildFrontend() {
    sh '''
        cd frontend
        npm install
        npm run build
        npx ng test --watch=false --coverage --coverage-reporters=lcov
    '''
}

// standalone=true : meme raison que sonarService - il faut generer le rapport lcov nous-memes
// puisque la stage Build & Test n'a pas tourne dans ce run.
def sonarFrontend(boolean standalone) {
    if (standalone) {
        buildFrontend()
    }
    withSonarQubeEnv('sonarqube') {
        sh "cd frontend && npx --yes @sonar/scan -Dsonar.projectKey=lets-travel-frontend -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
    }
}

pipeline {
    agent any

    parameters {
        booleanParam(name: 'SKIP_BUILD_TEST', defaultValue: false, description: 'Saute la stage Build & Test. Sonar/Deploy se debrouillent seuls si besoin. Force le resultat en FAILURE.')
        booleanParam(name: 'SKIP_SONAR', defaultValue: false, description: 'Saute la stage SonarQube Analysis & Quality Gate. Force le resultat en FAILURE.')
        booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: 'Saute la stage Deploy. Force le resultat en FAILURE.')
        string(name: 'TARGET_SERVICES', defaultValue: '', description: 'Liste separee par des virgules parmi api-gateway,auth-service,user-service,travel-service,payment-service,frontend. Vide = tous. Restreint uniquement Build & Test (Sonar reste toujours global). Force le resultat en FAILURE si non vide.')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
    }

    environment {
        // Ryuk injoignable sous charge faisait planter les tests avant neo4j.
        TESTCONTAINERS_RYUK_DISABLED = 'true'
        // Jenkins tourne dans un conteneur (docker.sock monte) : le port mappe de neo4j
        // n'est pas joignable via 172.17.0.1, seulement via host.docker.internal.
        TESTCONTAINERS_HOST_OVERRIDE = 'host.docker.internal'
    }

    stages {
        stage('Checkout') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        stage('Validate infra') {
            steps {
                sh '''
                    set -e
                    if [ -d infra ]; then
                        for f in $(find infra -name "*.sh"); do
                            echo "Shell : $f"
                            bash -n "$f"
                        done
                    fi
                '''
            }
        }

        stage('Build & Test') {
            when { expression { !params.SKIP_BUILD_TEST } }
            steps {
                script {
                    def targets = resolveTargets(params.TARGET_SERVICES, ALL_TARGETS)
                    targets.findAll { it != 'frontend' }.each { svc -> buildService(svc) }
                    if (targets.contains('frontend')) {
                        buildFrontend()
                    }
                }
            }
        }

        stage('SonarQube Analysis & Quality Gate') {
            when { expression { !params.SKIP_SONAR } }
            steps {
                script {
                    // Sonar analyse toujours TOUS les services, meme si Build & Test a ete
                    // restreint via TARGET_SERVICES - seul standalone varie par service.
                    def builtTargets = (params.SKIP_BUILD_TEST as boolean) ? [] : resolveTargets(params.TARGET_SERVICES, ALL_TARGETS)
                    ALL_SERVICES.each { svc -> sonarService(svc, !builtTargets.contains(svc)) }
                    sonarFrontend(!builtTargets.contains('frontend'))
                }
            }
        }

        stage('Deploy') {
            when { expression { !params.SKIP_DEPLOY } }
            steps {
                sh '''
                    set -e
                    DEPLOY_DIR="$HOST_REPO_PATH/infra/ci/deploy-workspace"
                    STATE_DIR="$HOST_REPO_PATH/infra/ci/persistent-state"

                    # Fichiers gitignores (secrets Vault, certs) : sauvegardes hors de
                    # DEPLOY_DIR avant le rm -rf, restaures apres, pour survivre aux builds.
                    STATE_FILES=".env infra/vault/.unseal-key.txt infra/vault/certs/vault.crt infra/vault/certs/vault.key infra/nginx/certs/travel-plan.crt infra/nginx/certs/travel-plan.key infra/internal-tls/certs/internal.crt infra/internal-tls/certs/internal.key"

                    mkdir -p "$STATE_DIR"
                    for f in $STATE_FILES; do
                        if [ -f "$DEPLOY_DIR/$f" ]; then
                            mkdir -p "$STATE_DIR/$(dirname "$f")"
                            mv "$DEPLOY_DIR/$f" "$STATE_DIR/$f"
                        fi
                    done

                    # rm -rf sur ce bind-mount WSL2/DrvFs echoue parfois "Directory not empty"
                    # sur un rm concurrent (verrou transitoire cote hote) : on retente.
                    for attempt in 1 2 3 4 5; do
                        rm -rf "$DEPLOY_DIR"/* && break
                        [ "$attempt" = 5 ] && { echo "rm -rf $DEPLOY_DIR/* a echoue apres 5 tentatives" >&2; exit 1; }
                        sleep 3
                    done
                    tar --exclude=.git --exclude=node_modules --exclude=target --exclude=dist --exclude=.angular -C "$WORKSPACE" -cf - . | tar -C "$DEPLOY_DIR" -xf -

                    for f in $STATE_FILES; do
                        if [ -f "$STATE_DIR/$f" ]; then
                            mkdir -p "$DEPLOY_DIR/$(dirname "$f")"
                            cp "$STATE_DIR/$f" "$DEPLOY_DIR/$f"
                        fi
                    done

                    cd ansible
                    ansible-galaxy collection install -r requirements.yml
                    ansible-playbook -i inventory.ini playbooks/site.yml -e project_dir="$DEPLOY_DIR"
                '''
            }
        }
    }

    post {
        always {
            script {
                // Un run partiel (stage sautee ou services cibles) ne peut jamais valoir comme
                // pipeline complete, meme si tout ce qui a tourne est vert - voir troubleshooting.md.
                boolean fullRun = !params.SKIP_BUILD_TEST && !params.SKIP_SONAR && !params.SKIP_DEPLOY && !params.TARGET_SERVICES?.trim()
                if (!fullRun) {
                    currentBuild.result = 'FAILURE'
                    currentBuild.description = "Run partiel (build=${!params.SKIP_BUILD_TEST}, sonar=${!params.SKIP_SONAR}, deploy=${!params.SKIP_DEPLOY}, cibles=${params.TARGET_SERVICES?.trim() ?: 'toutes'}) - FAILURE forcee, ce n'est pas un resultat de pipeline complet."
                }
            }
        }
    }
}
