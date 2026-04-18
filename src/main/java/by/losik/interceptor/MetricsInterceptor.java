package by.losik.interceptor;

import by.losik.annotation.Measured;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Measured
@Priority(Interceptor.Priority.APPLICATION)
public class MetricsInterceptor {

    @Inject
    MeterRegistry meterRegistry;

    @AroundInvoke
    public Object measure(InvocationContext context) throws Exception {
        String methodName = context.getMethod().getName();
        String className = context.getTarget().getClass().getSimpleName();

        Measured measured = context.getMethod().getAnnotation(Measured.class);
        String metricName = measured.value().isEmpty()
                ? className + "." + methodName
                : measured.value();

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Object result = context.proceed();
            sample.stop(Timer.builder(metricName)
                    .tag("status", "success")
                    .tags(measured.tags())
                    .register(meterRegistry));
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder(metricName)
                    .tag("status", "error")
                    .tag("error", e.getClass().getSimpleName())
                    .tags(measured.tags())
                    .register(meterRegistry));
            throw e;
        }
    }
}