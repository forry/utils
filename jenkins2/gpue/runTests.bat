rem @echo off
rem set WORKSPACE="C:\jenkins_slave\workspace\GPUnGine_tests_msvc2013"
mkdir %WORKSPACE%\log
echo "flag0"
del log\* /Q

cd build\bin
set /a "RET=0"

FOR %%i in (*.exe) do (
   %%i >> %WORKSPACE%\log\%1out.txt
   IF %errorlevel% neq 0 echo test/app %%i exited with error %errorlevel%
   set /a "RET|=%errorlevel%"
)

cd ..\..
exit %RET%