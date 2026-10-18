package com.seeloggyplus.features.logs.application;

import com.seeloggyplus.features.logs.application.port.LogFileRepository;
import com.seeloggyplus.shared.database.FatalDatabaseException;
import com.seeloggyplus.shared.database.NotFoundException;
import com.seeloggyplus.shared.model.LogFile;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LogFileServiceImplTest {

    @Test
    void insertValidatesBeforeCallingRepository() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            fail("invalid input must not reach persistence");
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> service.insertLogFile(null));
        assertThrows(IllegalArgumentException.class, () -> service.insertLogFile(file("", "/logs")));
        assertThrows(IllegalArgumentException.class, () -> service.insertLogFile(file("app.log", " ")));
    }

    @Test
    void insertGeneratesIdAndPreservesAnExistingId() {
        AtomicReference<LogFile> saved = new AtomicReference<>();
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            assertEquals("insert", method);
            saved.set((LogFile) args[0]);
            return null;
        }));
        LogFile file = file("app.log", "/logs");
        service.insertLogFile(file);
        assertSame(file, saved.get());
        assertDoesNotThrow(() -> UUID.fromString(file.getId()));
        file.setId("existing-id");
        service.insertLogFile(file);
        assertEquals("existing-id", saved.get().getId());
    }

    @Test
    void lookupDistinguishesMissingRecordFromDatabaseFailure() {
        LogFileServiceImpl missing = new LogFileServiceImpl(repository((method, args) -> {
            throw new NotFoundException("missing");
        }));
        assertNull(missing.getLogFileById("missing"));
        FatalDatabaseException failure = new FatalDatabaseException("unavailable", null);
        LogFileServiceImpl unavailable = new LogFileServiceImpl(repository((method, args) -> {
            throw failure;
        }));
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> unavailable.getLogFileById("id")).getCause());
    }

    @Test
    void serverScopedLookupKeepsArgumentOrderAndRemoteIdentity() {
        LogFile expected = file("app.log", "/logs");
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            assertEquals("findByPathNameAndServer", method);
            assertArrayEquals(new Object[] { "/logs", "app.log", "server-1", true }, args);
            return expected;
        }));
        assertSame(expected, service.getLogFileByPathNameAndServer("app.log", "/logs", "server-1", true));
    }

    @Test
    void parsingConfigAssociationCanBeCleared() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            assertEquals("updateParsingConfigId", method);
            assertArrayEquals(new Object[] { null, "log-1" }, args);
            return null;
        }));
        service.updateParsingConfigIdForLogFiles(null, "log-1");
    }

    @Test
    void deleteValidatesTheIdBeforeTouchingPersistence() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            fail("invalid input must not reach persistence");
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> service.deleteLogFileById(null));
        assertThrows(IllegalArgumentException.class, () -> service.deleteLogFileById("  "));
    }

    @Test
    void deleteReportsMissingRecordAndFatalFailuresDistinctly() {
        LogFileServiceImpl missing = new LogFileServiceImpl(repository((method, args) -> {
            throw new NotFoundException("missing");
        }));
        RuntimeException notFound = assertThrows(RuntimeException.class, () -> missing.deleteLogFileById("id"));
        assertSame(NotFoundException.class, notFound.getCause().getClass());

        FatalDatabaseException fatal = new FatalDatabaseException("db down", null);
        LogFileServiceImpl broken = new LogFileServiceImpl(repository((method, args) -> {
            throw fatal;
        }));
        assertSame(fatal, assertThrows(RuntimeException.class, () -> broken.deleteLogFileById("id")).getCause());
    }

    @Test
    void updateValidatesNullAndEmptyId() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            fail("invalid input must not reach persistence");
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> service.updateLogFile(null));
        LogFile withoutId = file("app.log", "/logs");
        withoutId.setId(" ");
        assertThrows(IllegalArgumentException.class, () -> service.updateLogFile(withoutId));
    }

    @Test
    void updateReportsMissingRecordAndFatalFailuresDistinctly() {
        LogFileServiceImpl missing = new LogFileServiceImpl(repository((method, args) -> {
            throw new NotFoundException("missing");
        }));
        LogFile existing = file("a.log", "/l");
        existing.setId("log-2");
        assertSame(NotFoundException.class,
                assertThrows(RuntimeException.class, () -> missing.updateLogFile(existing)).getCause().getClass());

        FatalDatabaseException fatal = new FatalDatabaseException("db down", null);
        LogFileServiceImpl broken = new LogFileServiceImpl(repository((method, args) -> {
            throw fatal;
        }));
        LogFile existing2 = file("a.log", "/l");
        existing2.setId("log-2");
        assertSame(fatal,
                assertThrows(RuntimeException.class, () -> broken.updateLogFile(existing2)).getCause());
    }

    @Test
    void lookupsValidateTheirIdsAndPaths() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            fail("invalid input must not reach persistence");
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> service.getLogFileById(""));
        assertThrows(IllegalArgumentException.class, () -> service.getLogFileByPathAndName(" ", "/logs"));
        assertThrows(IllegalArgumentException.class, () -> service.getLogFileByPathAndName("app.log", " "));
        assertThrows(IllegalArgumentException.class, () -> service.getLogFileByPathNameAndServer(null, "/logs", "s", true));
        assertThrows(IllegalArgumentException.class, () -> service.getLogFileByPathNameAndServer("a.log", "", "s", true));
    }

    @Test
    void pathLookupReturnsNullWhenTheRecordIsMissing() {
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            throw new NotFoundException("missing");
        }));
        assertNull(service.getLogFileByPathAndName("app.log", "/logs"));
        assertNull(service.getLogFileByPathNameAndServer("app.log", "/logs", "server", true));
    }

    @Test
    void deleteAllWrapsFatalFailures() {
        FatalDatabaseException fatal = new FatalDatabaseException("db down", null);
        LogFileServiceImpl service = new LogFileServiceImpl(repository((method, args) -> {
            throw fatal;
        }));
        assertSame(fatal, assertThrows(RuntimeException.class, service::deleteAllLogFiles).getCause());
    }

    @Test
    void parsingConfigUpdateValidatesTheLogFileIdAndReportsFailures() {
        LogFileServiceImpl invalid = new LogFileServiceImpl(repository((method, args) -> {
            fail("invalid input must not reach persistence");
            return null;
        }));
        assertThrows(IllegalArgumentException.class, () -> invalid.updateParsingConfigIdForLogFiles("cfg", " "));

        LogFileServiceImpl missing = new LogFileServiceImpl(repository((method, args) -> {
            throw new NotFoundException("missing");
        }));
        assertSame(NotFoundException.class,
                assertThrows(RuntimeException.class,
                        () -> missing.updateParsingConfigIdForLogFiles("cfg", "log-1")).getCause().getClass());

        FatalDatabaseException fatal = new FatalDatabaseException("db down", null);
        LogFileServiceImpl broken = new LogFileServiceImpl(repository((method, args) -> {
            throw fatal;
        }));
        assertSame(fatal,
                assertThrows(RuntimeException.class,
                        () -> broken.updateParsingConfigIdForLogFiles("cfg", "log-1")).getCause());
    }

    private static LogFile file(String name, String path) {
        return new LogFile(null, name, path, "10", "today", false, null, null);
    }

    private interface Call {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static LogFileRepository repository(Call call) {
        return (LogFileRepository) Proxy.newProxyInstance(LogFileRepository.class.getClassLoader(),
                new Class<?>[] { LogFileRepository.class },
                (proxy, method, args) -> call.invoke(method.getName(), args));
    }
}
