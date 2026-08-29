@echo off
REM EMS-Pro 项目启动脚本 (CMD)
REM 双击此文件即可启动项目

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
echo ========================================
echo   企业管理系统 Pro 启动中...
echo   JAVA_HOME: %JAVA_HOME%
echo   启动后访问: http://localhost:9090
echo ========================================

cd /d D:\workbuddy\2026-07-21-14-08-12\ems-pro
C:\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run -DskipTests
pause
