#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'GITHUB_REPO', defaultValue: 'github.com/marcocaberletti/repo', description: '')
  ]),
])

def title = 'Argus Authorization Service'
def target = 'stable'

try {
  stage("Promote from Beta to Stable") {
    node('generic'){
      def argus_root = '/mnt/packages/repo/argus'

      dir("${argus_root}"){
        sh "rsync -avu beta/ ${target}/"

        dir("${target}/") {
          sh "createrepo el6/RPMS"
          sh "repoview -t '${title} (CentOS 6)' el6/RPMS"

          sh "createrepo el7/RPMS"
          sh "repoview -t '${title} (CentOS 7)' el7/RPMS"
        }
      }
    }
  }

  stage("Publish on GitHub"){
    node('generic'){
      def github_repo_url = "https://${params.GITHUB_REPO}"
      def github_repo_branch = "gh-pages"

      dir('repo'){
        sh "git clone --depth=1 ${github_repo_url} -b ${github_repo_branch} ."

        sh "rsync -avu /mnt/packages/repo/argus/${target}/ ${target}/"

        dir("${target}/") {
          sh "createrepo el6/RPMS"
          sh "repoview -t '${title} (CentOS 6)' el6/RPMS"

          sh "createrepo el7/RPMS"
          sh "repoview -t '${title} (CentOS 7)' el7/RPMS"
        }

        withCredentials([
          usernamePassword(credentialsId: 'marco-github-credentials', passwordVariable: 'git_password', usernameVariable: 'git_username')
        ]) {
          sh "git config --global user.name 'JenkinsCI'"
          sh "git config --global user.email 'jenkinsci@cloud.cnaf.infn.it'"
          sh "git add ."
          sh "git commit -m 'Promote packages from beta to stable'"
          sh "git push https://${git_username}:${git_password}@${params.GITHUB_REPO}"
        }
      }
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}