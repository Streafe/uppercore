package xyz.upperlevel.uppercore.registry;

import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RegistryTest {

    @Test
    public void basicTest() {
        Plugin pluginA = mock(Plugin.class);
        when(pluginA.getName()).thenReturn("plugin_a");
        Plugin pluginB = mock(Plugin.class);
        when(pluginB.getName()).thenReturn("PLugIN_b");

        RegistryRoot root = new RegistryRoot();
        Registry<String> registryA = root.register(pluginA);
        Registry<Integer> registryB = root.register(pluginB);

        registryA.register("test", "value");
        assertEquals(registryA.get("test"), "value");
        assertEquals(registryA.find("test"), "value");
        assertEquals(root.find("plugin_a@test"), "value");

        registryB.register("some_int", 3);
        assertSame(registryB.get("some_int"), 3);
        assertSame(registryB.find("some_int"), 3);
        assertSame(root.find("plugin_b@some_int"), 3);

        Registry<String> child = registryA.registerChild("child");
        assertEquals(registryA.getChild("child"), child);
        child.register("value", "that");
        assertEquals(registryA.find("child.value"), "that");
        assertEquals(root.find("plugin_a@child.value"), "that");

        assertEquals(child.find("plugin_b@some_int"), 3);
    }

    @Test
    public void visitorTester() {
        Plugin pluginA = mock(Plugin.class);
        when(pluginA.getName()).thenReturn("plUGIn");

        RegistryRoot root = new RegistryRoot();
        Registry<String> registryA = root.register(pluginA);

        registryA.register("a1", "A");
        Registry<Integer> childA = registryA.registerChild("achild");
        childA.register("test", 4);

        AtomicInteger i = new AtomicInteger(0);

        // Normal test
        root.visit(new RegistryVisitor() {
            @Override
            public VisitResult preVisitRegistry(Registry<?> registry) {
                return VisitResult.CONTINUE;
            }

            @Override
            public VisitResult visitEntry(String name, Object value) {
                i.incrementAndGet();
                return VisitResult.CONTINUE;
            }

            @Override
            public VisitResult postVisitRegistry(Registry<?> registry) {
                return VisitResult.CONTINUE;
            }
        });
        assertEquals(2, i.get());


        // Test skip
        i.set(0);
        root.visit(new RegistryVisitor() {
            @Override
            public VisitResult preVisitRegistry(Registry<?> registry) {
                return registry == childA ? VisitResult.SKIP : VisitResult.CONTINUE;
            }

            @Override
            public VisitResult visitEntry(String name, Object value) {
                i.incrementAndGet();
                return VisitResult.CONTINUE;
            }

            @Override
            public VisitResult postVisitRegistry(Registry<?> registry) {
                return VisitResult.CONTINUE;
            }
        });
        assertEquals(1, i.get());

        // Test terminate
        i.set(0);
        root.visit(new RegistryVisitor() {
            @Override
            public VisitResult preVisitRegistry(Registry<?> registry) {
                return registry == childA ? VisitResult.TERMINATE : VisitResult.CONTINUE;
            }

            @Override
            public VisitResult visitEntry(String name, Object value) {
                i.incrementAndGet();
                return VisitResult.CONTINUE;
            }

            @Override
            public VisitResult postVisitRegistry(Registry<?> registry) {
                return VisitResult.CONTINUE;
            }
        });
        assertEquals(0, i.get());
    }
}
