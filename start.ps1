# EMS-Pro 项目启动脚本 (PowerShell)
# 双击此文件或在PowerShell中运行即可启动项目

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  企业管理系统 Pro 启动中..." -ForegroundColor Yellow
Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
& "C:\apache-maven-3.9.16\bin\mvn.cmd" -f "D:\workbuddy\2026-07-21-14-08-12\ems-pro\pom.xml" spring-boot:run -DskipTests
pause
