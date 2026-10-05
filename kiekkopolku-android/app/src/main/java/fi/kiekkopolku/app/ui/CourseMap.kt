package fi.kiekkopolku.app.ui

import android.content.ComponentCallbacks
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.kiekkopolku.app.R
import fi.kiekkopolku.app.domain.*
import kotlinx.serialization.json.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val SOURCE = "played-courses"
private const val POINTS = "played-points"
private const val CLUSTERS = "played-clusters"

/** Coordinates and opaque local IDs only; no players, scores or credentials are sent to the tile provider. */
internal fun mapFeatures(visits: List<CourseVisit>): String = buildJsonObject {
    put("type", "FeatureCollection")
    put("features", buildJsonArray {
        visits.filter { it.course.hasLocation }.forEach { visit -> add(buildJsonObject {
            put("type", "Feature")
            put("geometry", buildJsonObject {
                put("type", "Point")
                put("coordinates", buildJsonArray { add(visit.course.longitude!!); add(visit.course.latitude!!) })
            })
            put("properties", buildJsonObject { put("courseId", visit.course.id) })
        }) }
    })
}.toString()

@Composable
internal fun CourseMap(history: History, open: (String) -> Unit,
    mapContent: @Composable (List<CourseVisit>, (List<String>) -> Unit) -> Unit = { visits, select -> NativeCourseMap(visits, select) }) {
    val visits = history.courseVisits()
    val located = visits.filter { it.course.hasLocation }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var showMissing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.map_course_count, located.size), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if (located.size < visits.size) TextButton(onClick = { showMissing = true }) {
                Text(stringResource(R.string.map_missing_count, visits.size - located.size))
            }
        }
        if (history.coverage().blockedCards > 0) Text(stringResource(R.string.map_history_limit),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (visits.isEmpty()) Text(stringResource(R.string.map_no_courses), modifier = Modifier.padding(16.dp))
        Box(Modifier.weight(1f)) { mapContent(located) { selectedIds = it } }
    }
    val choices = if (showMissing) visits.filterNot { it.course.hasLocation } else visits.filter { it.course.id in selectedIds }
    if (showMissing || selectedIds.isNotEmpty()) AlertDialog(onDismissRequest = { selectedIds = emptyList(); showMissing = false },
        title = { Text(stringResource(if (showMissing) R.string.map_missing_title else R.string.courses)) },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
            if (showMissing) item { Text(stringResource(R.string.map_missing_help)) }
            items(choices, key = { it.course.id }) { visit -> TextButton(onClick = { selectedIds = emptyList(); showMissing = false; open(visit.course.id) }) {
                Column(Modifier.fillMaxWidth()) {
                    Text(visit.course.name)
                    Text(stringResource(R.string.map_course_detail, visit.entries.size, visit.last), style = MaterialTheme.typography.bodySmall)
                }
            } }
        } }, confirmButton = { TextButton(onClick = { selectedIds = emptyList(); showMissing = false }) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun NativeCourseMap(visits: List<CourseVisit>, select: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSelect by rememberUpdatedState(select)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { if (!disposed) { failed = true; ready = false } }
        }
    }
    fun loadStyle(target: MapLibreMap) {
        ready = false; failed = false
        target.setStyle(STYLE_URL) { style ->
            if (!disposed) {
                style.addSource(GeoJsonSource(SOURCE, "{\"type\":\"FeatureCollection\",\"features\":[]}",
                    GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(14)))
                style.addLayer(CircleLayer(POINTS, SOURCE).withFilter(not(has("point_count")))
                    .withProperties(circleRadius(9f), circleColor(Color.WHITE), circleStrokeWidth(3f), circleStrokeColor(Color.DKGRAY)))
                style.addLayer(CircleLayer(CLUSTERS, SOURCE).withFilter(has("point_count"))
                    .withProperties(circleRadius(22f), circleColor(Color.LTGRAY), circleStrokeWidth(2f), circleStrokeColor(Color.WHITE)))
                style.addLayer(SymbolLayer("played-counts", SOURCE).withFilter(has("point_count"))
                    .withProperties(textField(toString(get("point_count"))), textSize(13f), textColor(Color.BLACK),
                        textFont(arrayOf("Noto Sans Regular")), textAllowOverlap(true)))
                ready = true
            }
        }
    }
    DisposableEffect(mapView, lifecycle) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
            Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
            Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
            Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
            else -> Unit
        } }
        val memory = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { mapView.onLowMemory() }
        }
        context.applicationContext.registerComponentCallbacks(memory)
        lifecycle.addObserver(observer)
        mapView.getMapAsync { target ->
            if (!disposed) {
                map = target
                target.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(64.5, 26.0), 4.0))
                target.addOnMapClickListener { location ->
                    if (!ready) false else {
                        val point = target.projection.toScreenLocation(location)
                        val hit = target.queryRenderedFeatures(RectF(point.x - 16, point.y - 16, point.x + 16, point.y + 16), POINTS, CLUSTERS)
                        val ids = hit.filter { it.hasProperty("courseId") }.map { it.getStringProperty("courseId") }.distinct()
                        if (ids.isNotEmpty()) latestSelect(ids)
                        else if (hit.any { it.hasProperty("point_count") }) target.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(location, (target.cameraPosition.zoom + 2).coerceAtMost(16.0)))
                        hit.isNotEmpty()
                    }
                }
                loadStyle(target)
            }
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memory)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    fun fit() {
        val target = map ?: return
        val coordinates = visits.map { LatLng(it.course.latitude!!, it.course.longitude!!) }.distinct()
        when (coordinates.size) {
            0 -> target.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(64.5, 26.0), 4.0))
            1 -> target.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates.single(), 12.0))
            else -> target.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(coordinates).build(), 64))
        }
    }
    val features = remember(visits) { mapFeatures(visits) }
    LaunchedEffect(ready, features) {
        if (ready) {
            map?.style?.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(features)
            if (visits.isNotEmpty()) mapView.post { if (!disposed) fit() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().padding(bottom = 48.dp))
        FilledTonalButton(onClick = { fit() }, enabled = ready, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Text(stringResource(R.string.map_fit))
        }
        if (!ready && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) Card(Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.map_load_failed))
                TextButton(onClick = { map?.let { loadStyle(it) } }) { Text(stringResource(R.string.retry)) }
            }
        }
        // Explicit credit remains visible independently of the remote style's attribution field.
        Surface(Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfreemap.org/"))) }) {
                Text("© OpenStreetMap · OpenMapTiles · OpenFreeMap", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
