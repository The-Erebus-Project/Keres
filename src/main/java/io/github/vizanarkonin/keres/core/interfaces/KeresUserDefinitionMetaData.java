package io.github.vizanarkonin.keres.core.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-data annotation, used to allow users to provide more description for created user definition classes.
 * Primarily intended to be a description source for user definitions executed from the Hub - 
 * node will pass the values of this annotation to hub in order to generate consice run description.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface KeresUserDefinitionMetaData {
    String userDefId();
    String description();
}
