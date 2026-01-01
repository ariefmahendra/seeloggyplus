import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import java.util.Map;

public class GrokDebug {
    public static void main(String[] args) {
        GrokCompiler grokCompiler = GrokCompiler.newInstance();
        grokCompiler.registerDefaultPatterns();
        grokCompiler.register("EPOCH", "\\d{10}|\\d{13}");
        grokCompiler.register("COMPACT", "\\d{14}");
        grokCompiler.register("APACHE_ERROR_DATE", "%{DAY} %{MONTH} %{MONTHDAY} %{TIME} %{YEAR}");
        grokCompiler.register("US_TIMESTAMP", "%{DATE_US} %{TIME}");
        grokCompiler.register("EU_TIMESTAMP", "%{DATE_EU} %{TIME}");

        String[] patterns = {
                "%{TIMESTAMP_ISO8601:timestamp} \\|-%{LOGLEVEL:level} in %{DATA:context} - %{GREEDYDATA:message}", // Logback
                "%{TIMESTAMP_ISO8601:timestamp}\\s+%{LOGLEVEL:level}\\s+%{GREEDYDATA:message}", // ISO Standard
                "%{IPORHOST:clientip} %{USER:ident} %{USER:auth} \\[%{HTTPDATE:timestamp}\\] \"%{DATA:request}\" %{NUMBER:response} (?:%{NUMBER:bytes}|-)", // Apache
                                                                                                                                                            // Common
                "\\[%{APACHE_ERROR_DATE:timestamp}\\] \\[%{WORD:level}\\] %{GREEDYDATA:message}", // Apache Error
                "%{EPOCH:timestamp} %{GREEDYDATA:message}" // Epoch
        };

        String[] names = { "Logback", "ISO Standard", "Apache Common", "Apache Error", "Epoch" };

        for (int i = 0; i < patterns.length; i++) {
            try {
                Grok grok = grokCompiler.compile(patterns[i]);
                String regex = grok.getNamedRegex();
                System.out.println("Pattern: " + names[i]);
                System.out.println("Has timestamp group? " + regex.contains("(?<timestamp>"));
                // Print substring to avoid massive output
                System.out.println("Regex Start: " + regex.substring(0, Math.min(regex.length(), 200)));
                System.out.println("---");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
