pipeline {
    agent any

    tools {
        maven 'maven-3'
        jdk 'jdk17'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean compile -B'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test -B'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Static Analysis') {
            parallel {
                stage('SpotBugs') {
                    steps {
                        sh 'mvn spotbugs:check -B'
                    }
                }
                stage('Checkstyle') {
                    steps {
                        sh 'mvn checkstyle:check -B || true'
                    }
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn package -DskipTests -B'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.hpi', fingerprint: true
                }
            }
        }

        stage('Integration Tests') {
            when {
                branch 'main'
            }
            steps {
                sh 'mvn verify -DskipUnitTests -B'
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        failure {
            echo 'Build failed!'
        }
        success {
            echo 'Build successful!'
        }
    }
}
