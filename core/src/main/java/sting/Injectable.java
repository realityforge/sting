package sting;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identify a component type that Sting can create by invoking the constructor.
 * The type must be concrete and should have a single package-access constructor.
 * The constructor can accept zero or more services as arguments. The constructor parameters
 * can be explicitly annotated with a {@link Injector.Input} annotation otherwise the compiler will
 * treat the parameter as if it was annotated with a {@link Injector.Input} annotation with default
 * values for all the elements.
 */
@Target( ElementType.TYPE )
@Retention( RetentionPolicy.RUNTIME )
@Documented
public @interface Injectable
{
}
