#!groovy

   
def call(debug = false){
   def jenkinsFail = false;
   def anyBuildFailed = false;
   def testMap = [msvc2015:false, gcc:false]
   def slackmessageColorMap = [success:'#36A64F',fail:'#E40000' ,testFail:'#FF9D3C'] //for slack chat
   def hipMessageColorMap = [success:'GREEN',fail:'RED' ,testFail:'YELLOW'] //for hipChat
   def prefixes = [msvc2015:'msvc2015_', gcc:'gcc_']
   
   stage('builds')
   {
      node('pcmilet') {
         def cmGenerator = "Visual Studio 14 2015 Win64"
         def prefixIndex = 'msvc2015'
         try{
            def success = false;
            success = msvcXBuild(cmGenerator, prefixIndex, prefixes, testMap)
            result = success ? "SUCCESS" : "FAILED";
            anyBuildFailed = success ? anyBuildFailed : true;
            echo "${result}"
         } catch (e)
         {
            jenkinsFail = true
            if(debug)
               echo "MSVC2015 failed!!!!!!!!!!"
			   printThrowable(e)
            //throw e
         }
         
      }
      
      node('cadwork2'){
         def cmGenerator = "Unix Makefiles"
         def prefixIndex = 'gcc'
         try{
            def success = false;
            success = makeBuild(cmGenerator, prefixIndex, prefixes, testMap)
            result = success ? "SUCCESS" : "FAILED";
            anyBuildFailed = success ? anyBuildFailed : true;
         } catch (e){
            jenkinsFail = true
            if(debug)
               echo "linux failed!!!!!!!!!!"
			   printThrowable(e)
            //throw e
         }
      }
   }
   
   stage('notify')
   {
      node('master')
      {
         def attachment = ""
         def testssubject = "SUCCESS"
         def failedTestMessage = "";
         def buildResult = anyBuildFailed ? "FAILED" : "SUCCESS"
         
         def slackMessageColor = ""
         def hipMessageColor = ""
         def subject = ""
         def mailbody = ""
         def slackMessage = ""
         def hipMessage = ""
         
         if(jenkinsFail)
         {
            slackMessageColor = slackmessageColorMap['fail']
            subject = "$JOB_NAME - Build #$BUILD_NUMBER - Jenkins FAIL"
            mailbody = "$JOB_NAME - Build #$BUILD_NUMBER - Jenkins FAIL! \n\nCheck console output at ${BUILD_URL}console to view the results.\n${failedTestMessage}"
            slackMessage = subject + "\n Check console output at ${BUILD_URL}console to view the results."
            hipMessage = subject + '\n <a href="${BUILD_URL}console">Check console output to view the results.</a>'
         }
         else
         {
            try{
               sh 'if [ ! -d ./log ]; then mkdir ./log; fi'
               sh 'rm -rf ./log/*'
            } catch (e)
            {
               printThrowable(e)
            }
            
            dir('log')
            {        
               for(iter in  mapToList(testMap))
               { 
                  if(!iter.value)
                  {
                     failedTestMessage += "$iter.key test has FAIL!\n"
                     testssubject = "FAIL!"
                     echo "test ${iter.key} : ${iter.value} failed!"
                  }
                  else
                  {
                     try{
                        def klic = iter.key.toString()
                        string stname = prefixes[klic]+"unit_tests"
                        echo "${stname}"
                        unstash stname
                     } catch (e) { 
                        echo "${iter.key} FAIL"
                        printThrowable(e)                  
                     }
                  }
               }
            }
            
            subject = "$JOB_NAME - Build # $BUILD_NUMBER - $buildResult!, Tests : $testssubject"
            mailbody = "$JOB_NAME - Build # $BUILD_NUMBER - $buildResult, Tests : $testssubject:\n\nCheck console output at ${BUILD_URL}console to view the results.\n${failedTestMessage}"
            attachment = "log/*"
            slackMessageColor = result == "SUCCESS"? testssubject == "SUCCESS" ? slackmessageColorMap['success'] : slackmessageColorMap['testFail'] : slackmessageColorMap['fail']
            hipMessageColor = result == "SUCCESS"? testssubject == "SUCCESS" ? hipMessageColorMap['success'] : hipMessageColorMap['testFail'] : hipMessageColorMap['fail']
            slackMessage = subject + "\n Check console output at ${BUILD_URL}console to view the results."
            hipMessage = subject + '\n <a href="${BUILD_URL}console">Check console output to view the results.</a>'
            
         }
         if(debug)
         {
            echo "subject: $subject"
            echo "body:\n $mailbody"
            echo "attachement: $attachment"
            echo "color: $slackMessageColor"
         }
         slackSend color: slackMessageColor, message: slackMessage
         hipchatSend color: hipMessageColor, credentialId: '', message: hipMessage, notify: true, room: 'jenkins', sendAs: 'jenkins', server: '', v2enabled: false
         emailext attachLog: true, body: mailbody, subject: subject, to: env.geRecipients, from: 'jenkins', attachmentsPattern: attachment
      }
   }

   stage('shutdown')
   {
      node('cadwork2')
      {
         println "trying to shutdown cadwork2 node..."
         //vypnout cadwork2
         def outp = sh(returnStdout: true, script: 'who')
         if (outp == "")
         {
            try
            {
               sh 'sudo shutdown -P +1 &'
               sleep time: 2, unit: 'MINUTES' //we need some grace period to avoid deadlock when this job is done but the node is shutting down and another job tries to wake it - the wake will not come through and the successive job will be on hold.
            } catch(IOException ex){
               println "Shutdown successful, node disconnected."
            } catch(e) {
               println "another exception... maybe the shutdown was unsuccessful"
            }
         }
         else
         {
            println "someone is logged:"
            println outp
         }
      }
   }
}

