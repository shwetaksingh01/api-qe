/*
 * Parameters:
 * BRANCH_NAME - the release branch to use
 * ENV_NAME - the CF environment to deploy to
*/
def gitCredsId = 'github.build.ge.com.api.key'
def machineVersion = '17.3.0-SNAPSHOT'

def cronExpression = ""

if (BRANCH_NAME.trim().equals("develop")) {
    cronExpression = 'H 2-21/5 * * 1-5'
}


// propel is in UTC
properties([
        pipelineTriggers([cron(cronExpression)]),
        [
                $class  : 'jenkins.model.BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', daysToKeepStr: '30', numToKeepStr: '20']
        ],
        parameters([
                choice(choices: "cf3-sysint\ncf3-staging\ncf1-prod\nFrankSch-dev\nFrankSch-qual\nFrankSch-prod\nFF-prod", description: 'environment to test', name: 'ENV_NAME'),
                choice(choices: "all\nem-e2e\nem-alert\nem-api\nem-app\nem-api-beta\nem-audit\nem-command\nem-dam\nem-device\nem-edgegateway\nem-idpd\nem-notification\nem-user", description: 'submodule to test', name: 'MODULE')
        ])
])


def ENV_PREFIX = "${params.ENV_NAME}"
//def QTEST_INTEGRATION = "false"

switch (ENV_PREFIX) {
    case 'cf3-sysint': ENV_PREFIX = 'sysint'; break
    case 'cf3-staging': ENV_PREFIX = 'stage'; break
    case 'cf3-perf': ENV_PREFIX = 'perf'; break
    case 'FrankSch-dev': ENV_PREFIX = 's-dev'; break
    case 'FrankSch-qual': ENV_PREFIX = 's-qual'; break
    case 'FrankSch-prod': ENV_PREFIX = 's-prod'; break
    case 'FF-prod': ENV_PREFIX = 'ff-prod'; break
    case 'cf1-prod': ENV_PREFIX = 'prod'; break
    default: ENV_PREFIX = 'sysint'
}

timeout(time: 90, unit: 'MINUTES') {
    currentBuild.displayName = "${params.ENV_NAME}-${BRANCH_NAME}-${BUILD_NUMBER}"

//    if (ENV_PREFIX == 'sysint' || ENV_PREFIX == 'stage') {
//        QTEST_INTEGRATION = "true"
//    }

    node('dind') {
        deleteDir()
        versions = checkoutServiceAndSupportRepos("cloud-qe")
        envParams = readProperties file: "edge_app_deploy/deployParameters-${params.ENV_NAME}"

        def testMvnParams = [
                "-DUAA_URL=${envParams.UAA_URL}",
                "-DCLIENT_ID=${envParams.DEFAULT_TENANT_CLIENT_ID}",
                "-DUSE_ZONE_ID_HEADER=true",
                "-DTENANT_ZONE_ID=${envParams.DEFAULT_TENANT_ID}",
                "-Dtest.env=${ENV_PREFIX}"
//                "-DqTestIntegration=${QTEST_INTEGRATION}"
        ]

        def testCreds = [string(credentialsId: "${params.ENV_NAME}.default.tenant.client.secret", variable: 'CLIENT_SECRET'),
                         string(credentialsId: "${params.ENV_NAME}.tenant.service.internal.client.secret", variable: 'TENANT_MGR_CLIENT_SECRET')]

        if (ENV_PREFIX == 'sysint') {
            testCreds = [string(credentialsId: "${ENV_PREFIX}.default.tenant.client.secret", variable: 'CLIENT_SECRET'),
                         string(credentialsId: "${ENV_PREFIX}.tenant.service.internal.client.secret", variable: 'TENANT_MGR_CLIENT_SECRET')]
        }

        def fullMvnParams = testMvnParams
        def mvnParamsString = fullMvnParams.join(" ")

        def server = Artifactory.server 'Build-GE-Artifactory'



        stage('Build Dependencies') {
          withCredentials([string(credentialsId: 'buildge.artifactory.password', variable: 'ARTIFACTORY_PASSWORD')]) {
            withEnv(["JSETTINGS=-s ../jenkins/propel-settings.xml", "MVN_PARAM=${mvnParamsString} -Dbuildge.artifactory.password=${ARTIFACTORY_PASSWORD}", "sdkVersion=${machineVersion}"]) {
                docker.withRegistry('https://dtr.predix.io', 'dtr.predix.io.user') {
                    docker.image('dtr.predix.io/predix-edgemanager/edgemanager-mvn-build:0.0.5').inside('-v $HOME/.m2:/root/.m2') {
                        sh '''
                    cd "cloud-qe"
                    mvn clean install -DskipTests ${JSETTINGS} ${MVN_PARAM} -B

                    '''
                    }
                }
            }
          }
        }

        def jobList = [
                ['em-command', 'em-alert', 'em-audit', 'em-dam'],
                ['em-edgegateway', 'em-notification', 'em-user'],
                ['em-api-beta', 'em-app', 'em-device', 'em-idpd'],
                ['em-api'],
                ['em-e2e']
        ]

        jobList.eachWithIndex { item, index ->
            stage("RunTests${index}") {
                def buildSteps = item.collectEntries {
                    ["${it}": runQeTest(mvnParamsString, it, testCreds, machineVersion)]
                }
                if ("${params.MODULE}" == "all") {
                    if (buildSteps.size() == 1) {
                        buildSteps.each { k, v ->
                            stage(k) {
                                v()
                            }
                        }
                    } else {
                        parallel buildSteps
                    }
                } else {
                    if (buildSteps.containsKey("${params.MODULE}")) {
                        if (buildSteps.size() == 1) {
                            buildSteps.each { k, v ->
                                stage(k) {
                                    v()
                                }
                            }
                        } else {
                            def moduleToBuild = buildSteps.get("${params.MODULE}")
                            moduleToBuild()
                        }
                    } else {
                        echo "RunTests${index} skipped"
                    }
                }
            }
        }
    }
}

def runQeTest(mvnString, jobName, creds, machineVer) {
    return {
      withCredentials([string(credentialsId: 'buildge.artifactory.password', variable: 'ARTIFACTORY_PASSWORD')]) {
        withEnv(["JSETTINGS=-s ../../jenkins/propel-settings.xml", "MVN_PARAM=${mvnString} -Dbuildge.artifactory.password=${ARTIFACTORY_PASSWORD}", "JOB=${jobName}", "sdkVersion=${machineVer}"]) {
            withCredentials(creds) {
                docker.withRegistry('https://dtr.predix.io', 'dtr.predix.io.user') {
                    docker.image('dtr.predix.io/predix-edgemanager/edgemanager-mvn-build:0.0.5').inside('-v $HOME/.m2:/root/.m2') {
                        sh '''
                    echo '52.203.99.19 qtest.apps.ge.com' >> /etc/hosts
                    cp "cloud-qe/configuration.zip" "cloud-qe/${JOB}"
                    cp "cloud-qe/machine.zip" "cloud-qe/${JOB}"
                    cp "cloud-qe/machine_large.zip" "cloud-qe/${JOB}"
                    cd "cloud-qe/${JOB}"
                    mvn test ${JSETTINGS} ${MVN_PARAM} -DCLIENT_SECRET=${CLIENT_SECRET} -DTENANT_MGR_CLIENT_SECRET=${TENANT_MGR_CLIENT_SECRET} -B -fn
                    '''
                    }
                }
            }
        }
      }
      junit "cloud-qe/${jobName}/target/surefire-reports/TEST-*.xml"
    }
}
