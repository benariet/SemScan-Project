# How to Start the SemScan API Server

## Option 1: Run from IntelliJ IDEA (Recommended)

1. Open `SemScanApiApplication.java` in IntelliJ
2. Right-click on the file
3. Select **"Run 'SemScanApiApplication.main()'"**
4. Wait for the console to show: `🚀 SemScan API started successfully!`
5. The server will be running on `http://localhost:8080`

## Option 2: Run using Gradle

```powershell
# Windows PowerShell
.\gradlew bootRun

# Or build and run JAR
.\gradlew build
java -jar build\libs\SemScan-API-0.0.1-SNAPSHOT.jar
```

## Option 3: Run from Command Line (if JAR exists)

```powershell
# If you have a built JAR file
java -jar build\libs\SemScan-API-0.0.1-SNAPSHOT.jar
```

## Verify Server is Running

Once started, you should see in the console:
```
🚀 SemScan API started successfully!
📱 API Base URL: http://localhost:8080/
```

Then test with:
```powershell
# Use Invoke-WebRequest instead of curl in PowerShell
Invoke-WebRequest -Uri http://localhost:8080/api/v1/diagnostic/ping -Method GET
```

Or use curl.exe (if available):
```powershell
curl.exe http://localhost:8080/api/v1/diagnostic/ping
```

## Troubleshooting

If the server fails to start, check:
1. **Database connection** - Make sure MySQL is running and SSH tunnel is active (port 3307)
2. **Port 8080** - Make sure nothing else is using port 8080
3. **Check error logs** - Look in `logs/semscan-api-error.log` for startup errors
