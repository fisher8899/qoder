# Start QODER backend with JDK 17
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$proc = Start-Process -FilePath "D:\qoder\assessment-backend\mvn17.cmd" `
    -ArgumentList "spring-boot:run", "-Dspring-boot.run.profiles=dev" `
    -WorkingDirectory "D:\qoder\assessment-backend" `
    -NoNewWindow -PassThru -RedirectStandardOutput "D:\qoder\assessment-backend\backend-run.log" `
    -RedirectStandardError "D:\qoder\assessment-backend\backend-run.err.log"

Write-Output "Started with PID: $($proc.Id)"
