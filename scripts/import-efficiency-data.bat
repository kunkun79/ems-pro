@echo off
REM =============================================================
REM  EMS-Pro - Import employee efficiency simulation data
REM  Requires: mysql.exe in PATH, database ems_pro already exists
REM =============================================================
setlocal

set DB=ems_pro
set USER=root
set SQLDIR=%~dp0..\src\main\resources\sql

where mysql >nul 2>nul
if errorlevel 1 (
    echo [ERROR] mysql.exe not found in PATH.
    echo Add MySQL bin directory to PATH, for example:
    echo   set PATH=C:\Program Files\MySQL\MySQL Server 8.0\bin;%%PATH%%
    goto end
)

echo =============================================================
echo  EMS-Pro efficiency data import
echo  Database : %DB%
echo  Source   : %SQLDIR%
echo =============================================================
set /p PWD=Enter MySQL password for %USER%:

echo.
echo [1/5] Creating tables attendance / task / performance ...
mysql -u %USER% -p%PWD% %DB% < "%SQLDIR%\efficiency-schema.sql"
if errorlevel 1 goto fail

echo [2/5] Importing employee        430 rows ...
mysql -u %USER% -p%PWD% %DB% < "%SQLDIR%\efficiency-employee.sql"
if errorlevel 1 goto fail

echo [3/5] Importing attendance   117488 rows  ~13MB, 1-3 min ...
mysql -u %USER% -p%PWD% %DB% < "%SQLDIR%\efficiency-attendance.sql"
if errorlevel 1 goto fail

echo [4/5] Importing task          49519 rows ...
mysql -u %USER% -p%PWD% %DB% < "%SQLDIR%\efficiency-task.sql"
if errorlevel 1 goto fail

echo [5/5] Importing performance    5796 rows ...
mysql -u %USER% -p%PWD% %DB% < "%SQLDIR%\efficiency-performance.sql"
if errorlevel 1 goto fail

echo.
echo Verifying row counts ...
mysql -u %USER% -p%PWD% %DB% -e "SELECT (SELECT COUNT(*) FROM employee) AS employees, (SELECT COUNT(*) FROM attendance) AS attendance, (SELECT COUNT(*) FROM task) AS tasks, (SELECT COUNT(*) FROM performance) AS performance;"

echo.
echo Sample: monthly department efficiency preview
mysql -u %USER% -p%PWD% %DB% -e "SELECT d.name AS dept, DATE_FORMAT(a.work_date,'%%Y-%%m') AS ym, COUNT(*) AS records, ROUND(SUM(a.leave_type='正常')/COUNT(*),4) AS attendance_rate, ROUND(AVG(a.overtime_hours),2) AS avg_overtime FROM attendance a JOIN employee e ON a.emp_id=e.id JOIN department d ON e.dept_id=d.id GROUP BY d.name, ym ORDER BY ym LIMIT 12;"

echo.
echo [DONE] All data imported.
goto end

:fail
echo.
echo [FAILED] Import aborted. Check:
echo   1. MySQL service is running
echo   2. Database %DB% exists  (CREATE DATABASE ems_pro;)
echo   3. Password is correct and user has write privilege

:end
endlocal
pause
