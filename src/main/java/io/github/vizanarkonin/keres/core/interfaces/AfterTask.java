package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation, that marks a method to be executed after a user task.
 * Accepts opional parameter of taskNames.
 * If set - method will only execute after particular task method.
 * If not set - method will execute after any task method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AfterTask {
    String[] taskNames() default {};
}
