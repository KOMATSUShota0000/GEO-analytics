@echo off
set MAVEN_HOME=C:\Users\komas\.m2\wrapper\dists\apache-maven-3.9.6-bin\3311e1d4\apache-maven-3.9.6
set JAVA_CMD=java
if not "%JAVA_HOME%"=="" set JAVA_CMD=%JAVA_HOME%\bin\java
"%MAVEN_HOME%\bin\mvn.cmd" %*
