package de.caluga.morphium.quarkus.transaction;

import jakarta.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interceptor binding that wraps the annotated method (or all methods of a class)
 * in a Morphium transaction. On success the transaction is committed; on exception
 * it is rolled back and the exception is re-thrown.
 */
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MorphiumTransactional {}
