package com.florentmaufras.reduxdemo.universities.data

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import com.florentmaufras.redux.Store

class UniversitiesStore(
    application: Application? = null,
    initialState: UniversitiesState = UniversitiesState(),
    override val reducer: UniversitiesReducer = UniversitiesReducer(),
    override val effectHandler: UniversitiesEffectHandler = UniversitiesEffectHandler(
        openUrl = { url ->
            application?.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    )
) : Store<UniversitiesAction, UniversitiesState, UniversitiesEffect>(initialState) {

    init {
        if (state.value.countrySearched.isNotBlank()) {
            dispatch(UniversitiesAction.LoadUniversities(state.value.countrySearched))
        }
    }
}
