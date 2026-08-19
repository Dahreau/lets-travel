def SERVICES = ['api-gateway', 'auth-service', 'user-service', 'travel-service', 'payment-service']

def buildService(svc) {
    sh "cd backend/${svc} && ./mvnw -B clean verify -DforkCount=1 -DreuseForks=false"
}

def sonarService(svc) {
    withSonarQubeEnv('sonarqube') {
        sh "cd backend/${svc} && ./mvnw -B org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar -Dsonar.projectKey=travel-plan-${svc} -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
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

def sonarFrontend() {
    withSonarQubeEnv('sonarqube') {
        sh "cd frontend && npx --yes @sonar/scan -Dsonar.projectKey=travel-plan-frontend -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300"
    }
}

pipeline {
    agent any

    parameters {
        booleanParam(name: 'SKIP_BUILD_TEST', defaultValue: false, description: 'Skip Build & Test + SonarQube (deploy-only iteration). Forces the build result to FAILURE.')
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
                    SERVICES.each { svc -> buildService(svc) }
                    buildFrontend()
                }
            }
        }

        stage('SonarQube Analysis & Quality Gate') {
            when { expression { !params.SKIP_BUILD_TEST } }
            steps {
                script {
                    SERVICES.each { svc -> sonarService(svc) }
                    sonarFrontend()
                }
            }
        }

        stage('Deploy') {
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

                    rm -rf "$DEPLOY_DIR"/*
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
                if (params.SKIP_BUILD_TEST) {
                    currentBuild.result = 'FAILURE'
                    currentBuild.description = 'Debug run (Build/Test/Sonar skipped) — forced FAILURE, not a real gate result.'
                }
            }
        }
    }
}
