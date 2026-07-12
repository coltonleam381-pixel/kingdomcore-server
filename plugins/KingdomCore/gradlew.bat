@echo off
setlocal
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar;%DIRNAME%gradle\wrapper\gradle-wrapper-shared.jar;%DIRNAME%gradle\wrapper\gradle-cli.jar;%DIRNAME%gradle\wrapper\gradle-files.jar

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
