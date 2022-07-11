package site.kotty_kov.dz_maps

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.maps.android.ktx.awaitAnimation
import com.google.maps.android.ktx.awaitMap
import com.google.maps.android.ktx.model.cameraPosition
import kotlinx.coroutines.launch
import pub.devrel.easypermissions.EasyPermissions
import site.kotty_kov.dz_maps.databinding.ActivityMapsBinding
import java.lang.reflect.Type
import java.util.*


class MapsActivity : AppCompatActivity() {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var canPlaceNew: Boolean = false
    private var editMode = false
    private var removeingMode = false
    private var markers = mutableListOf<MarkerOptions>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        loadMarkers()


        lifecycleScope.launchWhenCreated {
            mMap = mapFragment.awaitMap()

            //load
            markers.forEach {
                mMap.addMarker(MarkerOptions().position(it.position).title(it.title))
            }

            // Marker click
            mMap.setOnMarkerClickListener { marker ->
                marker.showInfoWindow()

                if (removeingMode) {
                    marker.hideInfoWindow()
                    marker.remove()
                    markers = markers.filterNot { it.title == marker.title }.toMutableList()
                    removeingMode = false
                }

                if (editMode) {
                    marker.hideInfoWindow()
                    showChangeDialog(marker.title) { newName ->
                        markers = markers.map {
                            if (it.title == marker.title) {
                                MarkerOptions().position(it.position).title(newName)
                            } else {
                                it
                            }
                        }.toMutableList()
                        marker.title = newName
                        marker.showInfoWindow()
                        editMode = false
                    }
                }

                true
            }


            //UI setup
            mMap.isBuildingsEnabled = true
            mMap.isTrafficEnabled = true
            mMap.uiSettings.isZoomControlsEnabled = true
            requestPermssitionMyLocation()


            // Map click listener
            mMap.setOnMapClickListener { coords ->
                if (canPlaceNew) {
                    val sydney = LatLng(coords.latitude, coords.longitude)
                    showNewDialog { name ->
                        mMap.addMarker(MarkerOptions().position(sydney).title(name))
                        markers.add(MarkerOptions().position(sydney).title(name))
                    }
                    canPlaceNew = false

                }

            }
        }
    }

    private fun loadMarkers() {
        val founderListType: Type = object : TypeToken<ArrayList<MarkersV>?>() {}.type
        getPreferences(MODE_PRIVATE)?.let { hp ->
            hp.getString("markers", "[]")?.let { list ->
                val prepList: ArrayList<MarkersV> = GsonBuilder()
                    .create()
                    .fromJson(list, founderListType)
                markers = prepList.map { MarkerOptions().position(it.coords).title(it.title) }
                    .toMutableList()

            }

        }
    }

    override fun onStop() {
        super.onStop()
        saveMakers()
    }

    private fun saveMakers() {
        val sharedPref = getPreferences(MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("markers", GsonBuilder().create().toJson(convert(markers)))
            apply()
        }
    }


    private fun convert(markers: MutableList<MarkerOptions>): MutableList<MarkersV> {
        val items = mutableListOf<MarkersV>()
        markers.forEach {
            items.add(MarkersV(it.title ?: "empty", it.position))
        }
        return items
    }

    @SuppressLint("MissingPermission")
    private fun requestPermssitionMyLocation() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (EasyPermissions.hasPermissions(this@MapsActivity, *perms)) {
            mMap.apply {
                isMyLocationEnabled = true
                uiSettings.isMyLocationButtonEnabled = true
            }
        } else {
            EasyPermissions.requestPermissions(
                this@MapsActivity, getString(R.string.permitions_meow),
                1, *perms
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menumap, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.newMarker -> {
                canPlaceNew = true
                editMode = true
                removeingMode = false
                Toast.makeText(this, "Нажмите на карту", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.editMarker -> {
                editMode = true
                canPlaceNew = false
                removeingMode = false
                Toast.makeText(this, "Какой маркер редактировать?", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.deleteMarker -> {
                removeingMode = true
                canPlaceNew = false
                editMode = false
                Toast.makeText(this, "Какой маркер удалить?", Toast.LENGTH_SHORT).show()
                true
            }

            R.id.listAllMarkers -> {
                showAreasDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    fun showChangeDialog(oldName: String, newname: (String) -> Unit) {
        var returntext = "-1"

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Change marker")
        val dialogView = layoutInflater.inflate(R.layout.new_place__dialog, null)
        dialogBuilder.setView(dialogView)
            .setPositiveButton("OK") { a, b ->
                returntext = dialogView.findViewById<EditText>(R.id.newPlace).text.toString()
                newname(returntext)
            }
            .setNegativeButton("Передумал") { a, b -> }

        dialogView.findViewById<EditText>(R.id.newPlace).setText(oldName)
        dialogBuilder.create().show()
    }


    fun showNewDialog(name: (String) -> Unit) {
        var returntext = "-1"

        val dialogBuilder = AlertDialog.Builder(this)

        dialogBuilder.setTitle("New place")
        val dialogView = layoutInflater.inflate(R.layout.new_place__dialog, null)
        dialogBuilder.setView(dialogView)
            .setPositiveButton("OK") { a, b ->
                returntext = dialogView.findViewById<EditText>(R.id.newPlace).text.toString()
                name(returntext)
            }
            .setNegativeButton("Передумал") { a, b -> }

        dialogBuilder.create().show()
    }

    fun showAreasDialog() {
        val charSequence: Array<CharSequence> =
            markers.map { it.title as CharSequence }.toTypedArray()

        val builder = AlertDialog.Builder(this);
        builder.setTitle("Покажи мне:")
            .setSingleChoiceItems(charSequence, 0) { d, i ->
                lifecycleScope.launch {
                    mMap.awaitAnimation(CameraUpdateFactory.newCameraPosition(cameraPosition {
                        target(markers.filter { it.title == charSequence[i].toString() }[0].position)
                        zoom(6F)
                    }))
                }
                d.dismiss()
            }
            .setNegativeButton("Передумал") { a, b -> }

        builder.create().show()
    }

}


data class MarkersV(val title: String, val coords: LatLng)



