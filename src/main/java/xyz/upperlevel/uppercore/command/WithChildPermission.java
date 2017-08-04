package xyz.upperlevel.uppercore.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithChildPermission {
    DefaultPermission def() default DefaultPermission.FALSE;

    String desc() default "";
}
