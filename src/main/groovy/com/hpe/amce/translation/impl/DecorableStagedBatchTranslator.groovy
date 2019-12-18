package com.hpe.amce.translation.impl

import com.hpe.amce.translation.BatchTranslator

import javax.annotation.Nonnull
import javax.annotation.Nullable

/**
 * Batch translator that can be finely tuned.
 *
 * What will be done around each batch, phase and element can be tuned via
 * {@link DecorableStagedBatchTranslator#aroundBatch},
 * {@link DecorableStagedBatchTranslator#aroundStage} and
 * {@link DecorableStagedBatchTranslator#aroundElement}.
 *
 * With default settings, each stage defined via {@link DecorableStagedBatchTranslator#processingStages}
 * will be applied one-by-one. First stage will receive input batch of elements.
 * Second stage will receive output of first stage. Third stage will receive output of second stage. And so on.
 * Output of the last stage will be returned as final output.
 *
 * By default, errors generated by any stage for any element are ignored and stage output will not contain
 * result of erroneous element processing. This is "best effort" behaviour that can be changed via
 * {@link DecorableStagedBatchTranslator#aroundElement}.
 *
 * O - type of source elements.
 * R - type of result elements.
 * C - type of translation context.
 */
class DecorableStagedBatchTranslator<O, R, C> implements BatchTranslator<O, R, C> {

    /**
     * Processing stages.
     * <br/>
     * Keys are stage names and entries are closures that do the processing.
     * <br/>
     * The closures should take two parameters.
     * Where first parameter is a single event being processed.
     * Its type should be a type of raw event for the first stage.
     * For the second and further stages, its type should be the same
     * as output of previous stage. For example, if previous stage
     * is just a filter on raw events then for the current stage,
     * the first parameter should also be of raw event type.
     * Second parameter has type C and represents context (extra parameters) passed to
     * {@link #translateBatch}.
     * <br/>
     * The closure should return list of processed events (zero if filtered out,
     * exactly one for one-to-one translation, more than one if any extra
     * events are to be injected).
     * <br/>
     * Stages are called in whatever order map iterates them so use ordered maps.
     */
    @Nonnull
    Map<String, Closure<List<?>>> processingStages

    /**
     * Iterates over {@link DecorableStagedBatchTranslator#processingStages}
     * and calls {@link DecorableStagedBatchTranslator#aroundStage} for each.
     *
     * Each next stage gets input produced by previous stage.
     */
    class StagesCaller implements BatchTranslator<O, R, C> {
        @Override
        List<R> translateBatch(@Nullable List<O> elements, @Nullable C context) {
            List<?> result = elements
            processingStages.each { stageName, stageCode ->
                result = aroundStage.applyStage(stageName, stageCode, result, context)
            }
            result
        }
    }

    /**
     * Translates a batch.
     *
     * By default, this is {@link StagesCaller}.
     * You can decorate default translator to add custom behaviour like per-batch tracing or
     * per-batch metrics reporting.
     */
    @Nonnull
    BatchTranslator<O, R, C> aroundBatch = new StagesCaller()

    /**
     * Delegates to {@link DecorableStagedBatchTranslator#aroundElement} to actually
     * process each element.
     */
    class ActualStageProcessor implements AroundStage<C> {
        @Override
        @Nonnull
        List<?> applyStage(@Nonnull String stageName,
                           @Nonnull Closure<List<?>> stageCode, @Nonnull List<?> elements, @Nullable C context) {
            elements.collectMany { element ->
                aroundElement.translateElement(stageName, stageCode, element, context) ?: Collections.emptyList()
            }
        }
    }

    /**
     * Applies translation stage to a batch of elements.
     *
     * By default, this is {@link ActualStageProcessor}.
     * You can decorate default translator to add custom behaviour like per-stage tracing or
     * per-stage metrics reporting.
     */
    @Nonnull
    AroundStage<C> aroundStage = new ActualStageProcessor()

    /**
     * Applies translation to each element.
     *
     * By default, this is {@link ErrorSuppressor} that delegates to {@link StageCaller}.
     * You can use custom decorators around {@link StageCaller} to add custom behaviour
     * like per-element tracing, per-element metrics reporting or reporting element translation errors
     * to log or metrics.
     */
    @Nonnull
    AroundElement<C> aroundElement = new ErrorSuppressor<C>(new StageCaller<C>())

    @Override
    List<R> translateBatch(@Nullable List<O> elements, @Nullable C context) {
        aroundBatch.translateBatch(elements, context)
    }
}
