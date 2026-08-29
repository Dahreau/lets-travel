def ALL_SERVICES = ['api-gateway', 'auth-service', 'user-service', 'travel-service', 'payment-service']
def DEPLOYED_BASE_URL = 'https://host.docker.internal:8443'

def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify -DforkCount=1 -DreuseForks=false"
}

// standalone=true : Build & Test a ete saute - on (re)compile nous-memes avant sonar:sonar.
// returnStatus (pas de throw) : chaque service doit etre analyse meme si un precedent a echoue.
def sonarService(svc, boolean standalone) {
    def goal = standalone
        ? 'clean verify org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar'
        : 'org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar'
    def status = 0
    withSonarQubeEnv('sonarqube') {
        status = sh(script: "cd backend/${svc} && ./mvnw -B ${goal} -DforkCount=1 -DreuseForks=false -Dsonar.projectKey=lets-travel-${svc} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300", returnStatus: true)
    }
    return status
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
    def status = 0
    withSonarQubeEnv('sonarqube') {
        status = sh(script: "cd frontend && npx --yes @sonar/scan -Dsonar.projectKey=lets-travel-frontend -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300", returnStatus: true)
    }
    return status
}

pipeline {
    agent any

    parameters {
        booleanParam(name: 'SKIP_BUILD_TEST', defaultValue: false, description: 'Saute la stage Build & Test. Sonar se debrouille seul si besoin. Force le resultat en FAILURE.')
        booleanParam(name: 'SKIP_SONAR', defaultValue: false, description: 'Saute la stage SonarQube Analysis & Quality Gate. Force le resultat en FAILURE.')
        booleanParam(name: 'SKIP_DEPLOY', defaultValue: false, description: 'Saute Deploy ET les tests e2e/k6 (qui ont besoin du deploiement complet pour tourner). Force le resultat en FAILURE.')
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
                    // Frontend d'abord (~1min30) : un echec s'y voit tout de suite plutot
                    // qu'apres les 5 services backend (voir troubleshooting.md #72).
                    buildFrontend()
                    ALL_SERVICES.each { svc -> buildService(svc) }
                }
            }
        }

        stage('SonarQube Analysis & Quality Gate') {
            when { expression { !params.SKIP_SONAR } }
            steps {
                script {
                    // On analyse TOUT le monde d'abord, on ne bloque qu'a la fin - sinon le premier
                    // service en echec masque l'etat des suivants (voir troubleshooting.md).
                    boolean standalone = params.SKIP_BUILD_TEST as boolean
                    def failed = []
                    ALL_SERVICES.each { svc ->
                        if (sonarService(svc, standalone) != 0) {
                            failed << svc
                        }
                    }
                    if (sonarFrontend(standalone) != 0) {
                        failed << 'frontend'
                    }
                    if (failed) {
                        error("Quality Gate Sonar en echec pour : ${failed.join(', ')}")
                    }
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

        stage('Wait for stack ready') {
            when { expression { !params.SKIP_DEPLOY } }
            steps {
                sh """
                    set -e
                    # Deploy attend deja les healthchecks Compose, mais nginx/les apps peuvent
                    # finir de demarrer quelques secondes apres - filet de securite avant e2e/k6.
                    for attempt in \$(seq 1 30); do
                        code=\$(curl -sk -o /dev/null -w '%{http_code}' ${DEPLOYED_BASE_URL}/ || echo 000)
                        [ "\$code" = "200" ] && { echo "Stack prete (HTTP \$code)."; exit 0; }
                        echo "Stack pas prete (HTTP \$code), tentative \$attempt/30..."
                        sleep 5
                    done
                    echo "Stack toujours pas prete apres 30 tentatives (2m30)." >&2
                    exit 1
                """
            }
        }

        stage('E2E Tests (Playwright)') {
            when { expression { !params.SKIP_DEPLOY } }
            environment {
                E2E_BASE_URL = "${DEPLOYED_BASE_URL}"
            }
            steps {
                sh '''
                    cd e2e
                    npm ci
                    npx playwright test
                '''
            }
        }

        stage('Load Tests (k6)') {
            when { expression { !params.SKIP_DEPLOY } }
            steps {
                sh "docker run --rm -i --add-host=host.docker.internal:host-gateway grafana/k6 run -e BASE_URL=${DEPLOYED_BASE_URL} - < k6/lets-travel-load-test.js"
            }
        }
    }

    post {
        always {
            script {
                // Un run partiel (une stage sautee) ne peut jamais valoir comme pipeline
                // complete, meme si tout ce qui a tourne est vert - voir troubleshooting.md.
                boolean fullRun = !params.SKIP_BUILD_TEST && !params.SKIP_SONAR && !params.SKIP_DEPLOY
                if (!fullRun) {
                    currentBuild.result = 'FAILURE'
                    currentBuild.description = "Run partiel (build=${!params.SKIP_BUILD_TEST}, sonar=${!params.SKIP_SONAR}, deploy+e2e+k6=${!params.SKIP_DEPLOY}) - FAILURE forcee, ce n'est pas un resultat de pipeline complet."
                }
            }
        }
    }
}
