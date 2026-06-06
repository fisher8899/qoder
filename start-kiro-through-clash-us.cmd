@echo off
set "HTTP_PROXY=http://127.0.0.1:7897"
set "HTTPS_PROXY=http://127.0.0.1:7897"
set "ALL_PROXY=socks5://127.0.0.1:7897"
start "" "%LOCALAPPDATA%\Programs\Kiro\Kiro.exe"
