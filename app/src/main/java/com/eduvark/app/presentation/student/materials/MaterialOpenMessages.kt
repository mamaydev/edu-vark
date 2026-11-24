package com.eduvark.app.presentation.student.materials

import com.eduvark.app.R
import com.eduvark.app.domain.model.MaterialOpenFailure

/** Pemetaan kegagalan buka materi ke string milik Paket 2. */
fun materialOpenFailureText(failure: MaterialOpenFailure): Int = when (failure) {
    MaterialOpenFailure.SESSION_EXPIRED -> R.string.p2_material_open_session_expired
    MaterialOpenFailure.FORBIDDEN -> R.string.p2_material_open_forbidden
    MaterialOpenFailure.NETWORK -> R.string.p2_material_open_network
    MaterialOpenFailure.GENERIC -> R.string.p2_material_open_generic
}
