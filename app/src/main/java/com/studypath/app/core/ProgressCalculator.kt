package com.studypath.app.core

/** 进度计算：小任务按预估时长加权，部分完成也会推进总进度 */
object ProgressCalculator {

/** weighted = Σ(estimatedMinutes × progress)，total = Σ(estimatedMinutes)；progress 为 0..100，weighted/total 即百分比 */
fun percent(weighted: Long, total: Long): Int =
    if (total <= 0) 0 else (weighted / total).toInt().coerceIn(0, 100)
}
