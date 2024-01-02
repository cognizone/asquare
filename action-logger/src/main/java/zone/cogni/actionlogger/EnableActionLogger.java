package zone.cogni.actionlogger;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(LoggedActionConfiguration.class)
@EnableAsync(mode = AdviceMode.PROXY)
@EnableAspectJAutoProxy
public @interface EnableActionLogger {
}
