// def call(Map configMap){
//     pipeline {
//         agent {
//             label 'AGENT-1'
//         }
//         environment {
//             appVersion = ''
//             REGION = "us-east-1"
//             ACC_ID = "632745187858"
//             PROJECT = configMap.get('project')
//             COMPONENT = configMap.get('component')
//         }
//         options {
//             timeout(time: 30, unit: 'MINUTES')
//             disableConcurrentBuilds()
//         }
//         parameters {
//             booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
//         }
//         // Build 
//         stages {
//             stage('Read version') {
//                 steps {
//                     script {
//                         appVersion = readFile('version').trim()
//                         echo "app version: ${appVersion}"
//                     }
//                 }
//             }
//             stage('Install Dependencies') {
//                 steps {
//                     script {
//                         sh """
//                             pip3 install -r requirements.txt
//                         """
//                     }
//                 }
//             }
//             stage('Unit Testing') {
//                 steps {
//                     script {
//                         sh """
//                             echo "unit tests"
//                         """
//                     }
//                 }
//             }
//             stage('Docker Build') {
//                 steps {
//                     script {
//                         withAWS(credentials: 'aws-creds', region: 'us-east-1') {
//                             sh """
//                                 aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
//                                 docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
//                                 docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
//                                 #aws ecr wait image-scan-complete --repository-name ${PROJECT}/${COMPONENT} --image-id imageTag=${appVersion} --region ${REGION}
//                             """
//                         }
//                     }
//                 }
//             }
//             stage('Trigger Deploy') {
//                 when{
//                     expression { params.deploy}
//                 }
//                 steps {
//                     script {
//                         build job: "../${COMPONENT}-cd",
//                         parameters: [
//                             string(name: 'appVersion', value: "${appVersion}"),
//                             string(name: 'deploy_to', value: 'dev')
//                         ],
//                         propagate: false, //even sg fails vpc will not be effected
//                         wait: false //vpc will not wait for sg pipeline completion
//                     }
//                 }
//             }
//         }
//         post {
//             always {
//                 echo 'I will always say Hello again!'
//                 deleteDir()
//             }
//             success {
//                 echo 'Hello Success'
//             }
//             failure {
//                 echo 'Hello Failure'
//             }
//         }
//     }
// }