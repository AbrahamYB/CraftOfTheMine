package com.botwclimb.dev;

import com.botwclimb.BotwClimb;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Registers the headless tests with the test framework of Minecraft 1.21.5 and later (a registry of test functions, and test instances that
 * name them). Older versions find the annotated methods themselves, so there is nothing to do there.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class TestRegistration {
    private TestRegistration() {}

    //? if >=1.21.11 {
    /*private static final Class<?>[] CLASSES = {ClimbPlannerTests.class, PlotTests.class};

    private static net.minecraft.resources.Identifier id(String name) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(BotwClimb.MOD_ID, name);
    }

    private static java.util.List<java.lang.reflect.Method> tests() {
        java.util.List<java.lang.reflect.Method> out = new java.util.ArrayList<>();
        for (Class<?> c : CLASSES) for (var m : c.getDeclaredMethods()) if (m.isAnnotationPresent(GameTest.class)) out.add(m);
        return out;
    }

    @SubscribeEvent
    public static void functions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
            for (var m : tests()) {
                helper.register(id(m.getName().toLowerCase(java.util.Locale.ROOT)), (java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper>) h -> {
                    try {
                        m.setAccessible(true);
                        m.invoke(null, h);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException r) throw r;
                        throw new RuntimeException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    @SubscribeEvent
    public static void instances(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        var env = event.registerEnvironment(id("default"));
        for (var m : tests()) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id(m.getName().toLowerCase(java.util.Locale.ROOT)));
            int ticks = m.getAnnotation(GameTest.class).timeoutTicks();
            event.registerTest(id(m.getName().toLowerCase(java.util.Locale.ROOT)),
                    new net.minecraft.gametest.framework.FunctionGameTestInstance(key, new net.minecraft.gametest.framework.TestData<>(env, id(m.getAnnotation(GameTest.class).template()), ticks, 0, true)));
        }
    }
    *///?}
}
