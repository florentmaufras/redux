package com.florentmaufras.reduxdemo.universities.data

import com.florentmaufras.redux.Store

class UniversitiesStore(
    initialState: UniversitiesState = UniversitiesState(),
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    override val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler()
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect, UniversitiesReducer, UniversitiesEffectHandler>(
    initialState
)