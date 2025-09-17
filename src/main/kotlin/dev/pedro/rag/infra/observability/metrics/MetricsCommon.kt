package dev.pedro.rag.infra.observability.metrics

object MetricsCommon {
    const val TAG_PROVIDER = "provider"
    const val TAG_MODEL = "model"
    const val TAG_STATUS = "status"
    const val TAG_UPSTREAM_STATUS = "upstream_status"

    const val STATUS_SUCCESS = "success"
    const val STATUS_ERROR = "error"

    val DEFAULT_PERCENTILES: DoubleArray = doubleArrayOf(0.5, 0.9, 0.99)
}
