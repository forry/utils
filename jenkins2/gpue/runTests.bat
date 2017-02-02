rem @echo off
rem set WORKSPACE=C:/jenkins2_slave/workspace/GPUE/GPUEngine_main
mkdir %WORKSPACE%/log
echo "flag0"
del %WORKSPACE%/log/* /Q

cd %WORKSPACE%/gpuengine-code-build/bin/
set /a "RET=0"

FOR %%i in (*.exe) do (
   %%i >> %WORKSPACE%/log/%1out.txt
   IF %errorlevel% neq 0 echo test/app %%i exited with error %errorlevel%
   set /a "RET|=%errorlevel%"
)

rem exit %RET%