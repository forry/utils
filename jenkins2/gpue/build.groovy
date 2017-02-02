#!groovy
def call(){
   def result = "FAILED";

   stage('builds')
   {
      node('windows' && 'msvc2015') {
          try{
             def repo = 'gpuengine-code'
             def buildDir = 'gpuengine-code-build'
             def buildScript = 'build_script/jenkins2/gpue'
             checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'build_script'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'jenkins2/gpue']]]], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/forry/utils.git']]])
             checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: repo]], submoduleCfg: [], userRemoteConfigs: [[url: 'git://git.code.sf.net/p/gpuengine/code']]])
             def cmake = tool name: 'CMake', type: 'hudson.plugins.cmake.CmakeTool'
             //clean build dir
             sh "rm -rf ${buildDir}"
             sh "mkdir ${buildDir}"
             dir(buildDir) 
             {
               sh "${cmake} -C ../${buildScript}/msvc2015/cache_min.cmake -G \"Visual Studio 14 2015 Win64\" ../${repo}"
             }
             def msbuild = tool name: 'MSBUILD4', type: 'hudson.plugins.msbuild.MsBuildInstallation'
             bat "${msbuild}/msbuild.exe /p:Configuration=release ${buildDir}/ALL_BUILD.vcxproj"
             
             bat "${buildScript}/runTests.bat"
             /*writeFile file: 'testx.txt', text: "#$BUILD_NUMBER"
             stash includes: 'testx.txt', name: 'unit_tests', useDefaultExcludes: false*/
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
          //unstash 'unit_tests'
          def mailbody = "$JOB_NAME - Build # $BUILD_NUMBER - $result:\n\nCheck console output at $BUILD_URL to view the results."
          //emailext attachLog: true, body: mailbody, subject: "$JOB_NAME - Build # $BUILD_NUMBER - $result!", to: env.geRecipients, from: 'jenkins', attachmentsPattern: 'testx.txt'
      }
   }
}

return this;