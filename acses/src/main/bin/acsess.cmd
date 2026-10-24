@echo off
rem Runs acsess from the folder it is installed in: this script sits in bin\, acsess.jar one level up.
java -jar "%~dp0..\acsess.jar" %*
exit /b %errorlevel%
