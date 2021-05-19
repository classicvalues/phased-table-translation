package com.hpe.amce.translation.impl

import com.codahale.metrics.Meter
import com.codahale.metrics.MetricRegistry
import groovy.transform.CompileStatic

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Publishes metric about errors generated by decorated translator.
 *
 * Each time decorated translator throws an error,
 * this decorator increments a meter.
 *
 * The name of meter to be incremented is determined by
 * {@link ElementErrorMeteringDecorator#getMeterName}.
 *
 * Parameters of metric can be customized via {@link ElementErrorMeteringDecorator#metricMeterFactory}.
 *
 * C - type of translation context.
 */
@CompileStatic
class ElementErrorMeteringDecorator<C> implements AroundElement<C> {

    /**
     * Translator to be decorated.
     */
    @Nonnull
    AroundElement<C> next

    /**
     * Metric registry to which metrics should be reported.
     */
    @Nonnull
    MetricRegistry metricRegistry

    /**
     * Base name for reported metrics.
     */
    @Nonnull
    String metricsBaseName

    /**
     * Name of the metric that will report errors.
     *
     * The closure should take single String parameter that is stage name causing error and should return metric name.
     *
     * By default, this is {@link #metricsBaseName}STAGENAME.error.
     * Where STAGENAME is a name of the stage that has caused an error.
     */
    @Nonnull
    Closure<String> meterName = { "${metricsBaseName}${it}.error".toString() }

    /**
     * Factory that is to be used to create meter metrics.
     *
     * By default, uses default parameters of {@link com.codahale.metrics.Meter#Meter()}.
     */
    @Nonnull
    MetricRegistry.MetricSupplier<Meter> metricMeterFactory = new MetricRegistry.MetricSupplier<Meter>() {
        @Override
        Meter newMetric() {
            new Meter()
        }
    }

    /**
     * Creates an instance.
     * @param next Translator to be decorated.
     * @param metricRegistry Metric registry where metric should be reported to.
     * @param metricsBaseName Base name (prefix) for metrics.
     */
    ElementErrorMeteringDecorator(
            @Nonnull AroundElement<C> next,
            @Nonnull MetricRegistry metricRegistry,
            @Nonnull String metricsBaseName) {
        this.next = next
        this.metricRegistry = metricRegistry
        this.metricsBaseName = metricsBaseName
    }

    @Override
    // Want to catch Groovy assertion violations
    @SuppressWarnings('CatchThrowable')
    List<?> translateElement(@Nonnull String stageName, @Nonnull Closure<List<?>> stageCode,
                             @Nullable Object element, @Nullable C context) {
        try {
            next.translateElement(stageName, stageCode, element, context)
        } catch (Throwable e) {
            metricRegistry.meter(meterName(stageName), metricMeterFactory).mark()
            throw e
        }
    }
}
