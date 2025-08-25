def call(Map configMap){
    pipeline {
        agent {
            label 'AGENT-1'
        }
        environment {
            appVersion = ''
            REGION = "us-east-1"
            ACC_ID = "632745187858"
            PROJECT = configMap.get('project')
            COMPONENT = configMap.get('component')
            
        }
        options {
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
        }
        parameters {
            booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
        }
        // Build
        stages {
            stage('Read package.json') {
                steps {
                    script {
                        def packageJson = readJSON file: 'package.json'
                        appVersion = packageJson.version
                        echo "Package version: ${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    script {
                        sh """
                            npm install
                        """
                    }
                }
            }
            stage('Unit Testing') {
                steps {
                    script {
                        sh """
                            echo "unit tests"
                        """
                    }
                }
            }
            /* stage('Check Dependabot Alerts') {
                environment {
                    GITHUB_TOKEN = credentials('github-token')
                }
                steps {
                    script {
                        // fetch alerts from github
                        def response = sh(
                            script: """
                                curl -s -H "Accept: application/vnd.github+json" \
                                    -H "Authorization: token ${GITHUB_TOKEN}" \
                                    https://api.github.com/repos/Sreekanthp001/catalogue/dependabot/alerts
                            """,
                            returnStdout: true
                        ).trim()
                        
                        // parse Json
                        def json = readJSON text: response

                        // filter alerts by severity
                        def criticalOrHigh = json.findAll { alert ->
                            def severity = alert?.security_advisory?.severity?.toLowerCase()
                            def state = alert?.state?.toLowerCase()
                            return (state == "open" && (severity == "critical" || severity == "high"))
                        }

                        if (criticalOrHigh.size() > 0) {
                            error "❌ Found ${criticalOrHigh.size()} HIGH/CRITICAL Dependabot alerts. Failing pipeline!"
                        }else {
                            echo "✅ No HIGH/CRITICAL Dependabot alerts found."
                        }
                    }
                }
            } */
            stage('Docker Build') {
                steps {
                    script {
                        withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                            sh """ 
                                aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            """
                        }

                    }
                }
            }
            stage('Check Scan Results') {
                steps {
                    script {
                        withAWS(credentials: 'aws-creds', region: 'us-east-1') {
                        // fetch scan findings
                            def findings = sh(
                                script: """
                                    aws ecr describe-image-scan-findings \
                                    --repository-name ${PROJECT}/${COMPONENT} \
                                    --image-id imageTag=${appVersion} \
                                    --region ${REGION} \
                                    --output json
                                """,
                                returnStdout: true
                            ).trim()
                            // parse json
                            def highCritical = json.imageScanFindings.findings.findAll {
                                it.severity == "HIGH" || it.severity == "CRITICAL"
                            }
                            if (highCritical.size() > 0) {
                                echo "❌ Found ${highCritical.size()} HIGH/CRITICAL vulnerbilities!"
                                currentBuild.result = 'FAILURE'
                                error("Build failed due to vulnerabilities")
                            }else {
                                echo "✅ No HIGH/CRITICAL vulnerbilities found."
                            }
                        }
                    }
                }
            }
            stage('Trigger Deploy') {
                when{ 
                    expression { parms.deploy }
                }
                steps {
                    script {
                        build job: 'catalogue-cd', //1st this one and second below one 
                        //build job: "../${COMPONENT}-cd",
                        parameters: [
                            string(name: 'appVersion', value: "${appVersion}"),
                            string(name: 'deploy_to', value: 'dev')
                        ],
                        propagate: false, //even Sg fails vpc will not be effected
                        wait: false // vpc will not wait for sg pipeline completion
                    }
                }
            }
        }
        post {
            always {
                echo 'I will always say Hello again!'
                deleteDir()
            }
            success {
                echo 'Hello Success'
            }
            failure {
                echo 'Hello Failure'
            }
        }
    }
}