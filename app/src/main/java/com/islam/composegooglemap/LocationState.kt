package com.islam.composegooglemap

import com.google.android.gms.maps.model.LatLng

sealed class LocationState {
    object NoPermission:LocationState()
    object LocationDisabled:LocationState()
    object LocationLoading:LocationState()
    data class LocationAvailable(val cameraLatLang:LatLng):LocationState()
    object Error:LocationState()
}