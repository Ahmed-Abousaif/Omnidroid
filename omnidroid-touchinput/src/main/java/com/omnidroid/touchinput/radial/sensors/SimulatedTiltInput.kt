package com.omnidroid.touchinput.radial.sensors

import gg.padkit.inputstate.InputState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Release the previous mode immediately, including when no further sensor sample arrives. */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<TiltConfiguration>.toSimulatedInputStates(readings: suspend () -> Flow<FloatArray>): Flow<InputState> =
    flatMapLatest { configuration ->
        if (configuration is TiltConfiguration.Disabled) {
            flowOf(InputState())
        } else {
            flow {
                emit(InputState())
                emitAll(readings().map { configuration.process(it) })
            }
        }
    }.distinctUntilChanged()
