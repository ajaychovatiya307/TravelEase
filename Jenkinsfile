// Builds and tests every backend module and the frontend. Any failing test, lint error or type
// error fails the pipeline: maven.test.failure.ignore defaults to false in backend/pom.xml and is
// also forced here so a stray -D flag elsewhere cannot hide failures.

def run(String command) {
    if (isUnix()) {
        sh command
    } else {
        bat command
    }
}

pipeline {
    agent any

    options {
        timeout(time: 45, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                // Checks out the revision this pipeline was triggered for (multibranch / "Pipeline from SCM").
                checkout scm
            }
        }

        stage('Backend: build and test') {
            steps {
                dir('backend') {
                    run 'mvn -B clean verify -Dmaven.test.failure.ignore=false'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'backend/**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Frontend: lint, test and build') {
            steps {
                dir('frontend') {
                    run 'npm ci'
                    run 'npm run lint'
                    run 'npm run test:run'
                    run 'npm run build'
                }
            }
        }

        stage('Archive') {
            steps {
                archiveArtifacts artifacts: 'backend/*-service/target/*-service-*.jar,frontend/dist/**',
                    fingerprint: true
            }
        }
    }

    post {
        success {
            echo 'TravelEase pipeline completed successfully.'
        }
        failure {
            echo 'TravelEase pipeline failed: see the stage log and test report.'
        }
    }
}
