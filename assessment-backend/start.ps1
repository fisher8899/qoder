$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:JWT_SECRET = 'dev-only-please-change-me-min-32-bytes-xxx'
$cmd = "set JAVA_HOME=$env:JAVA_HOME&& set PATH=%JAVA_HOME%\bin;%PATH%&& set JWT_SECRET=$env:JWT_SECRET&& mvn spring-boot:run -Dspring-boot.run.profiles=dev"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd -WorkingDirectory "D:\qoder\assessment-backend" -NoNewWindow -PassThru | Select-Object Id
