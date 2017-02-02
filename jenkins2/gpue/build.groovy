#!groovy
def call(){
   def result = "FAILED";

   stage('builds')
   {
      node('windows' && 'msvc2013') {
          try{
             //checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'gpuengine-test-code']], submoduleCfg: [], userRemoteConfigs: [[url: 'git://git.code.sf.net/p/gpuengine-test/code']]])
             /*def cmake = tool name: 'CMake', type: 'hudson.plugins.cmake.CmakeTool'
             //echo "${cmake}"
             sh 'rm -rf gpuengine-test-build'
             sh 'mkdir gpuengine-test-build'
             dir('gpuengine-test-build') 
             {
              sh "${cmake} -G \"Visual Studio 12 2013 Win64\" ../gpuengine-test-code"
             }
             def msbuild = tool name: 'MSBUILD4', type: 'hudson.plugins.msbuild.MsBuildInstallation'
             echo "${msbuild}"
             sh "echo ${msbuild}"
             sh "${msbuild}/msbuild.exe gpuengine-test-build/ALL_BUILD.vcxproj"
             */
             writeFile file: 'testx.txt', text: "#$BUILD_NUMBER"
             stash includes: 'testx.txt', name: 'unit_tests', useDefaultExcludes: false
             result = "SUCCESS"
          } catch (e)
          {
              result = "FAILED"
              throw e
          }
          finally
          {
              def body = ""
              def color = "#36A64F"
              body = "$result Job $JOB_NAME #$BUILD_NUMBER\n ${BUILD_URL}console"
              if(result != "SUCCESS")
              { 
                  color = "#E40000"
              }
              //def build = currentBuild.rawBuild
              //def log = build.getLog(1000)
              //slackSend color: color, message: body
              //def string = log.join("\n")
              //slackSend color: '#439FE0', message: string
              //echo "mail to: ${env.geRecipients}"
              //echo "$mailbody"
              
          }
         
      }
   }
   stage('notify')
   {
      node('master')
      {
          unstash 'unit_tests'
          def mailbody = "$JOB_NAME - Build # $BUILD_NUMBER - $result:\n\nCheck console output at $BUILD_URL to view the results."
          emailext attachLog: true, body: mailbody, subject: "$JOB_NAME - Build # $BUILD_NUMBER - $result!", to: env.geRecipients, from: 'jenkins', attachmentsPattern: 'testx.txt'
      }
   }
}

return this;