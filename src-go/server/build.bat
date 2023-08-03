@echo off
@setlocal enabledelayedexpansion
title build Burp-Awesome-TLS
chcp 65001 >nul
@set output=Burp-Awesome-TLS
@set  build_dir=build
@if not exist %build_dir% (
        @mkdir %build_dir%
        @echo create folder %build_dir%
    ) 
@set OS_LIST=windows-dll linux-so darwin-dylib
@set ARCH_LIST=amd64 386 arm64 arm
@echo output:%output%

for %%t in (%OS_LIST%) do (
    for  /f  "tokens=1,2 delims=-"  %%o in ("%%t") do (
        for %%a in (%ARCH_LIST%) do (
            @REM @set GOOS = %%o
            @REM @set GOARCH = %%a
            @echo Compiling for %%o-%%a ...
            @set GOOS = %%o && set GOARCH = %%a && go build  -ldflags "-w -s"  -trimpath  --buildmode=c-shared  -o %build_dir%\%%o-%%a\server.%%p   .\cmd\main.go
        )
    )
)
@echo build finished!
@pause