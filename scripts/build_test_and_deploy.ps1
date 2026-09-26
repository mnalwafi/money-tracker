# PowerShell Wrapper for Money Tracker Build, Test & Deploy Pipeline
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
node "$ScriptDir\build_test_and_deploy.js"
