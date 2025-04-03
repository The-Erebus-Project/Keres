package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation, used to mark void method a task.
 * These methods are picked and put into task pool when KeresUserDefinition instance is initialized.
 * If user definition contains just 1 task method - it will stick to executing it.
 * If there are more than 1 method - it will use weight-based random to pick one.
 * 
 * This logic works for both standard and looped runners.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface KeresTask {
    int weight() default 1;
    int delayAfterTaskInMs() default 0;
}
