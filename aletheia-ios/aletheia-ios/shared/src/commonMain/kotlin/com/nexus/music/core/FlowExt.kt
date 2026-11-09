package com.nexus.music.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * OPT-04: ограничение частоты потока БЕЗ задержки первой эмиссии (в отличие от `debounce`).
 * Первое значение уходит сразу, затем не чаще одного раза в [periodMs]; из значений, пришедших за паузу,
 * доходит только последнее (conflate), так что итоговое состояние никогда не теряется.
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> =
    conflate().transform { emit(it); delay(periodMs) }
