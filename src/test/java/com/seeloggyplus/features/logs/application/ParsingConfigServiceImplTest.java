package com.seeloggyplus.features.logs.application;

import com.seeloggyplus.features.logs.application.port.ParsingConfigRepository;
import com.seeloggyplus.shared.model.ParsingConfig;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ParsingConfigServiceImplTest {

    @Test
    void saveGeneratesIdAndKeepsAnExistingId() {
        AtomicReference<ParsingConfig> saved = new AtomicReference<>();
        ParsingConfigRepository repository = repository((method, args) -> {
            assertEquals("save", method);
            saved.set((ParsingConfig) args[0]);
            return null;
        });
        ParsingConfigServiceImpl service = new ParsingConfigServiceImpl(repository, lines -> null);
        ParsingConfig config = new ParsingConfig("Raw", "(?<message>.*)");
        config.setId(null);
        service.save(config);
        assertSame(config, saved.get());
        assertDoesNotThrow(() -> UUID.fromString(config.getId()));
        config.setId("existing-id");
        service.save(config);
        assertEquals("existing-id", saved.get().getId());
    }

    @Test
    void detectionUsesInjectedDetectorWithoutPersistence() {
        List<String> samples = List.of("a log line");
        ParsingConfig expected = ParsingConfig.createRawConfig();
        ParsingConfigServiceImpl service = new ParsingConfigServiceImpl(repository((method, args) -> {
            fail("format detection must not access persistence");
            return null;
        }), lines -> {
            assertSame(samples, lines);
            return expected;
        });
        assertSame(expected, service.detectLogFormat(samples));
    }

    @Test
    void queriesAndMutationsDelegateToInjectedRepository() {
        ParsingConfig config = ParsingConfig.createRawConfig();
        AtomicReference<String> invoked = new AtomicReference<>();
        ParsingConfigServiceImpl service = new ParsingConfigServiceImpl(repository((method, args) -> {
            invoked.set(method);
            return switch (method) {
                case "findById", "findDefault" -> Optional.of(config);
                case "findAll" -> List.of(config);
                default -> null;
            };
        }), lines -> null);
        assertSame(config, service.findById("id").orElseThrow());
        assertEquals(List.of(config), service.findAll());
        assertSame(config, service.findDefault().orElseThrow());
        service.update(config);
        assertEquals("update", invoked.get());
        service.delete(config);
        assertEquals("delete", invoked.get());
    }

    private interface Call {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private static ParsingConfigRepository repository(Call call) {
        return (ParsingConfigRepository) Proxy.newProxyInstance(ParsingConfigRepository.class.getClassLoader(),
                new Class<?>[] { ParsingConfigRepository.class },
                (proxy, method, args) -> call.invoke(method.getName(), args));
    }
}
