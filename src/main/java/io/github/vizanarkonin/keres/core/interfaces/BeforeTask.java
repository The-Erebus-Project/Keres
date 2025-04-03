package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation, that marks a method to be executed before a user task.
 * Accepts opional parameter of taskNames.
 * If set - method will only execute before particular task method.
 * If not set - method will execute before any task method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeforeTask {
    String[] taskNames() default {};
}
