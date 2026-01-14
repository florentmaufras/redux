package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.EffectHandler
import com.florentmaufras.reduxdemo.universities.api.UniversitiesService
import timber.log.Timber

class UniversitiesEffectHandler(
    private val universitiesService: UniversitiesService = UniversitiesService(),
    private val timber: Timber.Tree = Timber,
): EffectHandler<UniversitiesAction, UniversitiesEffect> {
    override suspend fun handle(effect: UniversitiesEffect): UniversitiesAction? {
        return when(effect) {
            is UniversitiesEffect.LoadUniversities -> loadUniversities(effect.country)
        }
    }

    private suspend fun loadUniversities(country: String): UniversitiesAction {
        return try {
            val list = universitiesService.getUniversities(country)
            UniversitiesAction.UniversitiesLoaded(list)
        } catch (e: Exception) {
            timber.e(e)
            UniversitiesAction.LoadError
        }
    }
}