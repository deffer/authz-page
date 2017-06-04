stage 'Clean workspace'
node {
    deleteDir()
}
stage 'Build'
node {
    git branch: 'master', credentialsId: '33a32aa1-7b42-4cd2-86b0-15488a7a8c95', url: 'ssh://git@stash.auckland.ac.nz/iapi/authorization-server-oauth2.git'
    def mvnHome = tool 'M3'
    sh "'${mvnHome}/bin/mvn' clean"
    sh "'${mvnHome}/bin/mvn' package -Dbuild.number=${env.BUILD_NUMBER}"
}
stage 'Deployment to DEV'
node {
    sshagent(['33a32aa1-7b42-4cd2-86b0-15488a7a8c95']) {
        echo 'Copying build artifact to development environment'
        
        sh "scp target/authserver*.jar oauthdev01.its.auckland.ac.nz:/usr/share/apis/authserver/"
        echo 'Restarting service in oauthdev01'
        sh "ssh oauthdev01.its.auckland.ac.nz 'sudo /usr/bin/systemctl restart authserver'"
        
    }
}

stage 'Release to Nexus'
mail (to: 'irina.little@auckland.ac.nz',
     subject: "Job '${env.JOB_NAME} #${env.BUILD_NUMBER}' is waiting for approval to release to TEST",
     body: "Please go to ${env.BUILD_URL}.");
timeout(time:5, unit:'DAYS') {
    input message:'Approve release and deployment to TEST?'
}
node{
    def mvnHome = tool 'M3'
    // for release use argument, as in 'mvn -Darguments="-Dbuild.number=1" release:prepare'    
    def arguments = '-Darguments="-Dbuild.number='+env.BUILD_NUMBER+'"'
    sh "'${mvnHome}/bin/mvn' clean"
    sh "'${mvnHome}/bin/mvn' --batch-mode ${arguments} release:prepare"
    sh "'${mvnHome}/bin/mvn' --batch-mode ${arguments} release:perform"
}

stage 'Deploy release to TEST'
node {
    sshagent(['33a32aa1-7b42-4cd2-86b0-15488a7a8c95']) {
        echo 'Copying build artifact to dev environment'
        
        sh "scp target/authserver*.jar oauthtst01.its.auckland.ac.nz:/usr/share/apis/authserver/"
        echo 'Restarting service in oauthtst01'
        sh "ssh oauthtst01.its.auckland.ac.nz 'sudo /usr/bin/systemctl restart authserver'"
		
        sh "scp target/authserver*.jar oauthtst02.its.auckland.ac.nz:/usr/share/apis/authserver/"
        echo 'Restarting service in oauthtst02'
        sh "ssh oauthtst02.its.auckland.ac.nz 'sudo /usr/bin/systemctl restart authserver'"		
    }
    
    echo 'push pom.xml change and tag'
    sshagent(['33a32aa1-7b42-4cd2-86b0-15488a7a8c95']) {
        sh "git tag b${env.BUILD_NUMBER}"
        sh "git push origin master"
        sh "git push origin --tags"
    }
}


stage 'Release to PROD'
mail (to: 'irina.little@list.auckland.ac.nz',
     subject: "Job '${env.JOB_NAME} #${env.BUILD_NUMBER}' is waiting for approval to release to PROD",
     body: "Please go to ${env.BUILD_URL}.");

timeout(time:5, unit:'DAYS') {
    input message:'Approve release to PROD?'
}

node {
    sshagent(['33a32aa1-7b42-4cd2-86b0-15488a7a8c95']) {
        echo 'Copying build artifact to PROD environment'
        
        sh "scp target/authserver*.jar oauthprd01.its.auckland.ac.nz:/usr/share/apis/authserver/"
        echo 'Restarting service in oauthprd01'
        sh "ssh oauthprd01.its.auckland.ac.nz 'sudo /usr/bin/systemctl restart authserver'"

		        
        sh "scp target/authserver*.jar oauthprd02.its.auckland.ac.nz:/usr/share/apis/authserver/"
        echo 'Restarting service in oauthprd02'
        sh "ssh oauthprd02.its.auckland.ac.nz 'sudo /usr/bin/systemctl restart authserver'"
    }
}
// stage 'PROD WOF test'
// node{
//      sh "newman -c /home/jenkins/SFTC/SFTC-TEST.postman_collection"
// }