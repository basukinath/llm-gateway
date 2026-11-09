@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF)
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0__%"=="" SET __MVNW_ARG0__=%~dp0mvnw.cmd
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $env:MVNW_REPOURL = if ($env:MVNW_REPOURL) {$env:MVNW_REPOURL} else {'https://repo.maven.apache.org/maven2'}; $sslProtocols = [Net.SecurityProtocolType]::Tls12; if ($PSVersionTable.PSVersion.Major -ge 6) { $sslProtocols = 'Tls12','Tls13'} ; [Net.ServicePointManager]::SecurityProtocol = $sslProtocols; $distributionUrl = (Get-Content -Raw \"$scriptDir/.mvn/wrapper/maven-wrapper.properties\" | ConvertFrom-StringData).distributionUrl; $wrapperUrl = (Get-Content -Raw \"$scriptDir/.mvn/wrapper/maven-wrapper.properties\" | ConvertFrom-StringData).wrapperUrl; $wrapperJar = \"$scriptDir/.mvn/wrapper/maven-wrapper.jar\"; if ($wrapperUrl -and -not (Test-Path $wrapperJar)) { Write-Host \"Downloading Maven Wrapper from $wrapperUrl\"; (New-Object Net.WebClient).DownloadFile($wrapperUrl, $wrapperJar) }; Write-Output \"MVNW_JAVA_HOME=$env:JAVA_HOME\"; if (Test-Path $wrapperJar) { Write-Output \"MVNW_CMD=java -jar '$wrapperJar' $args\" } else { Write-Output \"MVNW_CMD=java -classpath '$scriptDir/.mvn/wrapper' org.apache.maven.wrapper.MavenWrapperMain $args\" } }"`) DO @IF "%%A"=="MVNW_CMD" (SET "MVNW_EXEC=%%B") ELSE IF "%%A"=="MVNW_JAVA_HOME" (SET "MVNW_JAVA_HOME=%%B")
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=

@IF NOT "%MVNW_JAVA_HOME%"=="" SET "JAVA_HOME=%MVNW_JAVA_HOME%"
@IF "%JAVA_HOME%"=="" (
    @FOR %%i IN (java.exe) DO @SET "JAVACMD=%%~$PATH:i"
) ELSE (
    @SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
)

@IF "%JAVACMD%"=="" (
    @ECHO Error: JAVA_HOME is not defined and java.exe is not in the PATH. >&2
    @GOTO error
)

@SET MAVEN_PROJECTBASEDIR=%~dp0
%MVNW_EXEC%
@IF ERRORLEVEL 1 GOTO error
@GOTO end

:error
@SET ERROR_CODE=1

:end
@EXIT /B %ERROR_CODE%
