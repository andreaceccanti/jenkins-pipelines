#!/usr/bin/env groovy
pipeline {

  agent { label 'docker' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
    REPOSITORY = "https://github.com/italiangrid/storm-deployment-tests"
    BRANCH = "master"
    DIRECTORY = "docker/storm-testsuite"
  }

  parameters {
    booleanParam(defaultValue: false, description: '', name: 'PUSH_TO_REGISTRY')
    booleanParam(defaultValue: true, description: '', name: 'PUSH_TO_DOCKERHUB')
  }

  stages {
    stage('prepare'){
      steps {
        deleteDir()
        git url: "${env.REPOSITORY}", branch: "${env.BRANCH}"
      }
    }

    stage('build'){
      steps {
        dir("${env.DIRECTORY}"){
          sh 'sh build-image.sh'
        }
      }
    }

    stage('push-registry') {
      when {
        expression {
          return params.PUSH_TO_REGISTRY
        }
      }
      steps {
        dir("${env.DIRECTORY}") {
          sh "sh push-image.sh"
        }
      }
    }

    stage('push-dockerhub') {
      when {
        expression {
          return params.PUSH_TO_DOCKERHUB
        }
      }
      steps {
        script {
          withDockerRegistry([ credentialsId: "dockerhub-enrico", url: "" ]) {
            dir("${env.DIRECTORY}") {
              sh "sh push-image-dockerhub.sh"
            }
          }
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script {
        if ('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
