# SeeLoggy+ Launcher Scripts

## Purpose
Launch SeeLoggy+ with custom max memory settings using your system's Java installation.

## Files
- `launcher.bat` - Windows launcher
- `launcher.sh` - Linux/Mac launcher  
- `launcher.properties` - Configuration file
- `seeloggyplus.jar` - Application JAR

## Requirements
- **Java 21 or higher** installed on your system
- Set `JAVA_HOME` environment variable, OR
- Configure custom Java path in `launcher.properties`

## How to Use

### Windows
```bash
launcher.bat
```

### Linux/Mac
```bash
chmod +x launcher.sh
./launcher.sh
```

## Configuration

### Max Memory
1. Open SeeLoggy+ Preferences (Settings > Preferences)
2. Go to **General** > **Performance**
3. Set **Max Memory (GB)** (1-16 GB)
4. Click Save
5. **Restart** using launcher script

Or manually edit `launcher.properties`:
```properties
max.memory.gb=8
```

### Custom Java Path
If you don't have `JAVA_HOME` set, configure custom Java path in `launcher.properties`:

**Windows:**
```properties
java.home=C:/Program Files/Java/jdk-21
```

**Linux/Mac:**
```properties
java.home=/usr/lib/jvm/java-21-openjdk
```

## Java Detection Priority
1. **Custom path** from `launcher.properties` (`java.home`)
2. **JAVA_HOME** environment variable
3. **System PATH** (default `java` command)

## Default Settings
- Max Memory: **4 GB**
- Java: Uses `JAVA_HOME` or system `java`

## Troubleshooting

### "java not found"
- Install Java 21+ 
- Set `JAVA_HOME` environment variable
- Or configure `java.home` in `launcher.properties`

### Check Java version
```bash
java -version
```
Should show Java 21 or higher.
