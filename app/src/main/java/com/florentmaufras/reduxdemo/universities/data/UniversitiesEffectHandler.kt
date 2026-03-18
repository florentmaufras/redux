package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.EffectHandler
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

class UniversitiesEffectHandler(
    private val universitiesService: UniversitiesService = UniversitiesService(),
    private val timber: Timber.Tree = Timber,
) : EffectHandler<UniversitiesAction, UniversitiesEffect> {
    override fun handle(effect: UniversitiesEffect): Flow<UniversitiesAction> = when (effect) {
        is UniversitiesEffect.LoadUniversities -> flow {
            try {
                val list = universitiesService.getUniversities(effect.country)
                emit(UniversitiesAction.UniversitiesLoaded(list))
            } catch (e: Exception) {
                timber.e(e)
                emit(UniversitiesAction.LoadError)
            }
        }
    }
}
