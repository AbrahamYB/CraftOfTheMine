package com.botwclimb.dev;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a headless test method. Minecraft's own annotation was removed in 1.21.5, so from then on the tests carry this one instead (the
 * build swaps the imports, see build.gradle) and {@link TestRegistration} registers them with the new test framework.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    String template() default "empty";

    int timeoutTicks() default 100;
}
