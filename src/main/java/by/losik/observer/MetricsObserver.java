package by.losik.observer;

import by.losik.event.MetricEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsObserver {

    @Inject
    MeterRegistry meterRegistry;

    public void onMetricEvent(@ObservesAsync MetricEvent event) {
        Counter.builder(event.name())
                .tag("status", event.status())
                .tag("type", event.additionalTag() != null ? event.additionalTag() : "default")
                .register(meterRegistry)
                .increment();
    }
}