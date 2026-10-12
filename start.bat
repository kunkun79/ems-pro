@echo off
REM EMS-Pro 项目启动脚本 (CMD)
REM 双击此文件即可启动项目

REM ====== 本机环境配置：若路径与你的机器不符，修改下面两行即可 ======
set "JDK17_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
set "MAVEN_HOME=C:\apache-maven-3.9.16"
REM ==================================================================

set "JAVA_HOME=%JDK17_HOME%"
echo ========================================
echo   企业管理系统 Pro 启动中...
echo   JAVA_HOME: %JAVA_HOME%
echo   启动后访问: http://localhost:9090
echo ========================================

cd /d "%~dp0"
call "%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run -DskipTests
pause
