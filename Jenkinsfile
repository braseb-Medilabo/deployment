// ============================================
// 1. Récupérer le token d'authentification
// ============================================
String getToken(String owner, String image) {
    return sh(
        script: """
            curl -sf "https://ghcr.io/token?scope=repository:${owner}/${image}:pull" | jq -r .token
        """,
        returnStdout: true
    ).trim()
}

// ============================================
// 2. Récupérer la liste brute des tags
// ============================================
String getImagesVersion(String owner, String image, String token) {
    String rawTags = sh(
        script: """
            curl -sf -H "Authorization: Bearer ${token}" \
                 "https://ghcr.io/v2/${owner}/${image}/tags/list" \
            | jq -r '.tags[]'
        """,
        returnStdout: true
    ).trim()
    if (!rawTags) {
        return []
    }
    return rawTags.split('\n') as List<String>
}

// ============================================
// 3. Filtrer + trier pour obtenir le dernier tag semver
// ============================================
String getLatestImageService(List<String> imagesVersion) {
    List<String> images = imagesVersion.findAll{ it -> it ==~ /^[0-9]+\.[0-9]+\.[0-9]+$/ }

    if (images.isEmpty()) {
        error("Aucune image valide trouvée parmi : ${imagesVersion}")
    }
    return images.sort().reverse()[0]
}

def services = [
    INFO_PATIENT_VERSION  : "patient-info-backend",
    NOTE_PATIENT_VERSION  : "patient-note-backend",
    RISK_PATIENT_VERSION  : "patient-risque-diabete-backend",
    GATEWAY_VERSION       : "gateway",
    FRONT_VERSION         :"front-end"
]

pipeline {
    agent any
   environment {
        OWNER    = "braseb-Medilabo"
        //INFO_PATIENT_VERSION = "latest"
        
    }
    stages {
        /*stage('Stop old docker compose') {
            steps {
                sh 'docker compose down --remove-orphans || true'
            }
        }*/
        stage('Clean workspace') {
            steps {
                cleanWs()
            }
        }
        stage('Get token and last service image') {
            steps {
                script{
                    
                    services.each{envVar, elm -> 
                        String token = getToken(env.OWNER, elm)
                        echo token
                        List<String> images = getImagesVersion(env.OWNER, elm, token)
                        echo images.toString()
                        String version = getLatestImageService(images)
                        echo version
                        env."${envVar}" = version
                        echo env."${envVar}"
                     }
                 }
            }
        }
        stage('Get deployment repository') {
            steps {
                git branch: 'main', poll: false, url: 'https://github.com/braseb-Medilabo/deployment.git'
            }
        }
        stage('Write .env file') {
    steps {
        withCredentials([
            string(credentialsId: 'pg-user',                variable: 'PG_USER'),
            string(credentialsId: 'pg-password',            variable: 'PG_PASSWORD'),
            string(credentialsId: 'mongo-user',             variable: 'MONGO_USER'),
            string(credentialsId: 'mongo-password',         variable: 'MONGO_PASSWORD'),
            string(credentialsId: 'jwt-secret',             variable: 'JWT_SECRET'),
            string(credentialsId: 'api-url',                variable: 'API_URL'),
            string(credentialsId: 'frontend-url',           variable: 'FRONTEND_URL'),
            string(credentialsId: 'api-server-gateway-url', variable: 'API_SERVER_GATEWAY_URL')
        ]) {
            script {
                writeFile file: '.env', text: """PG_USER=${PG_USER}
PG_PASSWORD=${PG_PASSWORD}
MONGO_USER=${MONGO_USER}
MONGO_PASSWORD=${MONGO_PASSWORD}
JWT_SECRET=${JWT_SECRET}
API_URL=${API_URL}
FRONTEND_URL=${FRONTEND_URL}
API_SERVER_GATEWAY_URL=${API_SERVER_GATEWAY_URL}
FRONT_VERSION=${env.FRONT_VERSION}
GATEWAY_VERSION=${env.GATEWAY_VERSION}
INFO_PATIENT_VERSION=${env.INFO_PATIENT_VERSION}
NOTE_PATIENT_VERSION=${env.NOTE_PATIENT_VERSION}
RISK_PATIENT_VERSION=${env.RISK_PATIENT_VERSION}
DEPLOYMENT_VERSION=${env.BUILD_NUMBER}
"""
            }
            sh 'chmod 600 .env'
        }
    }
}
        stage('Verify resolved versions') {
            steps {
                sh 'docker compose config | grep image:'
            }
        }
        
        stage('Debug') {
            steps {
                sh 'ls -la'
                sh 'echo "Shell env: $INFO_PATIENT_VERSION"'
            }
        }
        stage('Pull images before tests') {
            steps {
                sh """
                    docker pull ghcr.io/braseb-medilabo/patient-info-backend:${env.INFO_PATIENT_VERSION}
                    docker pull ghcr.io/braseb-medilabo/patient-note-backend:${env.NOTE_PATIENT_VERSION}
                    docker pull ghcr.io/braseb-medilabo/patient-risque-diabete-backend:${env.RISK_PATIENT_VERSION}
                    docker pull ghcr.io/braseb-medilabo/gateway:${env.GATEWAY_VERSION}
                """
            }
        }
        stage('Launch integration tests') {
            steps {
                dir('integration.test') {
                    sh 'mvn clean verify'
                }
            }
        }
        stage('Launch docker compose') {
            steps {
                sh 'docker compose pull'
                sh 'docker compose up -d'
            }
        }
        
    }

    post {
        failure {
            sh 'docker compose logs --no-color > compose-logs.txt || true'
            archiveArtifacts artifacts: 'compose-logs.txt', allowEmptyArchive: true
            //sh 'docker compose down --remove-orphans || true'
        }
    }
}