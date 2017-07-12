//import models.*
//import templates.*

import hudson.FilePath
import org.yaml.snakeyaml.Yaml

createJobs()

void createJobs() {
    def yaml = new Yaml()

    // Build a list of all config files ending in .yml
    def cwd = hudson.model.Executor.currentExecutor().getCurrentWorkspace().absolutize()
    def configFiles = new FilePath(cwd, 'configs').list('*.yml')

    // Create/update a pull request job for each config file
    configFiles.each { file ->
        def projectConfig = yaml.loadAs(file.readToString(), ProjectConfig.class)
        def project = projectConfig.project.replaceAll(' ', '-')

        PullRequestTemplate.create(job("${project}-Pull-Request"), projectConfig)
    }
}


class ProjectConfig {
    /*
     * Required
     */
    String project
    String repo
    String email

    /*
     * Optional
     */
    String command_test = "mvn clean test"
}

class PullRequestTemplate {
    static void create(job, config) {
        job.with {
            description("Builds all pull requests opened against <code>${config.repo}</code>.<br><br><b>Note</b>: This job is managed <a href='https://github.com/curalate/jenkins-job-dsl-demo'>programmatically</a>; any changes will be lost.")

            logRotator {
                daysToKeep(7)
                numToKeep(50)
            }

            concurrentBuild(true)

            scm {
                git {
                    remote {
                        github(config.repo)
                        refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                     }
                     branch('${sha1}')
                 }
            }

            triggers {
                githubPullRequest {
                    cron('H/5 * * * *')
                    triggerPhrase('@curalatebot rebuild')
                    onlyTriggerPhrase(false)
                    useGitHubHooks(true)
                    permitAll(true)
                    autoCloseFailedPullRequests(false)
                }
            }

            publishers {
                githubCommitNotifier()
            }

            steps {
                shell(config.command_test)
            }
        }
    }
}
