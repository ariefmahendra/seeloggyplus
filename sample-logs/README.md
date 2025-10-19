# Sample Log Files

This directory contains sample log files for testing SeeLoggyPlus application.

## Files

### app.log
Standard Java application log with various log levels (INFO, DEBUG, WARN, ERROR).

**Format:**
```
YYYY-MM-DD HH:mm:ss.SSS LEVEL [thread] logger.class - message
```

**Regex Pattern for Parsing:**
```regex
(?<timestamp>\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\[(?<thread>[^\]]+)\]\s+(?<logger>\S+)\s+-\s+(?<message>.*)
```

**Usage:**
1. Open SeeLoggyPlus
2. Go to Settings > Parsing Configuration
3. Select "Default Log Format" or create a new configuration with the regex above
4. Open this file: File > Open File > Select app.log
5. The table will show columns: Line, timestamp, level, thread, logger, message

## Testing Features

### 1. Search Testing
- Search for "ERROR" to find error entries
- Search for "http-thread" to find web requests
- Use regex: "(ERROR|WARN)" to find warnings and errors
- Use regex: "Duration: \d{4,}ms" to find slow requests (>1000ms)

### 2. Log Levels
- INFO: General information (green)
- DEBUG: Debug information (blue)
- WARN: Warnings (yellow)
- ERROR: Errors with stack traces (red)

### 3. Stack Traces
Lines 19-24: SQLException with stack trace
Lines 52-57: MessagingException with stack trace
Lines 71-77: NullPointerException with stack trace

### 4. Performance Analysis
- Line 18: Slow request warning (3456ms)
- Line 84: File upload (2132ms)
- Search "Duration:" to find all request timings

### 5. JSON Content
Line 14 contains JSON: `{"username":"john.doe","email":"john@example.com"}`
- Click on that row
- In the detail panel, click "Prettify JSON"

## Creating Your Own Sample Logs

You can add more log files here for testing different formats:

### Apache Access Log Format
```
127.0.0.1 - - [15/Jan/2024:10:30:45 +0000] "GET /api/users HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
```

### JSON Log Format
```json
{"timestamp":"2024-01-15T10:30:45.123Z","level":"ERROR","service":"auth-service","message":"Login failed"}
```

### Syslog Format
```
Jan 15 10:30:45 server1 sshd[12345]: Failed password for user from 192.168.1.100 port 54321 ssh2
```

## Tips

1. **Large File Testing**: Copy app.log multiple times and concatenate for testing large files
2. **Remote File Testing**: Place logs on SSH-accessible server for remote testing
3. **Custom Patterns**: Experiment with different regex patterns in Parsing Configuration
4. **Performance**: Test parallel parsing with files > 100MB

## Pattern Reference

Common named groups used in log parsing:
- `timestamp`: Date and time
- `level`: Log level (INFO, WARN, ERROR, etc.)
- `thread`: Thread name
- `logger`: Logger class name
- `message`: Log message content
- `ip`: IP address
- `method`: HTTP method
- `path`: Request path
- `status`: HTTP status code
- `duration`: Request duration

---

**Happy Testing!** 🔍📊