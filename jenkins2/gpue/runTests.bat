rem @echo off
rem set WORKSPACE=C:/jenkins2_slave/workspace/GPUE/GPUEngine_main
setlocal enabledelayedexpansion
rmdir "%WORKSPACE%/log" /S /Q

mkdir "%WORKSPACE%/log"
cd "%WORKSPACE%/gpuengine-code-build/bin/"
set /a "RET=0"

FOR %%i in (*.exe) do (
   %%i >> %WORKSPACE%/log/%1out.txt
   IF !errorlevel! neq 0 echo test/app %%i exited with error !errorlevel!
   set /a "RET|=!errorlevel!"
)

exit %RET%