def printThrowable(e)
{
	echo e.toString() + "\nSTACK TRACE:\n" + e.getStackTrace().toString();
}

def msvcXBuild(generator, prefixIndex, prefixes, testMap)
{
   def buildSuccess = false;
   def repo = 'gpuengine-code'
   def buildDir = 'gpuengine-code-build'
   def scripts = 'build_script/jenkins2/gpue'
   def buildPrefix = prefixes[prefixIndex]

   checkoutRepos(repo)
   CMakeGE(repo, scripts, buildDir, generator)
   msbuildGE(buildDir)

   buildSuccess = true;
   
   testMap[prefixIndex] = runTestsWin(scripts, buildPrefix)
   
   dir('log')
   {
      echo "${buildPrefix}unit_tests"
      stash includes: "${buildPrefix}tests.txt", name: "${buildPrefix}unit_tests", useDefaultExcludes: false
   }
   
   return buildSuccess;
            
}

def makeBuild(generator, prefixIndex, prefixes, testMap)
{
   def buildSuccess = false;
   def repo = 'gpuengine-code'
   def buildDir = 'gpuengine-code-build'
   def scripts = 'build_script/jenkins2/gpue'
   def buildPrefix = prefixes[prefixIndex]

   checkoutRepos(repo)
   CMakeGE(repo, scripts, buildDir, generator)
   gccBuildGE(buildDir)
   
   buildSuccess = true;
   
   testMap[prefixIndex] = runTestsLinux(buildPrefix, buildDir)
   
   dir('log')
   {
      echo "${buildPrefix}unit_tests"
      stash includes: "${buildPrefix}tests.txt", name: "${buildPrefix}unit_tests", useDefaultExcludes: false
   }
   
   return buildSuccess;
}

def checkoutRepos(gpueRepo)
{
   checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'build_script'], [$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'jenkins2/gpue']]]], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/forry/utils.git']]])
   
   checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: gpueRepo]], submoduleCfg: [], userRemoteConfigs: [[url: 'git://git.code.sf.net/p/gpuengine/code']]])
}

def CMakeGE(repo, scriptDir, buildDir, generator)
{
   def cmake = tool name: 'CMake', type: 'hudson.plugins.cmake.CmakeTool'
   //clean build dir
   sh "rm -rf ${buildDir}"
   sh "mkdir ${buildDir}"
   dir(buildDir) 
   {
      sh "${cmake} -C ../${scriptDir}/msvc2015/cache_min.cmake -G \"${generator}\" ../${repo}"
   }
}

def msbuildGE(buildDir)
{
   def msbuild = tool name: 'MSBUILD4', type: 'hudson.plugins.msbuild.MsBuildInstallation'
   bat "${msbuild}/msbuild.exe /p:Configuration=release ${buildDir}/ALL_BUILD.vcxproj"
}

def gccBuildGE(buildDir)
{
   sh "make -j4 -C ${buildDir}"
}

def runTestsWin(scripts, buildPrefix)
{
   def testRet = bat( returnStatus: true, script: "${scripts}/runTests.bat ${buildPrefix}")
   return testRet == 0;
}

def runTestsLinux(buildPrefix, buildDir)
{
   sh 'if [ ! -d ./log ]; then mkdir ./log; fi'
   sh 'rm -rf ./log/*'
   def testRet = sh returnStatus: true, script: "run-parts ${buildDir}/tests/ > ./log/${buildPrefix}tests.txt"
   return testRet == 0;
}

@NonCPS
def mapToList(depmap) {
    def dlist = []
    for (def entry2 in depmap) {
        dlist.add(new java.util.AbstractMap.SimpleImmutableEntry(entry2.key, entry2.value))
    }
    dlist
}

return this;