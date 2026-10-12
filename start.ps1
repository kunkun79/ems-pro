# EMS-Pro 项目启动脚本 (PowerShell)
# 双击此文件或在PowerShell中运行即可启动项目

# ====== 本机环境配置：若路径与你的机器不符，修改下面两行即可 ======
$jdk17Home = "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
$mavenHome = "C:\apache-maven-3.9.16"
# ==================================================================

$env:JAVA_HOME = $jdk17Home
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  企业管理系统 Pro 启动中..." -ForegroundColor Yellow
Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan

Set-Location -Path $PSScriptRoot
& "$mavenHome\bin\mvn.cmd" -f "$PSScriptRoot\pom.xml" spring-boot:run -DskipTests
pause
