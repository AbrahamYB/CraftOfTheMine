package com.botwclimb.dev;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Stands in for NeoForge's annotation of the same name; the new framework never prefixes template names, so this only keeps the test classes unchanged. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PrefixGameTestTemplate {
    boolean value();
}
