package com.islam.composegooglemap

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.libraries.places.api.Places
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.islam.composegooglemap.ui.theme.ComposeGoogleMapTheme
import kotlinx.coroutines.launch



class MainActivity : ComponentActivity() {
    val viewModel by viewModels<LocationViewModel>()
     val activity: Activity = Activity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeGoogleMapTheme {
                LocationScreen()
            }
        }
    }


    @OptIn(ExperimentalPermissionsApi::class, ExperimentalAnimationApi::class)
    @Composable
    fun LocationScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current

        viewModel.fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(context)
        Places.initialize(
            context.applicationContext,
            "AIzaSyCAXkkqwz1pckCD_Qb_AEIyJcR85Y1F9bY"
        )
        viewModel.placesClient = Places.createClient(context)
        viewModel.geoCoder = Geocoder(context)

        val locationPermissionState = rememberMultiplePermissionsState(
            listOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        LaunchedEffect(locationPermissionState.allPermissionsGranted) {
            if (locationPermissionState.allPermissionsGranted) {
                viewModel.getCurrentLocation()
            }
        }

        AnimatedContent(
            viewModel.locationState
        ) { state ->
            when (state) {
                is LocationState.NoPermission -> {
                    Column {
                        Text("We need location permission to continue")
                        Button(onClick = { locationPermissionState.launchMultiplePermissionRequest() }) {
                            Text("Request permission")
                        }
                    }
                }

                is LocationState.LocationDisabled -> {
                    Column {
                        Text("We need location to continue")
                        Button(onClick = { requestLocationEnable() }) {
                            Text("Enable location")
                        }
                    }
                }

                is LocationState.LocationLoading -> {
                    Text("Loading Map")
                }

                is LocationState.Error -> {
                    Column {
                        Text("Error fetching your location")
                        Button(onClick = { viewModel.getCurrentLocation() }) {
                            Text("Retry")
                        }
                    }
                }

                is LocationState.LocationAvailable -> {
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(state.cameraLatLang, 15f)
                    }

                    val mapUiSettings by remember { mutableStateOf(MapUiSettings()) }
                    val mapProperties by remember { mutableStateOf(MapProperties(isMyLocationEnabled = true)) }
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(viewModel.currentLatLong) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(viewModel.currentLatLong))
                    }

                    LaunchedEffect(cameraPositionState.isMoving) {
                        if (!cameraPositionState.isMoving) {
                            viewModel.getAddress(cameraPositionState.position.target)
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        GoogleMap(modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = mapUiSettings,
                            properties = mapProperties,
                            onMapClick = {
                                scope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLng(it))
                                }
                            })
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedVisibility(
                                    viewModel.locationAutofill.isNotEmpty(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(viewModel.locationAutofill) {
                                            Row(modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .clickable {
                                                    viewModel.text = it.address
                                                    viewModel.locationAutofill.clear()
                                                    viewModel.getCoordinates(it)
                                                }) {
                                                Icon(imageVector = Icons.Default.Place, contentDescription ="" )
                                                Text(it.address)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                                OutlinedTextField(
                                    value = viewModel.text, onValueChange = {
                                        viewModel.text = it
                                        viewModel.searchPlaces(it)
                                    }, modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun locationEnabled(): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    private fun requestLocationEnable() {
        activity.let {
            val locationRequest = LocationRequest.create()
            val builder = LocationSettingsRequest
                .Builder()
                .addLocationRequest(locationRequest)
            val task = LocationServices
                .getSettingsClient(it)
                .checkLocationSettings(builder.build())
                .addOnSuccessListener {
                    if (it.locationSettingsStates?.isLocationPresent == true) {
                        viewModel.getCurrentLocation()
                    }
                }
                .addOnFailureListener {
                    if (it is ResolvableApiException) {
                        try {
                            it.startResolutionForResult(activity, 999)
                        } catch (e: IntentSender.SendIntentException) {
                            e.printStackTrace()
                        }
                    }
                }

        }
    }
}




