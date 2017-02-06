#!groovy

   
def call(debug = false){
   def result = "FAILED";
   def testMap = [msvc2015:false]
   def messageColorMap = [success:'#36A64F',fail:'#E40000' ,testFail:'#FF9D3C']
   def prefixes = [msvc2015:'msvc2015_']

   stage('builds')
   {
      node('windows' && 'msvc2015') {
         def cmGenerator = "Visual Studio 14 2015 Win64"
         def prefixIndex = 'msvc2015'
         try{
            def success = false;
            success = msvcXBuild(cmGenerator, prefixIndex, prefixes, testMap)
            result = success ? "SUCCESS" : "FAILED";
            echo "${result}"
         } catch (e)
         {
            result = "FAILED"
            if(debug)
               echo "MSVC2015 failed!!!!!!!!!!"
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
         def testssubject = "SUCCEEDED"
         def failedTestMessage = "";
         testMap.each{ key, value ->
			if(!value)
			{
				failedTestMessage += "$key test has FAILED!\n"
				testssubject = "FAILED!"
			}
			attachment += "log/"+prefixes['msvc2015']+"out.txt"
		  };
		try{
          unstash 'unit_tests'
		} catch (e) { attachment = ""}
         def slackMessageColor = result == "SUCCESS"? testssubject == "SUCCEEDED" ? messageColorMap['success'] : messageColorMap['testFail'] : messageColorMap['fail']
         def subject = "$JOB_NAME - Build # $BUILD_NUMBER - $result!, Tests : $testssubject"
         def mailbody = "$JOB_NAME - Build # $BUILD_NUMBER - $result, Tests : $testssubject:\n\nCheck console output at $BUILD_URL to view the results.\n"
         def slackMessage = subject + "\n Check console output at $BUILD_URL to view the results."
         if(debug)
         {
            echo "subject: $subject"
            echo "body:\n $mailbody"
            echo "attachement: $attachment"
            echo "color: $slackMessageColor"
         }
         slackSend color: slackMessageColor, message: slackMessage
         //emailext attachLog: true, body: mailbody, subject: subject, to: env.geRecipients, from: 'jenkins', attachmentsPattern: attachment
      }
   }
}

def printThrowable(e)
{
	echo e.getMessage() + "\nSTACK TRACE:\n" + e.getStackTrace().toString();
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
   
   testMap[prefixIndex] = runTests(scripts, buildPrefix)
   
   stash includes: "log/${buildPrefix}out.txt", name: 'unit_tests', useDefaultExcludes: false
   
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

def runTests(scripts, buildPrefix)
{
   def testRet = bat( returnStatus: true, script: "${scripts}/runTests.bat ${buildPrefix}")
   return testRet == 0;
}

return this;